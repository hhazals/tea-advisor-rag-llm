package com.tearag.recommendation;

import com.tearag.common.ApiException;
import com.tearag.recommendation.RecommendationDtos.CategorySummary;
import com.tearag.recommendation.RecommendationDtos.DrinkInfo;
import com.tearag.recommendation.RecommendationDtos.DrinkRecommendation;
import com.tearag.recommendation.RecommendationDtos.ExcludedDrink;
import com.tearag.recommendation.RecommendationDtos.Note;
import com.tearag.recommendation.RecommendationDtos.RecommendationResponse;
import com.tearag.recommendation.RecommendationDtos.SourceRef;
import com.tearag.recommendation.RecommendationRepository.Drink;
import com.tearag.recommendation.RecommendationRepository.MealFood;
import com.tearag.recommendation.RecommendationRepository.Rule;
import com.tearag.recommendation.RecommendationRepository.SafetyNote;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kural tabanlı öneri (CLAUDE.md mimari karar 1 ve 2):
 * <ol>
 *   <li>Günün yemekleri kategorilere ayrılır; akşam yemeği kalemi 2, diğerleri 1 ağırlık.</li>
 *   <li>drink_rules: puan += base_score × kategori ağırlığı. Pasif içecekler aday olmaz.</li>
 *   <li>avoid_if_condition kuralı, durum aktifse base_score (-100) × ağırlık ekler.</li>
 *   <li>safety_notes: aktif durumla eşleşen exclude → elenir; her caution → puan yarıya iner.</li>
 *   <li>Sıralama: puan (azalan), uyarı sayısı (artan), içecek id.</li>
 * </ol>
 */
@Service
@Transactional(readOnly = true)
public class RuleBasedRecommender {

    static final int DINNER_WEIGHT = 2;
    static final double CAUTION_FACTOR = 0.5;
    static final int ALTERNATIVE_COUNT = 2;
    static final String EVENING = "evening";

    private final RecommendationRepository repo;

    public RuleBasedRecommender(RecommendationRepository repo) {
        this.repo = repo;
    }

    public List<String> conditionCodes() {
        return repo.userConditionCodes();
    }

    public RecommendationResponse recommend(LocalDate date, Set<String> conditions, boolean evening) {
        Set<String> userConditions = validate(conditions);
        Set<String> active = new HashSet<>(userConditions);
        if (evening) {
            active.add(EVENING);
        }

        // 1) Günün yemekleri → kategoriler
        List<MealFood> foods = repo.foodsOfDay(date);
        Map<Integer, CategoryAgg> categories = new LinkedHashMap<>();
        Set<String> uncategorized = new LinkedHashSet<>();
        for (MealFood f : foods) {
            if (f.categoryId() == null) {
                uncategorized.add(f.foodName());
                continue;
            }
            CategoryAgg agg = categories.computeIfAbsent(f.categoryId(),
                    id -> new CategoryAgg(f.categorySlug(), f.categoryNameTr()));
            agg.weight += "dinner".equals(f.mealType()) ? DINNER_WEIGHT : 1;
            agg.foods.add(f.foodName());
        }
        List<CategorySummary> categorySummaries = categories.values().stream()
                .map(c -> new CategorySummary(c.slug, c.nameTr, c.weight, List.copyOf(c.foods))).toList();
        List<String> userConditionList = List.copyOf(userConditions);

        if (categories.isEmpty()) {
            String msg = foods.isEmpty()
                    ? "Bu tarih için kayıtlı öğün yok."
                    : "Bu tarihteki yemeklerin kategorisi belirtilmemiş; öneri için yemeklere kategori atayın.";
            return empty(date, evening, userConditionList, categorySummaries, uncategorized, List.of(), msg);
        }

        // 2-3) Kurallardan puan
        Map<Integer, Drink> drinks = repo.drinks().stream().collect(Collectors.toMap(Drink::id, Function.identity()));
        Map<Integer, Candidate> candidates = new LinkedHashMap<>();
        List<Rule> avoidRules = new ArrayList<>();
        for (Rule rule : repo.rulesFor(categories.keySet())) {
            Drink drink = drinks.get(rule.drinkId());
            if (!drink.active()) {
                continue;
            }
            if ("avoid_if_condition".equals(rule.role())) {
                avoidRules.add(rule);
                continue;
            }
            CategoryAgg cat = categories.get(rule.categoryId());
            Candidate c = candidates.computeIfAbsent(drink.id(), id -> new Candidate(drink));
            c.score += rule.baseScore() * cat.weight;
            c.ruleReasons.add(cat.nameTr + " (" + roleTr(rule.role()) + "): " + rule.rationaleTr());
            if (c.bestRule == null || rule.baseScore() > c.bestRule.baseScore()) {
                c.bestRule = rule;
                c.bestCategory = cat;
            }
        }
        for (Rule rule : avoidRules) {
            Candidate c = candidates.get(rule.drinkId());
            if (c != null && active.contains(rule.conditionCode())) {
                c.score += rule.baseScore() * categories.get(rule.categoryId()).weight;
                c.ruleReasons.add("Kaçınılmalı (" + rule.conditionCode() + "): " + rule.rationaleTr());
            }
        }

        // 4) Güvenlik kısıtları
        List<ExcludedDrink> excluded = new ArrayList<>();
        Map<Integer, List<SafetyNote>> notesByDrink = repo.safetyNotes(candidates.keySet()).stream()
                .collect(Collectors.groupingBy(SafetyNote::drinkId));
        for (Candidate c : candidates.values()) {
            List<Note> exclusions = new ArrayList<>();
            for (SafetyNote n : notesByDrink.getOrDefault(c.drink.id(), List.of())) {
                boolean applies = n.conditionCode() != null && active.contains(n.conditionCode());
                if (n.conditionCode() == null || (applies && "info".equals(n.severity()))) {
                    c.infos.add(toNote(n));
                } else if (applies && "exclude".equals(n.severity())) {
                    exclusions.add(toNote(n));
                } else if (applies && "caution".equals(n.severity())) {
                    c.cautions.add(toNote(n));
                    c.score *= CAUTION_FACTOR;
                }
            }
            if (!exclusions.isEmpty()) {
                c.excluded = true;
                excluded.add(new ExcludedDrink(toInfo(c.drink), exclusions));
            }
        }

        // 5) Sıralama
        List<Candidate> ranked = candidates.values().stream()
                .filter(c -> !c.excluded && c.score > 0)
                .sorted(Comparator.comparingDouble((Candidate c) -> -c.score)
                        .thenComparingInt(c -> c.cautions.size())
                        .thenComparingInt(c -> c.drink.id()))
                .toList();
        if (ranked.isEmpty()) {
            return empty(date, evening, userConditionList, categorySummaries, uncategorized, excluded,
                    "Belirttiğiniz durumlarla uyumlu bir içecek bulunamadı.");
        }

        List<String> foodSummary = categories.values().stream().map(c -> c.nameTr).toList();
        return new RecommendationResponse(date, evening, userConditionList, categorySummaries,
                List.copyOf(uncategorized),
                toRecommendation(ranked.get(0), foodSummary),
                ranked.stream().skip(1).limit(ALTERNATIVE_COUNT).map(c -> toRecommendation(c, foodSummary)).toList(),
                excluded, null, "rules", RecommendationDtos.DISCLAIMER, List.of(), List.of(), null);
    }

    private Set<String> validate(Set<String> conditions) {
        Set<String> normalized = conditions.stream().map(s -> s.strip().toLowerCase(java.util.Locale.ROOT))
                .filter(s -> !s.isEmpty()).collect(Collectors.toCollection(TreeSet::new));
        if (normalized.contains(EVENING)) {
            throw ApiException.badRequest("'evening' bir durum değil; evening=true parametresini kullanın.");
        }
        List<String> valid = repo.userConditionCodes();
        Set<String> unknown = new TreeSet<>(normalized);
        unknown.removeAll(valid);
        if (!unknown.isEmpty()) {
            throw ApiException.badRequest("Bilinmeyen durum kodu: " + unknown + ". Geçerli kodlar: " + valid);
        }
        return normalized;
    }

    private static RecommendationResponse empty(LocalDate date, boolean evening, List<String> conditions,
                                                List<CategorySummary> categories, Set<String> uncategorized,
                                                List<ExcludedDrink> excluded, String message) {
        return new RecommendationResponse(date, evening, conditions, categories, List.copyOf(uncategorized),
                null, List.of(), excluded, message, "rules", RecommendationDtos.DISCLAIMER, List.of(), List.of(), null);
    }

    private static DrinkRecommendation toRecommendation(Candidate c, List<String> foodSummary) {
        String reason = "Bugünkü öğünleriniz: " + String.join(", ", foodSummary) + ". "
                + c.drink.nameTr() + ", " + c.bestCategory.nameTr + " için " + roleTr(c.bestRule.role())
                + " öneri: " + c.bestRule.rationaleTr()
                + " Kanıt düzeyi: " + c.drink.evidenceLevel() + "."
                + (c.drink.evidenceNote() != null ? " " + c.drink.evidenceNote() : "");
        double score = Math.round(c.score * 10) / 10.0;
        return new DrinkRecommendation(toInfo(c.drink), score, reason, List.copyOf(c.ruleReasons),
                List.copyOf(c.cautions), List.copyOf(c.infos));
    }

    private static DrinkInfo toInfo(Drink d) {
        return new DrinkInfo(d.slug(), d.nameTr(), d.nameEn(), d.category(), d.containsCaffeine(),
                d.containsLactose(), d.evidenceLevel(), d.evidenceNote());
    }

    private static Note toNote(SafetyNote n) {
        SourceRef src = n.source() == null ? null : new SourceRef(n.source().title(), n.source().url(),
                n.source().authority(), n.source().urlVerified());
        return new Note(n.conditionCode(), n.severity(), n.noteTr(), src);
    }

    private static String roleTr(String role) {
        return switch (role) {
            case "primary" -> "birincil";
            case "alternative" -> "alternatif";
            default -> role;
        };
    }

    private static final class CategoryAgg {
        final String slug;
        final String nameTr;
        int weight;
        final Set<String> foods = new LinkedHashSet<>();

        CategoryAgg(String slug, String nameTr) {
            this.slug = slug;
            this.nameTr = nameTr;
        }
    }

    private static final class Candidate {
        final Drink drink;
        double score;
        boolean excluded;
        Rule bestRule;
        CategoryAgg bestCategory;
        final List<String> ruleReasons = new ArrayList<>();
        final List<Note> cautions = new ArrayList<>();
        final List<Note> infos = new ArrayList<>();

        Candidate(Drink drink) {
            this.drink = drink;
        }
    }
}
