package com.tearag.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

import com.tearag.nutrition.CalorieLookup.CalorieLookupException;
import com.tearag.nutrition.CalorieLookup.Match;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** USDA cevap ayrıştırma */
class UsdaCalorieClientTest {

    private MockRestServiceServer server;
    private UsdaCalorieClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://usda.test/fdc");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new UsdaCalorieClient(builder.build(), new UsdaProperties("https://usda.test/fdc", "test-key",
                List.of("Survey (FNDDS)", "SR Legacy", "Foundation"), Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }

    @Test
    void picksFirstResultWithEnergyAndSendsExpectedQuery() {
        server.expect(requestTo(startsWith("https://usda.test/fdc/v1/foods/search")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(queryParam("api_key", "test-key"))
                .andExpect(content().json("""
                        {"query": "cheese pizza", "dataType": ["Survey (FNDDS)", "SR Legacy", "Foundation"],
                         "pageSize": 10}""", JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"totalHits": 3, "foods": [
                          {"fdcId": 1, "description": "No energy here", "dataType": "Survey (FNDDS)",
                           "foodNutrients": [{"nutrientId": 1003, "unitName": "G", "value": 11.2}]},
                          {"fdcId": 2708618, "description": "Pizza, cheese, stuffed crust", "dataType": "Survey (FNDDS)",
                           "foodNutrients": [
                             {"nutrientId": 1008, "nutrientName": "Energy", "unitName": "KCAL", "value": 274},
                             {"nutrientId": 1003, "unitName": "G", "value": 12.1}]},
                          {"fdcId": 3, "description": "Later result", "dataType": "SR Legacy",
                           "foodNutrients": [{"nutrientId": 1008, "unitName": "KCAL", "value": 999}]}
                        ]}""", MediaType.APPLICATION_JSON));

        Optional<Match> match = client.findByName("cheese pizza");

        assertThat(match).contains(new Match("2708618", "Pizza, cheese, stuffed crust", new BigDecimal("274.00")));
        server.verify();
    }

    @Test
    void fallsBackToAtwaterEnergyAndIgnoresKilojoules() {
        server.expect(requestTo(startsWith("https://usda.test/fdc/v1/foods/search")))
                .andRespond(withSuccess("""
                        {"foods": [{"fdcId": 42, "description": "Salmon, raw", "dataType": "Foundation",
                          "foodNutrients": [
                            {"nutrientId": 1062, "unitName": "kJ", "value": 800},
                            {"nutrientId": 2047, "unitName": "KCAL", "value": 190.5},
                            {"nutrientId": 2048, "unitName": "KCAL", "value": 186.456}]}]}""",
                        MediaType.APPLICATION_JSON));

        assertThat(client.findByName("salmon"))
                .contains(new Match("42", "Salmon, raw", new BigDecimal("186.46")));
    }

    @Test
    void emptyResultMeansNoMatch() {
        server.expect(requestTo(startsWith("https://usda.test/fdc/v1/foods/search")))
                .andRespond(withSuccess("""
                        {"totalHits": 0, "foods": []}""", MediaType.APPLICATION_JSON));

        assertThat(client.findByName("xyzzy")).isEmpty();
    }

    @Test
    void serverErrorBecomesLookupException() {
        server.expect(requestTo(startsWith("https://usda.test/fdc/v1/foods/search")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.findByName("pizza")).isInstanceOf(CalorieLookupException.class);
    }
}
