package com.tearag.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tearag.IntegrationTest;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class EmbeddingIngestionTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void resetEmbeddings() {
        jdbc.update("UPDATE knowledge_chunks SET embedding = NULL, embedding_model = NULL");
    }

    /** Sahte vektör; ilk eleman metnin sırası (ayırt edilebilsin). */
    private void stubEmbeddings(int dims) {
        when(embeddingModel.embed(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return IntStream.range(0, texts.size()).mapToObj(i -> {
                float[] v = new float[dims];
                v[0] = i + 1;
                v[1] = 0.5f;
                return v;
            }).toList();
        });
    }

    @Test
    void embedsAllChunksOnceThenOnlyOnForce() throws Exception {
        stubEmbeddings(768);

        mvc.perform(post("/admin/embed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embedded").value(21))
                .andExpect(jsonPath("$.total").value(21))
                .andExpect(jsonPath("$.model").value("paraphrase-multilingual"))
                .andExpect(jsonPath("$.dimensions").value(768));

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM knowledge_chunks
                WHERE embedding IS NOT NULL AND vector_dims(embedding) = 768
                  AND embedding_model = 'paraphrase-multilingual'""", Integer.class)).isEqualTo(21);

        // Tekrar çağrı yapılırsa hiçbir şey gömülmez
        mvc.perform(post("/admin/embed")).andExpect(jsonPath("$.embedded").value(0));

        // force: hepsi yeniden
        mvc.perform(post("/admin/embed").param("force", "true")).andExpect(jsonPath("$.embedded").value(21));
    }

    @Test
    void reembedsChunksProducedByAnotherModel() throws Exception {
        stubEmbeddings(768);
        mvc.perform(post("/admin/embed"));
        jdbc.update("UPDATE knowledge_chunks SET embedding_model = 'old-model' WHERE id IN (1, 2)");

        mvc.perform(post("/admin/embed")).andExpect(jsonPath("$.embedded").value(2));
    }

    @Test
    void ollamaDownGives503AndWritesNothing() throws Exception {
        when(embeddingModel.embed(anyList())).thenThrow(new RuntimeException("Connection refused"));

        mvc.perform(post("/admin/embed"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ollama pull")));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM knowledge_chunks WHERE embedding IS NOT NULL",
                Integer.class)).isZero();
    }

    @Test
    void wrongDimensionIsRejectedAndRolledBack() throws Exception {
        stubEmbeddings(384);

        mvc.perform(post("/admin/embed")).andExpect(status().is5xxServerError());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM knowledge_chunks WHERE embedding IS NOT NULL",
                Integer.class)).isZero();
    }

    @Test
    void formatsPgVector() {
        assertThat(EmbeddingIngestionService.toPgVector(new float[] {0.25f, -1f, 3f})).isEqualTo("[0.25,-1.0,3.0]");
    }
}
