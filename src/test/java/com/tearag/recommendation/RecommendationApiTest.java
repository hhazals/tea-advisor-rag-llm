package com.tearag.recommendation;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tearag.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Bize kalmış kural:
 * (primary 10, alternative 5; akşam yemeği ağırlığı 2; her caution × 0,5).
 * Kategori id'leri: 1 pizza_pide, 2 red_meat_bbq, 3 chicken, 4 sweets_chocolate, 5 pasta.
 */
class RecommendationApiTest extends IntegrationTest {

    static final String DAY = "2026-11-17";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM meal_items");
        jdbc.update("DELETE FROM meals");
        jdbc.update("DELETE FROM foods");
    }

    @Test
    void picksPrimaryDrinkWithTieBrokenAlternatives() throws Exception {
        meal("lunch", food("cheese pizza", 1));
        recommend("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedBy").value("rules"))
                .andExpect(jsonPath("$.recommendation.drink.slug").value("fennel"))
                .andExpect(jsonPath("$.recommendation.score").value(10.0))
                .andExpect(jsonPath("$.recommendation.reasonTr", containsString("Pizza / Pide")))
                .andExpect(jsonPath("$.recommendation.reasonTr", containsString("Kanıt düzeyi: Geleneksel kullanım (EMA)")))
                .andExpect(jsonPath("$.alternatives", hasSize(2)))
                .andExpect(jsonPath("$.alternatives[0].drink.slug").value("chamomile"))
                .andExpect(jsonPath("$.alternatives[1].drink.slug").value("kefir"))
                .andExpect(jsonPath("$.excluded", hasSize(0)))
                .andExpect(jsonPath("$.disclaimer", containsString("sağlık tavsiyesi değildir")));
    }

    @Test
    void dinnerCountsDoubleAndCategoriesAccumulate() throws Exception {
        meal("lunch", food("cheese pizza", 1));
        meal("dinner", food("creamy pasta", 5));
        recommend("")
                .andExpect(jsonPath("$.categories", hasSize(2)))
                .andExpect(jsonPath("$.categories[1].slug").value("pasta"))
                .andExpect(jsonPath("$.categories[1].weight").value(2))
                .andExpect(jsonPath("$.recommendation.drink.slug").value("fennel"))
                .andExpect(jsonPath("$.recommendation.score").value(30.0))
                .andExpect(jsonPath("$.recommendation.ruleReasons", hasSize(2)))
                .andExpect(jsonPath("$.alternatives[0].score").value(15.0));
    }

    @Test
    void cautionHalvesScoreAndLosesTies() throws Exception {
        meal("dinner", food("lamb kebab", 2));
        recommend("pregnancy")
                .andExpect(jsonPath("$.recommendation.drink.slug").value("fennel"))
                .andExpect(jsonPath("$.alternatives[0].drink.slug").value("kefir"))
                .andExpect(jsonPath("$.alternatives[1].drink.slug").value("ginger"))
                .andExpect(jsonPath("$.alternatives[1].score").value(10.0))
                .andExpect(jsonPath("$.alternatives[1].cautions[0].conditionCode").value("pregnancy"))
                .andExpect(jsonPath("$.alternatives[1].cautions[0].source.url",
                        containsString("ema.europa.eu")));
    }

    @Test
    void eveningLowersCaffeinatedKombucha() throws Exception {
        meal("lunch", food("grilled chicken", 3));
        mvc.perform(get("/api/recommendation").param("date", DAY).param("evening", "true").param("llm", "false"))
                .andExpect(jsonPath("$.evening").value(true))
                .andExpect(jsonPath("$.recommendation.drink.slug").value("chamomile"))
                .andExpect(jsonPath("$.alternatives[*].drink.slug", org.hamcrest.Matchers.contains("fennel", "kefir")));
    }

    @Test
    void excludeRemovesDrinkAndExplainsWhy() throws Exception {
        meal("lunch", food("waffle", 4));
        recommend("under_12")
                .andExpect(jsonPath("$.recommendation.drink.slug").value("chamomile"))
                .andExpect(jsonPath("$.alternatives", hasSize(0)))
                .andExpect(jsonPath("$.excluded", hasSize(1)))
                .andExpect(jsonPath("$.excluded[0].drink.slug").value("melissa"))
                .andExpect(jsonPath("$.excluded[0].reasons[0].severity").value("exclude"))
                .andExpect(jsonPath("$.excluded[0].reasons[0].source.authority",
                        containsString("European Medicines Agency")));
    }

    @Test
    void conditionSpecificInfoNotesAreShown() throws Exception {
        meal("lunch", food("grilled chicken", 3));
        recommend("pregnancy")
                .andExpect(jsonPath("$.recommendation.drink.slug").value("chamomile"))
                .andExpect(jsonPath("$.recommendation.infos[*].conditionCode", hasItem("pregnancy")))
                .andExpect(jsonPath("$.excluded[0].drink.slug").value("kombucha"));
    }

    @Test
    void inactiveDrinkIsNeverRecommended() throws Exception {
        meal("lunch", food("waffle", 4));
        recommend("reflux")
                .andExpect(jsonPath("$.recommendation.drink.slug").value("melissa"))
                .andExpect(jsonPath("$.alternatives[*].drink.slug", org.hamcrest.Matchers.not(hasItem("peppermint"))));
    }

    @Test
    void reportsUncategorizedFoodsAndEmptyDays() throws Exception {
        recommend("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendation").doesNotExist())
                .andExpect(jsonPath("$.message").value("Bu tarih için kayıtlı öğün yok."));

        meal("lunch", food("mystery stew", null));
        recommend("")
                .andExpect(jsonPath("$.recommendation").doesNotExist())
                .andExpect(jsonPath("$.uncategorizedFoods[0]").value("mystery stew"))
                .andExpect(jsonPath("$.message", containsString("kategorisi belirtilmemiş")));
    }

    @Test
    void validatesConditions() throws Exception {
        recommend("reflux,flu").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("[flu]")));
        recommend("evening").andExpect(status().isBadRequest());

        mvc.perform(get("/api/recommendation/conditions"))
                .andExpect(jsonPath("$", hasItem("reflux")))
                .andExpect(jsonPath("$", hasItem("asteraceae_allergy")))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.not(hasItem("evening"))));
    }

    private ResultActions recommend(String conditions) throws Exception {
        return mvc.perform(get("/api/recommendation").param("date", DAY)
                .param("conditions", conditions).param("evening", "false").param("llm", "false"));
    }

    private int food(String name, Integer categoryId) {
        return jdbc.queryForObject("INSERT INTO foods (name, food_category_id) VALUES (?, ?) RETURNING id",
                Integer.class, name, categoryId);
    }

    private void meal(String type, int foodId) {
        Integer mealId = jdbc.queryForObject(
                "INSERT INTO meals (meal_date, meal_type) VALUES (?, ?) RETURNING id",
                Integer.class, LocalDate.parse(DAY), type);
        jdbc.update("INSERT INTO meal_items (meal_id, food_id, portion_gram) VALUES (?, ?, 200)", mealId, foodId);
    }
}
