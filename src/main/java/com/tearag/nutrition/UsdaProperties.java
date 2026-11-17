package com.tearag.nutrition;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * tearag.usda.* ayarları.
 *
 * @param apiKey  https://fdc.nal.usda.gov/api-key-signup adresinden ücretsiz alınır;
 *                DEMO_KEY saatte ~30 istekle sınırlıdır.
 * @param dataTypes yemek (hazır ürün değil) verisi içeren USDA veri setleri
 */
@ConfigurationProperties("tearag.usda")
public record UsdaProperties(String baseUrl, String apiKey, List<String> dataTypes,
                             Duration connectTimeout, Duration readTimeout) {
}
