package com.tearag.food;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tearag.IntegrationTest;
import com.tearag.nutrition.CalorieLookup.CalorieLookupException;
import com.tearag.nutrition.CalorieLookup.Match;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class FoodApiTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM meal_items");
        jdbc.update("DELETE FROM meals");
        jdbc.update("DELETE FROM foods");
    }

    private static final Match PIZZA = new Match("2708618", "Pizza, cheese, stuffed crust", new BigDecimal("274.00"));

    @Test
    void fetchesKcalFromUsdaOnCreateAndCachesIt() throws Exception {
        when(calorieLookup.findByName("cheese pizza")).thenReturn(Optional.of(PIZZA));

        int id = createFood("""
                {"name": "cheese pizza", "categoryId": 1}""");

        mvc.perform(get("/api/foods/{id}", id))
                .andExpect(jsonPath("$.kcalPer100g").value(274.0))
                .andExpect(jsonPath("$.kcalSource").value("usda"))
                .andExpect(jsonPath("$.usdaFdcId").value("2708618"))
                .andExpect(jsonPath("$.usdaDescription").value("Pizza, cheese, stuffed crust"));
        assertThat(jdbc.queryForObject("SELECT kcal_fetched_at IS NOT NULL FROM foods WHERE id = ?",
                Boolean.class, id)).isTrue();

        // Kategori değişikliği (isim aynı) API'yi tekrar çağırmaz, değer korunur
        mvc.perform(put("/api/foods/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "cheese pizza", "categoryId": 4}"""))
                .andExpect(jsonPath("$.kcalPer100g").value(274.0));
        verify(calorieLookup, times(1)).findByName(any());
    }

    @Test
    void manualKcalSkipsUsdaAndClearsApiInfo() throws Exception {
        when(calorieLookup.findByName("cheese pizza")).thenReturn(Optional.of(PIZZA));
        int id = createFood("""
                {"name": "cheese pizza"}""");

        mvc.perform(put("/api/foods/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "cheese pizza", "kcalPer100g": 300}"""))
                .andExpect(jsonPath("$.kcalPer100g").value(300))
                .andExpect(jsonPath("$.kcalSource").value("manual"))
                .andExpect(jsonPath("$.usdaFdcId").doesNotExist());

        createFood("""
                {"name": "baklava", "kcalPer100g": 430}""");
        verify(calorieLookup, never()).findByName("baklava");
    }

    @Test
    void usdaOutageDoesNotBlockCreate() throws Exception {
        when(calorieLookup.findByName(any())).thenThrow(new CalorieLookupException("down", null));

        int id = createFood("""
                {"name": "doner kebab", "categoryId": 2}""");

        mvc.perform(get("/api/foods/{id}", id))
                .andExpect(jsonPath("$.kcalPer100g").doesNotExist())
                .andExpect(jsonPath("$.kcalSource").doesNotExist());
    }

    @Test
    void refreshEndpoint() throws Exception {
        int id = createFood("""
                {"name": "cheese pizza", "kcalPer100g": 300}""");

        when(calorieLookup.findByName("cheese pizza")).thenReturn(Optional.of(PIZZA));
        mvc.perform(post("/api/foods/{id}/kcal/refresh", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kcalPer100g").value(274.0))
                .andExpect(jsonPath("$.kcalSource").value("usda"));

        // Eşleşme yok: 422, mevcut değer korunur
        when(calorieLookup.findByName("cheese pizza")).thenReturn(Optional.empty());
        mvc.perform(post("/api/foods/{id}/kcal/refresh", id))
                .andExpect(status().isUnprocessableEntity());

        // Servis çökük: 503, mevcut değer korunur
        when(calorieLookup.findByName("cheese pizza")).thenThrow(new CalorieLookupException("down", null));
        mvc.perform(post("/api/foods/{id}/kcal/refresh", id))
                .andExpect(status().isServiceUnavailable());

        mvc.perform(get("/api/foods/{id}", id))
                .andExpect(jsonPath("$.kcalPer100g").value(274.0));
        mvc.perform(post("/api/foods/{id}/kcal/refresh", 999999)).andExpect(status().isNotFound());
    }

    @Test
    void listsSeededCategories() throws Exception {
        mvc.perform(get("/api/food-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].slug").value("pizza_pide"))
                .andExpect(jsonPath("$[0].nameTr").value("Pizza / Pide"));
    }

    @Test
    void fullCrudFlow() throws Exception {
        int id = createFood("""
                {"name": "Kaşarlı pide", "categoryId": 1, "kcalPer100g": 260}""");

        mvc.perform(get("/api/foods/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kaşarlı pide"))
                .andExpect(jsonPath("$.category.slug").value("pizza_pide"))
                .andExpect(jsonPath("$.kcalPer100g").value(260));

        mvc.perform(put("/api/foods/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Kıymalı pide", "categoryId": 1}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kıymalı pide"))
                .andExpect(jsonPath("$.kcalPer100g").doesNotExist());

        mvc.perform(delete("/api/foods/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/foods/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void searchesByNameAndFiltersByCategory() throws Exception {
        createFood("""
                {"name": "Adana kebap", "categoryId": 2}""");
        createFood("""
                {"name": "Tavuk şiş", "categoryId": 3}""");
        createFood("""
                {"name": "Kremalı makarna", "categoryId": 5}""");

        mvc.perform(get("/api/foods").param("q", "KEBAP"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Adana kebap"));

        mvc.perform(get("/api/foods").param("categoryId", "3"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Tavuk şiş"));

        // LIKE joker karakterleri literal aranır
        mvc.perform(get("/api/foods").param("q", "%"))
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(get("/api/foods"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("Adana kebap"));
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        createFood("""
                {"name": "Waffle"}""");

        mvc.perform(post("/api/foods").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Waffle"}"""))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/foods").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Sushi", "categoryId": 99}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Geçersiz kategori: 99"));

        mvc.perform(post("/api/foods").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "  "}"""))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/foods").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Çikolata", "kcalPer100g": -5}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotDeleteFoodUsedInMeal() throws Exception {
        int foodId = createFood("""
                {"name": "Somon", "categoryId": 6}""");
        mvc.perform(post("/api/meals").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mealType": "dinner", "items": [{"foodId": %d, "portionGram": 200}]}"""
                                .formatted(foodId)))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/foods/{id}", foodId)).andExpect(status().isConflict());
    }

    private int createFood(String body) throws Exception {
        String response = mvc.perform(post("/api/foods").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(response);
        return node.get("id").asInt();
    }
}
