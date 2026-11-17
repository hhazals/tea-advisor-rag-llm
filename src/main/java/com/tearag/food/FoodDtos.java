package com.tearag.food;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** /api/foods ve /api/food-categories istek/cevap modelleri. */
public final class FoodDtos {

    private FoodDtos() {
    }

    /**
     * kcalPer100g isteğe bağlıdır: elle girilebilir, yoksa NutritionService dış API'den doldurur.
     */
    public record FoodRequest(
            @NotBlank @Size(max = 120) String name,
            Integer categoryId,
            @DecimalMin("0") @Digits(integer = 5, fraction = 2) BigDecimal kcalPer100g) {
    }

    public record CategoryResponse(Integer id, String slug, String nameTr, String description) {

        static CategoryResponse from(FoodCategory c) {
            return new CategoryResponse(c.getId(), c.getSlug(), c.getNameTr(), c.getDescription());
        }
    }

    public record CategoryRef(Integer id, String slug, String nameTr) {

        static CategoryRef from(FoodCategory c) {
            return c == null ? null : new CategoryRef(c.getId(), c.getSlug(), c.getNameTr());
        }
    }

    /**
     * kcalSource: "usda" (API'den), "manual" (elle girildi) veya null (kalori yok).
     * usdaDescription: USDA'da eşleşen kayıt; yanlış eşleşmeyi fark etmek için gösterilir.
     */
    public record FoodResponse(Integer id, String name, CategoryRef category, BigDecimal kcalPer100g,
                               String kcalSource, String usdaFdcId, String usdaDescription) {

        static FoodResponse from(Food f) {
            String source = f.getApiFoodId() != null ? "usda" : f.getKcalPer100g() != null ? "manual" : null;
            return new FoodResponse(f.getId(), f.getName(), CategoryRef.from(f.getCategory()), f.getKcalPer100g(),
                    source, f.getApiFoodId(), f.getApiFoodDescription());
        }
    }
}
