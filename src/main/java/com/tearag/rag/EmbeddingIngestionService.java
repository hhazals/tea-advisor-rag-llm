package com.tearag.rag;

import com.tearag.common.ApiException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * knowledge_chunks.embedding alanını doldurur.
 * Embedding'e giden metin: "İçecek adı: parça içeriği" — içerik veritabanında olduğu gibi kalır.
 */
@Service
public class EmbeddingIngestionService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingIngestionService.class);

    /** V4 migration'daki vector(768) ile aynı olmalı. */
    static final int DIMENSIONS = 768;

    private final JdbcTemplate jdbc;
    private final EmbeddingModel embeddingModel;
    private final String modelName;

    public EmbeddingIngestionService(JdbcTemplate jdbc, EmbeddingModel embeddingModel,
                                     @Value("${spring.ai.ollama.embedding.options.model}") String modelName) {
        this.jdbc = jdbc;
        this.embeddingModel = embeddingModel;
        this.modelName = modelName;
    }

    public record Result(int embedded, int total, String model, int dimensions) {
    }

    private record Chunk(int id, String text) {
    }

    /**
     * @param force true ise tüm parçalar yeniden üretilir; false ise yalnızca boş olanlar
     *              veya başka bir modelle üretilmiş olanlar.
     */
    @Transactional
    public Result embed(boolean force) {
        List<Chunk> chunks = jdbc.query("""
                SELECT k.id, d.name_tr || ': ' || k.content AS text
                FROM knowledge_chunks k JOIN drinks d ON d.id = k.drink_id
                WHERE ? OR k.embedding IS NULL OR k.embedding_model IS DISTINCT FROM ?
                ORDER BY k.id""",
                (rs, i) -> new Chunk(rs.getInt("id"), rs.getString("text")), force, modelName);
        int total = jdbc.queryForObject("SELECT count(*) FROM knowledge_chunks", Integer.class);

        if (chunks.isEmpty()) {
            return new Result(0, total, modelName, DIMENSIONS);
        }

        List<float[]> vectors;
        try {
            vectors = embeddingModel.embed(chunks.stream().map(Chunk::text).toList());
        } catch (RuntimeException e) {
            log.warn("Embedding üretilemedi ({}): {}", modelName, e.toString());
            throw ApiException.serviceUnavailable("Embedding servisine ulaşılamadı. Ollama çalışıyor mu ve '"
                    + modelName + "' modeli indirildi mi? (ollama pull " + modelName + ")");
        }
        if (vectors.size() != chunks.size()) {
            throw new IllegalStateException("Embedding sayısı parça sayısıyla uyuşmuyor: "
                    + vectors.size() + " / " + chunks.size());
        }

        for (int i = 0; i < chunks.size(); i++) {
            float[] v = vectors.get(i);
            if (v.length != DIMENSIONS) {
                throw new IllegalStateException("'" + modelName + "' " + v.length + " boyutlu vektör üretti, şema "
                        + DIMENSIONS + " bekliyor. Modeli değiştirdiysen yeni bir migration ile sütunu güncelle.");
            }
            jdbc.update("UPDATE knowledge_chunks SET embedding = ?::vector, embedding_model = ? WHERE id = ?",
                    toPgVector(v), modelName, chunks.get(i).id());
        }
        log.info("{} parça '{}' ile embed edildi", chunks.size(), modelName);
        return new Result(chunks.size(), total, modelName, DIMENSIONS);
    }

    /** pgvector metin biçimi: [0.1,0.2,...] */
    static String toPgVector(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 10).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
