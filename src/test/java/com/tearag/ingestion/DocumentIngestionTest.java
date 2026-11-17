package com.tearag.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tearag.IntegrationTest;
import com.tearag.ingestion.SourceFetcher.FetchException;
import com.tearag.ingestion.SourceFetcher.FetchedDocument;
import com.tearag.ingestion.SourceFetcher.Kind;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Ingestion hattı uçtan uca: indirme mock, çıkarma/parçalama/DB/embedding gerçek (embedding modeli mock). */
class DocumentIngestionTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        when(embeddingModel.embed(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return texts.stream().map(t -> {
                float[] v = new float[768];
                v[0] = 1f;
                return v;
            }).toList();
        });
        // Varsayılan: her kaynak indirilemiyor; testler ilgili kaynakları açar
        doThrow(new FetchException("HTTP 403", null)).when(sourceFetcher).fetch(anyString());
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM knowledge_chunks WHERE origin = 'ingested'");
        jdbc.update("UPDATE knowledge_chunks SET embedding = NULL, embedding_model = NULL");
        jdbc.update("UPDATE sources SET ingested_at = NULL, ingest_status = NULL");
    }

    /** ~600 kelimelik HTML makale: 60 cümle × 10 kelime. */
    private static byte[] longArticle(String topic) {
        String paragraphs = IntStream.range(0, 6).mapToObj(p -> "<p>" + IntStream.range(0, 10)
                        .mapToObj(s -> topic + " sentence " + (p * 10 + s) + " describes traditional use and safety.")
                        .collect(Collectors.joining(" ")) + "</p>")
                .collect(Collectors.joining());
        return ("<html><body><nav>menu</nav><main>" + paragraphs + "</main></body></html>")
                .getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void ingestsFetchesChunksAndEmbedsAndReportsFailures() throws Exception {
        doReturn(new FetchedDocument(Kind.HTML, longArticle("Chamomile"), "u")).when(sourceFetcher).fetch(org.mockito.ArgumentMatchers.contains("nccih.nih.gov/health/chamomile"));

        mvc.perform(post("/admin/ingest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources.length()").value(20))
                .andExpect(jsonPath("$.sources[?(@.sourceId == 11)].status").value("ok"))
                .andExpect(jsonPath("$.sources[?(@.sourceId == 1)].status").value("hata: HTTP 403"))
                .andExpect(jsonPath("$.embedding.model").value("paraphrase-multilingual"))
                .andExpect(jsonPath("$.embeddingError").doesNotExist());

        int chunks = jdbc.queryForObject("""
                SELECT count(*) FROM knowledge_chunks k JOIN drinks d ON d.id = k.drink_id
                WHERE k.origin = 'ingested' AND k.source_id = 11 AND d.slug = 'chamomile'""", Integer.class);
        assertThat(chunks).isBetween(4, 8); // 600 kelime / ~150 (+ örtüşme)
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM knowledge_chunks WHERE origin = 'ingested'
                  AND (embedding IS NULL OR claim_type IS NOT NULL OR chunk_index IS NULL)""", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT ingest_status FROM sources WHERE id = 11", String.class))
                .startsWith("ok: ");
        assertThat(jdbc.queryForObject("SELECT ingest_status FROM sources WHERE id = 1", String.class))
                .isEqualTo("hata: HTTP 403");
        // elle yazılmış parçalar korunuyor
        assertThat(jdbc.queryForObject("SELECT count(*) FROM knowledge_chunks WHERE origin = 'curated'",
                Integer.class)).isEqualTo(21);
    }

    @Test
    void reingestReplacesInsteadOfDuplicating() throws Exception {
        doReturn(new FetchedDocument(Kind.HTML, longArticle("Chamomile"), "u")).when(sourceFetcher).fetch(org.mockito.ArgumentMatchers.contains("nccih.nih.gov/health/chamomile"));
        mvc.perform(post("/admin/ingest"));
        int first = countIngested(11);

        mvc.perform(post("/admin/ingest"));
        assertThat(countIngested(11)).isEqualTo(first);
    }

    @Test
    void failedReFetchKeepsPreviousChunks() throws Exception {
        doReturn(new FetchedDocument(Kind.HTML, longArticle("Chamomile"), "u")).when(sourceFetcher).fetch(org.mockito.ArgumentMatchers.contains("nccih.nih.gov/health/chamomile"));
        mvc.perform(post("/admin/ingest"));
        int first = countIngested(11);

        doThrow(new FetchException("zaman aşımı", null)).when(sourceFetcher).fetch(anyString());
        mvc.perform(post("/admin/ingest"));
        assertThat(countIngested(11)).isEqualTo(first);
    }

    @Test
    void sourceThatNoLongerHasTextLosesOldChunks() throws Exception {
        doReturn(new FetchedDocument(Kind.HTML, longArticle("Chamomile"), "u"))
                .when(sourceFetcher).fetch(org.mockito.ArgumentMatchers.contains("nccih.nih.gov/health/chamomile"));
        mvc.perform(post("/admin/ingest"));
        assertThat(countIngested(11)).isPositive();

        // Sayfa indiriliyor ama artık yalnızca başlık var (25 kelimeden kısa)
        doReturn(new FetchedDocument(Kind.HTML,
                "<html><body><main><h1>Chamomile - herbal medicinal product</h1></main></body></html>"
                        .getBytes(StandardCharsets.UTF_8), "u"))
                .when(sourceFetcher).fetch(org.mockito.ArgumentMatchers.contains("nccih.nih.gov/health/chamomile"));
        mvc.perform(post("/admin/ingest"))
                .andExpect(jsonPath("$.sources[?(@.sourceId == 11)].status").value("boş: metin çıkarılamadı"));
        assertThat(countIngested(11)).isZero();
    }

    @Test
    void multiDrinkSourceIsStoredForEachDrink() throws Exception {
        // Kaynak 17 (ISAPP) hem kefir hem kombucha ile eşleşik
        doReturn(new FetchedDocument(Kind.HTML, longArticle("Fermented foods"), "u")).when(sourceFetcher).fetch(org.mockito.ArgumentMatchers.contains("doi.org/10.1038"));
        mvc.perform(post("/admin/ingest"));

        List<String> drinks = jdbc.queryForList("""
                SELECT DISTINCT d.slug FROM knowledge_chunks k JOIN drinks d ON d.id = k.drink_id
                WHERE k.source_id = 17 AND k.origin = 'ingested' ORDER BY d.slug""", String.class);
        assertThat(drinks).containsExactly("kefir", "kombucha");
    }

    @Test
    void ollamaDownStillSavesChunks() throws Exception {
        doReturn(new FetchedDocument(Kind.HTML, longArticle("Chamomile"), "u")).when(sourceFetcher).fetch(org.mockito.ArgumentMatchers.contains("nccih.nih.gov/health/chamomile"));
        doThrow(new RuntimeException("Connection refused")).when(embeddingModel).embed(anyList());

        mvc.perform(post("/admin/ingest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embeddingError").value(org.hamcrest.Matchers.containsString("ollama pull")));
        assertThat(countIngested(11)).isPositive();
    }

    private int countIngested(int sourceId) {
        return jdbc.queryForObject("SELECT count(*) FROM knowledge_chunks WHERE origin = 'ingested' AND source_id = ?",
                Integer.class, sourceId);
    }
}
