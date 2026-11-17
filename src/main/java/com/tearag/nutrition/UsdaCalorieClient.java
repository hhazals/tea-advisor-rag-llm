package com.tearag.nutrition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * USDA FoodData Central arama API'si: POST /v1/foods/search.
 * Survey (FNDDS), SR Legacy ve Foundation kayıtlarında besin değerleri 100 g başınadır.
 */
@Component
public class UsdaCalorieClient implements CalorieLookup {

    private static final Logger log = LoggerFactory.getLogger(UsdaCalorieClient.class);

    /** Öncelik sırasıyla enerji besin id'leri: Energy (kcal), Atwater özel, Atwater genel. */
    private static final List<Integer> ENERGY_NUTRIENT_IDS = List.of(1008, 2048, 2047);
    private static final int PAGE_SIZE = 10;

    private final RestClient http;
    private final UsdaProperties props;

    @Autowired
    public UsdaCalorieClient(RestClient.Builder builder, UsdaProperties props) {
        this.props = props;
        this.http = builder.baseUrl(props.baseUrl()).requestFactory(requestFactory(props)).build();
    }

    /** Testlerde MockRestServiceServer'a bağlı RestClient vermek için. */
    UsdaCalorieClient(RestClient http, UsdaProperties props) {
        this.http = http;
        this.props = props;
    }

    @Override
    public Optional<Match> findByName(String foodName) {
        SearchResponse response;
        try {
            // GET + dataType filtresi bazı sorgularda (ör. "waffle") 400 dönüyor; POST gövdesi güvenilir.
            response = http.post()
                    .uri(uri -> uri.path("/v1/foods/search").queryParam("api_key", props.apiKey()).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SearchRequest(foodName, props.dataTypes(), PAGE_SIZE))
                    .retrieve()
                    .body(SearchResponse.class);
        } catch (RestClientException e) {
            // Mesajda URL (ve API anahtarı) olabileceği için yalnızca hata tipini logla
            String reason = e instanceof RestClientResponseException r
                    ? "HTTP " + r.getStatusCode().value() + (r.getStatusCode().value() == 429 ? " - istek limiti doldu" : "")
                    : e.getClass().getSimpleName();
            log.warn("USDA araması başarısız: '{}' ({})", foodName, reason);
            throw new CalorieLookupException("USDA kalori servisine ulaşılamadı", e);
        }
        if (response == null || response.foods() == null) {
            return Optional.empty();
        }
        // USDA alaka sırasına göre döner; enerji değeri olan ilk kaydı al
        return response.foods().stream()
                .map(UsdaCalorieClient::toMatch)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<Match> toMatch(SearchFood food) {
        if (food.foodNutrients() == null) {
            return Optional.empty();
        }
        for (Integer id : ENERGY_NUTRIENT_IDS) {
            Optional<BigDecimal> kcal = food.foodNutrients().stream()
                    .filter(n -> id.equals(n.nutrientId()) && "KCAL".equalsIgnoreCase(n.unitName()) && n.value() != null)
                    .map(n -> n.value().setScale(2, RoundingMode.HALF_UP))
                    .findFirst();
            if (kcal.isPresent()) {
                return Optional.of(new Match(String.valueOf(food.fdcId()), food.description(), kcal.get()));
            }
        }
        return Optional.empty();
    }

    private static SimpleClientHttpRequestFactory requestFactory(UsdaProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());
        return factory;
    }

    record SearchRequest(String query, List<String> dataType, int pageSize) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchResponse(List<SearchFood> foods) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchFood(Long fdcId, String description, String dataType, List<SearchNutrient> foodNutrients) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchNutrient(Integer nutrientId, String unitName, BigDecimal value) {
    }
}
