package com.tearag.recommendation;

import java.time.LocalDate;
import java.util.List;

/** GET /api/recommendation cevap modeli. */
public final class RecommendationDtos {

    private RecommendationDtos() {
    }

    public static final String DISCLAIMER =
            "Bu öneri bilgilendirme amaçlıdır, sağlık tavsiyesi değildir. Sağlık sorunlarınız için bir uzmana danışın.";

    public record RecommendationResponse(
            LocalDate date,
            boolean evening,
            List<String> conditions,
            List<CategorySummary> categories,
            List<String> uncategorizedFoods,
            DrinkRecommendation recommendation,
            List<DrinkRecommendation> alternatives,
            List<ExcludedDrink> excluded,
            String message,
            String generatedBy,
            String disclaimer,
            List<SourceRef> sources,
            List<Integer> usedChunkIds,
            String fallbackReason) {

        /** generatedBy = "rules": LLM kullanılmadı veya cevabı reddedildi (neden fallbackReason'da). */
        public RecommendationResponse withFallback(String reason) {
            return new RecommendationResponse(date, evening, conditions, categories, uncategorizedFoods,
                    recommendation, alternatives, excluded, message, "rules", disclaimer, List.of(), List.of(), reason);
        }

        /** LLM gerekçesi doğrulandıktan sonra: önerinin metni ve kaynakları değişir, seçim aynı kalır. */
        public RecommendationResponse withLlmReason(String reasonTr, List<SourceRef> sources, List<Integer> chunkIds) {
            DrinkRecommendation r = recommendation;
            DrinkRecommendation updated = new DrinkRecommendation(r.drink(), r.score(), reasonTr, r.ruleReasons(),
                    r.cautions(), r.infos());
            return new RecommendationResponse(date, evening, conditions, categories, uncategorizedFoods,
                    updated, alternatives, excluded, message, "rag_llm", disclaimer, sources, chunkIds, null);
        }
    }

    /** weight: akşam yemeğindeki kalemler 2, diğerleri 1 sayılır. */
    public record CategorySummary(String slug, String nameTr, int weight, List<String> foods) {
    }

    public record DrinkInfo(String slug, String nameTr, String nameEn, String category, boolean containsCaffeine,
                            boolean containsLactose, String evidenceLevel, String evidenceNote) {
    }

    public record SourceRef(String title, String url, String authority, boolean urlVerified) {
    }

    public record Note(String conditionCode, String severity, String noteTr, SourceRef source) {
    }

    /**
     * reasonTr: uygulamanın kendi kuralına dayalı açıklama (kaynak iddiası değildir).
     * ruleReasons: puana katkı yapan her kural.
     */
    public record DrinkRecommendation(DrinkInfo drink, double score, String reasonTr, List<String> ruleReasons,
                                      List<Note> cautions, List<Note> infos) {
    }

    public record ExcludedDrink(DrinkInfo drink, List<Note> reasons) {
    }
}
