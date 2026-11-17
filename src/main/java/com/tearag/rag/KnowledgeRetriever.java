package com.tearag.rag;

import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * pgvector kosinüs araması. Her zaman tek bir içeceğin parçaları içinde arar (karar algoritmada verildi)
 * ve yalnızca şu anki embedding modeliyle üretilmiş vektörleri karşılaştırır.
 */
@Component
public class KnowledgeRetriever {

    private final JdbcTemplate jdbc;
    private final EmbeddingModel embeddingModel;
    private final String modelName;

    public KnowledgeRetriever(JdbcTemplate jdbc, EmbeddingModel embeddingModel,
                              @Value("${spring.ai.ollama.embedding.options.model}") String modelName) {
        this.jdbc = jdbc;
        this.embeddingModel = embeddingModel;
        this.modelName = modelName;
    }

    public record RetrievedChunk(int id, int sourceId, String claimType, String content, double similarity) {
    }

    /** En benzerden en az benzere sıralı; içeceğin embed edilmiş parçası yoksa boş liste. */
    public List<RetrievedChunk> search(String drinkSlug, String query, int topK) {
        float[] vector = embeddingModel.embed(query);
        if (vector == null || vector.length != EmbeddingIngestionService.DIMENSIONS) {
            throw new IllegalStateException("Sorgu embedding'i geçersiz (boyut: "
                    + (vector == null ? "null" : vector.length) + ")");
        }
        String v = EmbeddingIngestionService.toPgVector(vector);
        return jdbc.query("""
                SELECT k.id, k.source_id, k.claim_type, k.content, 1 - (k.embedding <=> ?::vector) AS sim
                FROM knowledge_chunks k JOIN drinks d ON d.id = k.drink_id
                WHERE d.slug = ? AND k.embedding IS NOT NULL AND k.embedding_model = ?
                ORDER BY k.embedding <=> ?::vector
                LIMIT ?""",
                (rs, i) -> new RetrievedChunk(rs.getInt("id"), rs.getInt("source_id"), rs.getString("claim_type"),
                        rs.getString("content"), rs.getDouble("sim")),
                v, drinkSlug, modelName, v, topK);
    }
}
