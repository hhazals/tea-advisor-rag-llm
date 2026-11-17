package com.tearag.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tearag.IntegrationTest;
import com.tearag.rag.EmbeddingIngestionServiceAccess;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * RAG akışı: retrieval (gerçek pgvector) + LLM (mock) + doğrulama + kural tabanlı geri dönüş.
 * Seed: rezene (fennel) parçalarının hepsi source_id=1; papatya parçaları 9 ve 11.
 */
class RagRecommendationTest extends IntegrationTest {

    static final String DAY = "2026-11-17";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    /** Embed. sorgu da aynı vektörü alırsa benzerlik 1. */
    static final float[] CHUNK_VECTOR = unit(0);

    @BeforeEach
    void setUp() {
        jdbc.update("UPDATE knowledge_chunks SET embedding = ?::vector, embedding_model = 'paraphrase-multilingual'",
                EmbeddingIngestionServiceAccess.toPgVector(CHUNK_VECTOR));
        when(embeddingModel.embed(anyString())).thenReturn(CHUNK_VECTOR);
        when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());

        int pizza = jdbc.queryForObject("INSERT INTO foods (name, food_category_id) VALUES ('cheese pizza', 1) RETURNING id",
                Integer.class);
        int meal = jdbc.queryForObject("INSERT INTO meals (meal_date, meal_type) VALUES (?, 'lunch') RETURNING id",
                Integer.class, LocalDate.parse(DAY));
        jdbc.update("INSERT INTO meal_items (meal_id, food_id, portion_gram) VALUES (?, ?, 250)", meal, pizza);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM meal_items");
        jdbc.update("DELETE FROM meals");
        jdbc.update("DELETE FROM foods");
        jdbc.update("UPDATE knowledge_chunks SET embedding = NULL, embedding_model = NULL");
    }

    @Test
    void validLlmAnswerReplacesReasonAndCitesSourcesFromDb() throws Exception {
        llmReturns("""
                {"drink": "fennel", "reason": "Rezene, şişkinlik ve gaz için geleneksel olarak kullanılır.", "source_ids": [1]}""");

        recommend()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedBy").value("rag_llm"))
                .andExpect(jsonPath("$.fallbackReason").doesNotExist())
                .andExpect(jsonPath("$.recommendation.drink.slug").value("fennel"))
                .andExpect(jsonPath("$.recommendation.reasonTr")
                        .value("Rezene, şişkinlik ve gaz için geleneksel olarak kullanılır."))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].url", containsString("ema.europa.eu")))
                .andExpect(jsonPath("$.usedChunkIds", hasSize(3)))
                .andExpect(jsonPath("$.alternatives[0].drink.slug").value("chamomile"));

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        String text = prompt.getValue().getContents();
        assertThat(text).contains("Kesin tıbbi fayda yazma")
                .contains("İÇECEK: Rezene (kod: fennel)")
                .contains("[kaynak_id=1] (geleneksel kullanım)")
                .contains("Pizza / Pide (cheese pizza)")
                .doesNotContain("[kaynak_id=11]"); // başka içeceğin parçası LLM'e gitmez
    }

    @Test
    void rejectsDifferentDrink() throws Exception {
        llmReturns("""
                {"drink": "ginger", "reason": "Zencefil iyi gelir.", "source_ids": [1]}""");

        recommend()
                .andExpect(jsonPath("$.generatedBy").value("rules"))
                .andExpect(jsonPath("$.fallbackReason", containsString("farklı içecek: ginger")))
                .andExpect(jsonPath("$.recommendation.drink.slug").value("fennel"))
                .andExpect(jsonPath("$.recommendation.reasonTr", containsString("Kanıt düzeyi")))
                .andExpect(jsonPath("$.sources", hasSize(0)));
    }

    @Test
    void rejectsSourceNotInRetrieval() throws Exception {
        llmReturns("""
                {"drink": "fennel", "reason": "Rezene geleneksel olarak kullanılır.", "source_ids": [1, 11]}""");

        recommend()
                .andExpect(jsonPath("$.generatedBy").value("rules"))
                .andExpect(jsonPath("$.fallbackReason", containsString("source_id: [11]")));
    }

    @Test
    void rejectsLinksAndMissingSources() throws Exception {
        llmReturns("""
                {"drink": "fennel", "reason": "Bkz. https://example.com", "source_ids": [1]}""");
        recommend().andExpect(jsonPath("$.fallbackReason", containsString("link")));

        llmReturns("""
                {"drink": "fennel", "reason": "Rezene.", "source_ids": []}""");
        recommend().andExpect(jsonPath("$.fallbackReason", containsString("kaynak gösterilmedi")));
    }

    @Test
    void llmErrorOrGarbageFallsBackToRules() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("429 quota"));
        recommend()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedBy").value("rules"))
                .andExpect(jsonPath("$.fallbackReason", containsString("LLM/retrieval hatası")));

        llmReturns("bu JSON değil");
        recommend().andExpect(jsonPath("$.generatedBy").value("rules"));
    }

    @Test
    void lowSimilaritySkipsLlm() throws Exception {
        when(embeddingModel.embed(anyString())).thenReturn(unit(1)); // parçalara dik → benzerlik 0

        recommend()
                .andExpect(jsonPath("$.generatedBy").value("rules"))
                .andExpect(jsonPath("$.fallbackReason", containsString("Benzerlik eşiğin altında")));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void missingEmbeddingsSkipLlm() throws Exception {
        jdbc.update("UPDATE knowledge_chunks SET embedding = NULL, embedding_model = NULL");

        recommend().andExpect(jsonPath("$.fallbackReason", containsString("/admin/embed")));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void llmFalseSkipsEverything() throws Exception {
        mvc.perform(get("/api/recommendation").param("date", DAY).param("evening", "false").param("llm", "false"))
                .andExpect(jsonPath("$.generatedBy").value("rules"))
                .andExpect(jsonPath("$.fallbackReason", containsString("llm=false")));
        verify(chatModel, never()).call(any(Prompt.class));
        verify(embeddingModel, never()).embed(anyString());
    }

    private ResultActions recommend() throws Exception {
        return mvc.perform(get("/api/recommendation").param("date", DAY).param("evening", "false"));
    }

    private void llmReturns(String content) {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(content)))));
    }

    private static float[] unit(int index) {
        float[] v = new float[768];
        v[index] = 1f;
        return v;
    }
}
