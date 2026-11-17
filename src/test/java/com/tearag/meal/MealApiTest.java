//bu scripte review lazım 
package com.tearag.meal;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tearag.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class MealApiTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;


        //denediklerim bi kalsın burada
    int pide;    
    int baklava; 

    @BeforeEach
    void seedFoods() {
        pide = insertFood("Pide", 1, "260");
        baklava = insertFood("Baklava", 4, "430");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM meal_items");
        jdbc.update("DELETE FROM meals");
        jdbc.update("DELETE FROM foods");
    }

    @Test
    void createsMealAndComputesKcal() throws Exception {
        createMeal("""
                {"mealDate": "2026-11-17", "mealType": "lunch",
                 "items": [{"foodId": %d, "portionGram": 250}, {"foodId": %d, "portionGram": 300}]}"""
                .formatted(pide, ayran))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mealDate").value("2026-11-17"))
                .andExpect(jsonPath("$.mealType").value("lunch"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].foodName").value("Ayran"))
                .andExpect(jsonPath("$.items[0].kcal").doesNotExist())
                .andExpect(jsonPath("$.items[1].foodName").value("Pide"))
                .andExpect(jsonPath("$.items[1].categorySlug").value("pizza_pide"))
                .andExpect(jsonPath("$.items[1].kcal").value(650.0))
                .andExpect(jsonPath("$.totalKcal").value(650.0))
                .andExpect(jsonPath("$.kcalComplete").value(false));

        String stored = jdbc.queryForObject("SELECT meal_type FROM meals", String.class);
        org.assertj.core.api.Assertions.assertThat(stored).isEqualTo("lunch");
    }

    @Test
    void defaultsDateToToday() throws Exception {
        createMeal("""
                {"mealType": "snack", "items": [{"foodId": %d, "portionGram": 80}]}""".formatted(baklava))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mealDate").value(LocalDate.now().toString()));
    }

    @Test
    void listsMealsOfDayInMealOrder() throws Exception {
        createMeal(mealJson("2026-11-17", "dinner", baklava));
        createMeal(mealJson("2026-11-17", "breakfast", pide));
        createMeal(mealJson("2026-11-16", "lunch", pide));

        mvc.perform(get("/api/meals").param("date", "2026-11-17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].mealType").value("breakfast"))
                .andExpect(jsonPath("$[1].mealType").value("dinner"));
    }

    @Test
    void updateSyncsItems() throws Exception {
        int id = idOf(createMeal("""
                {"mealDate": "2026-11-17", "mealType": "dinner",
                 "items": [{"foodId": %d, "portionGram": 200}, {"foodId": %d, "portionGram": 250}]}"""
                .formatted(pide, ayran)));

        mvc.perform(put("/api/meals/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mealDate": "2026-11-17", "mealType": "dinner",
                                 "items": [{"foodId": %d, "portionGram": 100}, {"foodId": %d, "portionGram": 50}]}"""
                                .formatted(pide, baklava))) //mesela
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].foodName").value("Baklava"))
                .andExpect(jsonPath("$.items[0].kcal").value(215.0))
                .andExpect(jsonPath("$.items[1].foodName").value("Pide"))
                .andExpect(jsonPath("$.items[1].portionGram").value(100))
                .andExpect(jsonPath("$.totalKcal").value(475.0))
                .andExpect(jsonPath("$.kcalComplete").value(true));

        mvc.perform(get("/api/meals/{id}", id))
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void rejectsInvalidMeals() throws Exception {
        createMeal("""
                {"mealType": "lunch", "items": [{"foodId": %d, "portionGram": 100}, {"foodId": %d, "portionGram": 50}]}"""
                .formatted(pide, pide))
                .andExpect(status().isBadRequest());

        createMeal("""
                {"mealType": "lunch", "items": [{"foodId": 999999, "portionGram": 100}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Bulunamayan yemek id'leri: [999999]"));

        createMeal("""
                {"mealType": "brunch", "items": [{"foodId": %d, "portionGram": 100}]}""".formatted(pide))
                .andExpect(status().isBadRequest());

        createMeal("""
                {"mealType": "lunch", "items": []}""")
                .andExpect(status().isBadRequest());

        createMeal("""
                {"mealType": "lunch", "items": [{"foodId": %d, "portionGram": 0}]}""".formatted(pide))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/meals").param("date", "2026-11-17")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deletesMealWithItems() throws Exception {
        int id = idOf(createMeal(mealJson("2026-11-17", "lunch", pide)));

        mvc.perform(delete("/api/meals/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/meals/{id}", id)).andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("SELECT count(*) FROM meal_items", Integer.class)).isZero();
    }

    private ResultActions createMeal(String body) throws Exception {
        return mvc.perform(post("/api/meals").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int idOf(ResultActions result) throws Exception {
        return json.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asInt();
    }

    private static String mealJson(String date, String type, int foodId) {
        return """
                {"mealDate": "%s", "mealType": "%s", "items": [{"foodId": %d, "portionGram": 100}]}"""
                .formatted(date, type, foodId);
    }

    private int insertFood(String name, Integer categoryId, String kcal) {
        return jdbc.queryForObject(
                "INSERT INTO foods (name, food_category_id, kcal_per_100g) VALUES (?, ?, ?::numeric) RETURNING id",
                Integer.class, name, categoryId, kcal);
    }
}
