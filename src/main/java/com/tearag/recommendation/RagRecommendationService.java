package com.tearag.recommendation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tearag.common.LlmSupport;
import com.tearag.rag.KnowledgeRetriever;
import com.tearag.rag.KnowledgeRetriever.RetrievedChunk;
import com.tearag.rag.RagProperties;
import com.tearag.recommendation.RecommendationDtos.CategorySummary;
import com.tearag.recommendation.RecommendationDtos.DrinkRecommendation;
import com.tearag.recommendation.RecommendationDtos.Note;
import com.tearag.recommendation.RecommendationDtos.RecommendationResponse;
import com.tearag.recommendation.RecommendationDtos.SourceRef;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * RAG katmanı (CLAUDE.md mimari karar 1, 3, 4):
 * içeceği kural motoru seçer; LLM yalnızca retrieval ile gelen parçalara dayanarak gerekçe yazar.
 * LLM cevabı doğrulanamazsa veya herhangi bir adım başarısız olursa kural tabanlı öneri döner.
 */
@Service
public class RagRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RagRecommendationService.class);

    static final int MAX_REASON_LENGTH = 1200;

    static final String SYSTEM_PROMPT = """
            Sen bir içecek öneri uygulamasının açıklama yazarısın. İçeceği sen seçmiyorsun; uygulama seçti.
            Görevin, yalnızca verilen KAYNAK PARÇALARI'na dayanarak kullanıcıya kısa bir Türkçe gerekçe yazmak.

            Kurallar:
            1. Yalnızca KAYNAK PARÇALARI'ndaki bilgileri kullan. Dışarıdan sağlık bilgisi veya iddia ekleme.
            2. Geleneksel kullanımı klinik kanıt gibi sunma ("geleneksel olarak kullanılır" gibi ifadeler kullan).
               Kesin tıbbi fayda yazma: "iyileştirir", "tedavi eder", "önler", "kanıtlanmıştır" gibi ifadeler yasak.
            3. Parçalarda bir sınırlılık (kanıtın zayıf olduğu) belirtiliyorsa bunu da kısaca söyle.
            4. UYARILAR verilmişse gerekçede kısaca belirt.
            5. 2-4 cümle yaz. Kullanıcıya "siz" diye hitap et.
            6. "drink" alanına sana verilen içecek kodunu aynen yaz; başka içecek önerme.
            7. "source_ids" alanına yalnızca gerçekten kullandığın parçaların kaynak_id değerlerini yaz.
            8. Metne URL, link veya kaynak adı yazma; kaynaklar ayrıca gösterilecek.
            9. Parçalar İngilizce belge alıntıları olabilir; gerekçeyi her zaman Türkçe yaz. Parçada içecekle
               ilgisi olmayan bilgi (ör. ilaç formu, dozaj tablosu) varsa onu kullanma.
            """;

    /** LLM'in döndürmesi beklenen JSON: {"drink": "...", "reason": "...", "source_ids": [..]} */
    record LlmAnswer(String drink, String reason, @JsonProperty("source_ids") List<Integer> sourceIds) {
    }

    private final RuleBasedRecommender rules;
    private final KnowledgeRetriever retriever;
    private final RecommendationRepository repo;
    private final LlmSupport llm;
    private final RagProperties props;

    public RagRecommendationService(RuleBasedRecommender rules, KnowledgeRetriever retriever,
                                    RecommendationRepository repo, LlmSupport llm, RagProperties props) {
        this.rules = rules;
        this.retriever = retriever;
        this.repo = repo;
        this.llm = llm;
        this.props = props;
    }

    public RecommendationResponse recommend(LocalDate date, Set<String> conditions, boolean evening, boolean useLlm) {
        RecommendationResponse base = rules.recommend(date, conditions, evening); // doğrulama hataları (400) burada
        if (!useLlm) {
            return base.withFallback("LLM istenmedi (llm=false).");
        }
        if (base.recommendation() == null) {
            return base;
        }
        if (!llm.isConfigured()) {
            return base.withFallback("GEMINI_API_KEY tanımlı değil (.env dosyasına ekleyin).");
        }
        try {
            return enrich(base);
        } catch (RuntimeException e) {
            log.warn("RAG başarısız, kural tabanlı öneri dönülüyor: {}", e.toString());
            return base.withFallback("LLM/retrieval hatası: " + e.getClass().getSimpleName());
        }
    }

    private RecommendationResponse enrich(RecommendationResponse base) {
        DrinkRecommendation rec = base.recommendation();
        String slug = rec.drink().slug();

        // 1) Retrieval: yalnızca seçilen içeceğin parçaları
        List<RetrievedChunk> chunks = retriever.search(slug, retrievalQuery(base), props.topK());
        if (chunks.isEmpty()) {
            return base.withFallback("Bu içecek için embedding'i olan bilgi parçası yok (POST /admin/embed).");
        }
        double best = chunks.get(0).similarity();
        if (best < props.similarityThreshold()) {
            return base.withFallback(String.format(Locale.ROOT,
                    "Benzerlik eşiğin altında (%.3f < %.2f).", best, props.similarityThreshold()));
        }

        // 2) LLM
        LlmAnswer answer = llm.client().prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt(base, chunks))
                .call()
                .entity(LlmAnswer.class);

        // 3) Doğrulama
        String rejection = validate(answer, slug, chunks);
        if (rejection != null) {
            log.warn("LLM cevabı reddedildi: {} — {}", rejection, answer);
            return base.withFallback("LLM cevabı reddedildi: " + rejection);
        }

        // 4) Linkler veritabanından
        Set<Integer> sourceIds = new LinkedHashSet<>(answer.sourceIds());
        List<SourceRef> sources = repo.sourcesByIds(sourceIds).stream()
                .map(s -> new SourceRef(s.title(), s.url(), s.authority(), s.urlVerified())).toList();
        List<Integer> usedChunkIds = chunks.stream().filter(c -> sourceIds.contains(c.sourceId()))
                .map(RetrievedChunk::id).toList();
        return base.withLlmReason(answer.reason().strip(), sources, usedChunkIds);
    }

    /** null: geçerli; aksi halde ret nedeni. */
    static String validate(LlmAnswer answer, String expectedSlug, List<RetrievedChunk> chunks) {
        if (answer == null) {
            return "boş cevap";
        }
        if (!expectedSlug.equals(answer.drink())) {
            return "listede olmayan/farklı içecek: " + answer.drink();
        }
        if (answer.reason() == null || answer.reason().isBlank()) {
            return "gerekçe boş";
        }
        if (answer.reason().length() > MAX_REASON_LENGTH) {
            return "gerekçe çok uzun";
        }
        if (answer.reason().matches("(?s).*(https?://|www\\.).*")) {
            return "gerekçede link var";
        }
        if (answer.sourceIds() == null || answer.sourceIds().isEmpty()) {
            return "kaynak gösterilmedi";
        }
        Set<Integer> allowed = chunks.stream().map(RetrievedChunk::sourceId).collect(Collectors.toSet());
        Set<Integer> unknown = answer.sourceIds().stream().filter(id -> !allowed.contains(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!unknown.isEmpty()) {
            return "retrieval'da gelmeyen source_id: " + unknown;
        }
        return null;
    }

    /** Parçalar "İçecek (Drink): içerik" biçiminde embed edildiği için sorgu da aynı önekle başlar. */
    static String retrievalQuery(RecommendationResponse base) {
        String categories = base.categories().stream().map(CategorySummary::nameTr).collect(Collectors.joining(", "));
        var drink = base.recommendation().drink();
        return drink.nameTr() + " (" + drink.nameEn() + "): " + categories
                + " yemeklerinden sonra sindirim, şişkinlik ve rahatlama için kullanımı";
    }

    static String userPrompt(RecommendationResponse base, List<RetrievedChunk> chunks) {
        DrinkRecommendation rec = base.recommendation();
        StringBuilder sb = new StringBuilder();
        sb.append("İÇECEK: ").append(rec.drink().nameTr()).append(" (kod: ").append(rec.drink().slug()).append(")\n");
        sb.append("KANIT DÜZEYİ: ").append(rec.drink().evidenceLevel());
        if (rec.drink().evidenceNote() != null) {
            sb.append(". ").append(rec.drink().evidenceNote());
        }
        sb.append("\nBUGÜNKÜ YEMEKLER: ");
        sb.append(base.categories().stream()
                .map(c -> c.nameTr() + " (" + String.join(", ", c.foods()) + ")")
                .collect(Collectors.joining("; ")));
        sb.append("\nKULLANICI DURUMLARI: ")
                .append(base.conditions().isEmpty() ? "belirtilmedi" : String.join(", ", base.conditions()));
        sb.append("\nAKŞAM: ").append(base.evening() ? "evet" : "hayır");
        sb.append("\nUYGULAMA KURALI (kaynak değildir, yalnızca neden seçildiği): ")
                .append(String.join(" | ", rec.ruleReasons()));
        sb.append("\nUYARILAR:");
        if (rec.cautions().isEmpty()) {
            sb.append(" yok");
        }
        for (Note n : rec.cautions()) {
            sb.append("\n- ").append(n.noteTr());
        }
        sb.append("\n\nKAYNAK PARÇALARI:");
        for (RetrievedChunk c : chunks) {
            sb.append("\n[kaynak_id=").append(c.sourceId()).append("] (").append(claimTypeTr(c.claimType()))
                    .append(") ").append(c.content());
        }
        return sb.toString();
    }

    private static String claimTypeTr(String claimType) {
        if (claimType == null) {
            return "belge alıntısı"; // ingestion ile gelen parça (V5)
        }
        return switch (claimType) {
            case "traditional_use" -> "geleneksel kullanım";
            case "well_established_use" -> "iyi belgelenmiş kullanım";
            case "clinical_evidence" -> "klinik çalışma";
            case "limitation" -> "sınırlılık";
            case "safety" -> "güvenlik";
            default -> claimType;
        };
    }
}
