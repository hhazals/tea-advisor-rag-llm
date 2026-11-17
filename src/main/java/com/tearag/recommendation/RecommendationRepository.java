package com.tearag.recommendation;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Öneri motorunun okuduğu tablolar (salt okunur, JDBC). */
@Repository
public class RecommendationRepository {

    private final JdbcTemplate jdbc;

    public RecommendationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record MealFood(String foodName, Integer categoryId, String categorySlug, String categoryNameTr, String mealType) {
    }

    record Rule(int categoryId, int drinkId, String role, int baseScore, String conditionCode, String rationaleTr) {
    }

    record Drink(int id, String slug, String nameTr, String nameEn, String category, boolean containsCaffeine,
                 boolean containsLactose, String evidenceLevel, String evidenceNote, boolean active) {
    }

    record Source(String title, String url, String authority, boolean urlVerified) {
    }

    record SafetyNote(int drinkId, String conditionCode, String severity, String noteTr, Source source) {
    }

    List<MealFood> foodsOfDay(LocalDate date) {
        return jdbc.query("""
                SELECT f.name, fc.id AS cat_id, fc.slug, fc.name_tr, m.meal_type
                FROM meals m
                JOIN meal_items mi ON mi.meal_id = m.id
                JOIN foods f ON f.id = mi.food_id
                LEFT JOIN food_categories fc ON fc.id = f.food_category_id
                WHERE m.meal_date = ?
                ORDER BY fc.id NULLS LAST, f.name""",
                (rs, i) -> new MealFood(rs.getString("name"), (Integer) rs.getObject("cat_id"),
                        rs.getString("slug"), rs.getString("name_tr"), rs.getString("meal_type")),
                date);
    }

    List<Rule> rulesFor(Collection<Integer> categoryIds) {
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        String in = categoryIds.stream().map(id -> "?").collect(Collectors.joining(","));
        return jdbc.query("SELECT * FROM drink_rules WHERE food_category_id IN (" + in + ") ORDER BY drink_id",
                (rs, i) -> new Rule(rs.getInt("food_category_id"), rs.getInt("drink_id"), rs.getString("role"),
                        rs.getInt("base_score"), rs.getString("condition_code"), rs.getString("rationale_tr")),
                categoryIds.toArray());
    }

    List<Drink> drinks() {
        return jdbc.query("SELECT * FROM drinks ORDER BY id",
                (rs, i) -> new Drink(rs.getInt("id"), rs.getString("slug"), rs.getString("name_tr"),
                        rs.getString("name_en"), rs.getString("category"), rs.getBoolean("contains_caffeine"),
                        rs.getBoolean("contains_lactose"), rs.getString("evidence_level"),
                        rs.getString("evidence_note"), rs.getBoolean("active")));
    }

    List<SafetyNote> safetyNotes(Collection<Integer> drinkIds) {
        if (drinkIds.isEmpty()) {
            return List.of();
        }
        String in = drinkIds.stream().map(id -> "?").collect(Collectors.joining(","));
        return jdbc.query("""
                SELECT sn.drink_id, sn.condition_code, sn.severity, sn.note_tr,
                       s.title, s.url, s.authority, s.url_verified
                FROM safety_notes sn LEFT JOIN sources s ON s.id = sn.source_id
                WHERE sn.drink_id IN (%s)
                ORDER BY sn.id""".formatted(in),
                (rs, i) -> new SafetyNote(rs.getInt("drink_id"), rs.getString("condition_code"),
                        rs.getString("severity"), rs.getString("note_tr"),
                        rs.getString("url") == null ? null : new Source(rs.getString("title"), rs.getString("url"),
                                rs.getString("authority"), rs.getBoolean("url_verified"))),
                drinkIds.toArray());
    }

    /** LLM'in gösterdiği kaynak id'leri için başlık/URL — linkler her zaman buradan gelir. */
    List<Source> sourcesByIds(Collection<Integer> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String in = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        return jdbc.query("SELECT title, url, authority, url_verified FROM sources WHERE id IN (" + in + ") ORDER BY id",
                (rs, i) -> new Source(rs.getString("title"), rs.getString("url"), rs.getString("authority"),
                        rs.getBoolean("url_verified")),
                ids.toArray());
    }

    /** Kullanıcının bildirebileceği durum kodları (evening hariç: o saatten gelir). */
    List<String> userConditionCodes() {
        return jdbc.queryForList("""
                SELECT condition_code FROM safety_notes WHERE condition_code IS NOT NULL
                UNION
                SELECT condition_code FROM drink_rules WHERE condition_code IS NOT NULL
                EXCEPT SELECT 'evening'
                ORDER BY 1""", String.class);
    }
}
