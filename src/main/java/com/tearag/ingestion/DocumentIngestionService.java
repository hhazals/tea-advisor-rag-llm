package com.tearag.ingestion;

import com.tearag.ingestion.SourceFetcher.FetchedDocument;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RAG ingestion hattı: Fetch → Extract → Clean → Chunk → (DB'ye yaz) → Embed (EmbeddingIngestionService).
 * Her kaynak ayrı işlenir; biri başarısız olursa önceki parçaları korunur, diğerleri devam eder.
 * İndirilen belgeler depoya veya diske yazılmaz; yalnızca parçaları veritabanında tutulur.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    /** Uzun raporların embedding süresini sınırlamak için kaynak başına üst sınır. */
    static final int MAX_CHUNKS_PER_SOURCE = 150;

    private final JdbcTemplate jdbc;
    private final SourceFetcher fetcher;
    private final TransactionTemplate tx;

    public DocumentIngestionService(JdbcTemplate jdbc, SourceFetcher fetcher, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.fetcher = fetcher;
        this.tx = tx;
    }

    public record SourceReport(int sourceId, String title, List<String> drinks, String status, int chunks) {
    }

    private record SourceRow(int id, String title, String url, List<Integer> drinkIds, List<String> drinkSlugs) {
    }

    public List<SourceReport> ingestAll() {
        List<SourceRow> sources = jdbc.query("""
                SELECT s.id, s.title, s.url,
                       array_agg(d.id ORDER BY d.id) AS drink_ids, array_agg(d.slug ORDER BY d.id) AS slugs
                FROM sources s
                JOIN source_drinks sd ON sd.source_id = s.id
                JOIN drinks d ON d.id = sd.drink_id
                GROUP BY s.id, s.title, s.url
                ORDER BY s.id""",
                (rs, i) -> new SourceRow(rs.getInt("id"), rs.getString("title"), rs.getString("url"),
                        Arrays.asList((Integer[]) rs.getArray("drink_ids").getArray()),
                        Arrays.asList((String[]) rs.getArray("slugs").getArray())));
        return sources.stream().map(this::ingest).toList();
    }

    private SourceReport ingest(SourceRow source) {
        List<String> chunks;
        try {
            FetchedDocument doc = fetcher.fetch(source.url());
            List<String> paragraphs = TextExtractor.extract(doc);
            // Çok kısa parçalar (ör. yalnızca sayfa başlığı) bilgi taşımaz ama kısa oldukları için yüksek benzerlik alır
            chunks = TextChunker.chunk(paragraphs).stream()
                    .filter(c -> TextChunker.wordCount(c) >= TextChunker.MIN_WORDS)
                    .toList();
            if (chunks.size() > MAX_CHUNKS_PER_SOURCE) {
                chunks = chunks.subList(0, MAX_CHUNKS_PER_SOURCE);
            }
        } catch (RuntimeException e) {
            String status = "hata: " + e.getMessage();
            log.warn("Kaynak {} ingest edilemedi: {}", source.id(), status);
            jdbc.update("UPDATE sources SET ingest_status = ? WHERE id = ?", status, source.id());
            return new SourceReport(source.id(), source.title(), source.drinkSlugs(), status, 0);
        }
        if (chunks.isEmpty()) {
            // Belge indirildi ama kullanılabilir metin yok: eski parçalar da artık geçersiz (indirme hatasından farklı)
            tx.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM knowledge_chunks WHERE source_id = ? AND origin = 'ingested'", source.id());
                jdbc.update("UPDATE sources SET ingested_at = now(), ingest_status = 'boş: metin çıkarılamadı' "
                        + "WHERE id = ?", source.id());
            });
            return new SourceReport(source.id(), source.title(), source.drinkSlugs(), "boş: metin çıkarılamadı", 0);
        }

        List<String> finalChunks = chunks;
        tx.executeWithoutResult(status -> {
            // Yeniden ingest: bu kaynağın eski "ingested" parçaları silinir, elle yazılmış parçalara dokunulmaz
            jdbc.update("DELETE FROM knowledge_chunks WHERE source_id = ? AND origin = 'ingested'", source.id());
            for (Integer drinkId : source.drinkIds()) {
                for (int i = 0; i < finalChunks.size(); i++) {
                    jdbc.update("""
                            INSERT INTO knowledge_chunks (drink_id, source_id, claim_type, content, origin, chunk_index)
                            VALUES (?, ?, NULL, ?, 'ingested', ?)""",
                            drinkId, source.id(), finalChunks.get(i), i);
                }
            }
            jdbc.update("UPDATE sources SET ingested_at = now(), ingest_status = ? WHERE id = ?",
                    "ok: " + finalChunks.size() + " parça", source.id());
        });
        log.info("Kaynak {} → {} parça ({})", source.id(), finalChunks.size(), source.drinkSlugs());
        return new SourceReport(source.id(), source.title(), source.drinkSlugs(), "ok", finalChunks.size());
    }
}
