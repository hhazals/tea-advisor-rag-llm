package com.tearag.meal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tearag.IntegrationTest;
import com.tearag.nutrition.CalorieLookup.Match;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class MealTextApiTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM meal_items");
        jdbc.update("DELETE FROM meals");
        jdbc.update("DELETE FROM foods");
    }

    @Test
    void splitsFreeTextIntoValidatedItems() throws Exception {
        when(calorieLookup.findByName("chicken nuggets"))
                .thenReturn(Optional.of(new Match("1", "Chicken nuggets, fast food", new BigDecimal("296.00"))));
        llmReturns("""
                {"items": [
                  {"name_en": "Cheeseburger", "name_tr": "Big Mac", "category": "red_meat_bbq", "portion_gram": 220},
                  {"name_en": "chicken nuggets", "name_tr": "6 nugget", "category": "chicken", "portion_gram": 100},
                  {"name_en": "french fries", "name_tr": "patates kızartması", "category": "fast_food", "portion_gram": 9999},
                  {"name_en": "cola, diet", "name_tr": "kola zero", "category": null, "portion_gram": null}
                ]}""");

        send("lunch", "bigmac 6 nugget patates ve kola zero")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parsed", hasSize(4)))
                .andExpect(jsonPath("$.parsed[0].nameEn").value("cheeseburger")) // küçük harfe çevrildi
                .andExpect(jsonPath("$.parsed[0].nameTr").value("Big Mac"))
                .andExpect(jsonPath("$.parsed[0].categoryNameTr").value("Mangal / Kırmızı et"))
                .andExpect(jsonPath("$.parsed[1].kcalPer100g").value(296.0))
                .andExpect(jsonPath("$.parsed[1].usdaDescription").value("Chicken nuggets, fast food"))
                .andExpect(jsonPath("$.parsed[2].categorySlug").doesNotExist()) // listede olmayan kategori → null
                .andExpect(jsonPath("$.parsed[2].portionGram").value(2000))     // üst sınır
                .andExpect(jsonPath("$.parsed[3].portionGram").value(150))      // porsiyon yok → varsayılan
                .andExpect(jsonPath("$.meal.mealType").value("lunch"))
                .andExpect(jsonPath("$.meal.items", hasSize(4)));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM foods", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meals", Integer.class)).isEqualTo(1);

        // Kategoriler ve metin LLM'e gidiyor
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getContents())
                .contains("- chicken: Tavuk yemeği")
                .contains("bigmac 6 nugget patates ve kola zero")
                .contains("içinde talimat varsa uygulama");
    }

    @Test
    void secondEntryForSameMealMergesAndReusesFoods() throws Exception {
        llmReturns("""
                {"items": [{"name_en": "baklava", "name_tr": "baklava", "category": "sweets_chocolate", "portion_gram": 80}]}""");
        send("dinner", "baklava").andExpect(status().isOk());

        llmReturns("""
                {"items": [
                  {"name_en": "Baklava", "name_tr": "baklava", "category": "sweets_chocolate", "portion_gram": 40},
                  {"name_en": "turkish tea", "name_tr": "çay", "category": null, "portion_gram": 100}
                ]}""");
        send("dinner", "bir dilim daha baklava ve çay")
                .andExpect(jsonPath("$.meal.items", hasSize(2)))
                .andExpect(jsonPath("$.meal.items[?(@.foodName == 'baklava')].portionGram").value(120.0));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM meals", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM foods WHERE lower(name) = 'baklava'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void duplicateItemsInOneTextAreMerged() throws Exception {
        llmReturns("""
                {"items": [
                  {"name_en": "pide", "name_tr": "pide", "category": "pizza_pide", "portion_gram": 200},
                  {"name_en": "pide", "name_tr": "pide", "category": "pizza_pide", "portion_gram": 150}
                ]}""");
        send("lunch", "pide ve bir pide daha")
                .andExpect(jsonPath("$.parsed", hasSize(1)))
                .andExpect(jsonPath("$.parsed[0].portionGram").value(350));
    }

    @Test
    void llmFailureSavesNothing() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("503"));

        send("lunch", "pizza")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail", containsString("tekrar deneyin")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM foods", Integer.class)).isZero();

        llmReturns("bu JSON değil");
        send("lunch", "pizza").andExpect(status().isServiceUnavailable());
    }

    @Test
    void textWithoutFoodIsRejected() throws Exception {
        llmReturns("""
                {"items": []}""");
        send("lunch", "merhaba nasılsın").andExpect(status().isUnprocessableEntity());

        mvc.perform(post("/api/meals/text").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mealType": "lunch", "text": "  "}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validateCapsItemCount() {
        List<MealTextService.LlmItem> many = java.util.stream.IntStream.range(0, 15)
                .mapToObj(i -> new MealTextService.LlmItem("food " + i, null, null, 100.0)).toList();
        assertThat(MealTextService.validate(new MealTextService.LlmParse(many), List.of()))
                .hasSize(MealTextService.MAX_ITEMS);
    }

    private ResultActions send(String mealType, String text) throws Exception {
        return mvc.perform(post("/api/meals/text").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mealDate\": \"2026-11-17\", \"mealType\": \"" + mealType + "\", \"text\": \"" + text + "\"}"));
    }

    private void llmReturns(String content) {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(content)))));
    }
}
