package com.tearag.recommendation;

import com.tearag.recommendation.RecommendationDtos.RecommendationResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendation")
public class RecommendationController {

    /** Bu saatten itibaren "akşam" sayılır (kafeinli içecek puanı düşer). */
    static final int EVENING_FROM_HOUR = 17;

    private final RagRecommendationService rag;
    private final RuleBasedRecommender recommender;

    public RecommendationController(RagRecommendationService rag, RuleBasedRecommender recommender) {
        this.rag = rag;
        this.recommender = recommender;
    }

    /**
     * GET /api/recommendation?date=2026-11-17&conditions=reflux,pregnancy&evening=true
     *
     * @param date       verilmezse bugün
     * @param conditions kullanıcı durumları (virgülle); geçerli kodlar: GET /api/recommendation/conditions
     * @param evening    verilmezse sunucu saati 17:00 ve sonrasıysa true
     * @param llm        false ise yalnızca kural tabanlı öneri (Gemini çağrılmaz)
     */
    @GetMapping
    public RecommendationResponse recommend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "") Set<String> conditions,
            @RequestParam(required = false) Boolean evening,
            @RequestParam(defaultValue = "true") boolean llm) {
        return rag.recommend(
                date != null ? date : LocalDate.now(),
                conditions,
                evening != null ? evening : LocalTime.now().getHour() >= EVENING_FROM_HOUR,
                llm);
    }

    @GetMapping("/conditions")
    public List<String> conditions() {
        return recommender.conditionCodes();
    }
}
