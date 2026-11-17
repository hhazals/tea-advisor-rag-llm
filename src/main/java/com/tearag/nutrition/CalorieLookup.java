package com.tearag.nutrition;

import java.math.BigDecimal;
import java.util.Optional;

/** Dış kalori kaynağı (şu an USDA FoodData Central). Testlerde mock'lanır. */
public interface CalorieLookup {

    /**
     * Yemek adına (İngilizce) göre 100 g başına kaloriyi arar.
     *
     * @return eşleşme yoksa boş
     * @throws CalorieLookupException API'ye ulaşılamazsa veya hata dönerse
     */
    Optional<Match> findByName(String foodName);

    record Match(String apiFoodId, String description, BigDecimal kcalPer100g) {
    }

    class CalorieLookupException extends RuntimeException {

        public CalorieLookupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
