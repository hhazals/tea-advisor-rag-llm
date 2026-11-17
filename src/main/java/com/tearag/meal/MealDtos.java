package com.tearag.meal;

import com.tearag.food.Food;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** /api/meals istek/cevap modelleri. */
public final class MealDtos {

    private MealDtos() {
    }

    /** mealDate boşsa bugünün tarihi kullanılır. */
    public record MealRequest(
            LocalDate mealDate,
            @NotNull MealType mealType,
            @NotEmpty List<@Valid @NotNull MealItemRequest> items) {
    }

    public record MealItemRequest(
            @NotNull Integer foodId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 5, fraction = 2)
            BigDecimal portionGram) {
    }

    /** kcal: kalorisi bilinmeyen yemekte null. */
    public record MealItemResponse(Integer foodId, String foodName, String categorySlug,
                                   BigDecimal portionGram, BigDecimal kcal) {

        static MealItemResponse from(MealItem item) {
            Food f = item.getFood();
            BigDecimal kcal = f.getKcalPer100g() == null ? null
                    : f.getKcalPer100g().multiply(item.getPortionGram())
                            .divide(BigDecimal.valueOf(100), 1, RoundingMode.HALF_UP);
            return new MealItemResponse(f.getId(), f.getName(),
                    f.getCategory() == null ? null : f.getCategory().getSlug(),
                    item.getPortionGram(), kcal);
        }
    }

    /**
     * totalKcal yalnızca kalorisi bilinen kalemlerin toplamıdır;
     * kcalComplete=false ise bazı kalemlerin kalorisi henüz yok.
     */
    public record MealResponse(Integer id, LocalDate mealDate, MealType mealType, OffsetDateTime createdAt,
                               List<MealItemResponse> items, BigDecimal totalKcal, boolean kcalComplete) {

        static MealResponse from(Meal meal) {
            List<MealItemResponse> items = meal.getItems().stream()
                    .map(MealItemResponse::from)
                    .sorted(Comparator.comparing(MealItemResponse::foodName))
                    .toList();
            BigDecimal total = items.stream().map(MealItemResponse::kcal).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean complete = items.stream().allMatch(i -> i.kcal() != null);
            return new MealResponse(meal.getId(), meal.getMealDate(), meal.getMealType(), meal.getCreatedAt(),
                    items, total, complete);
        }
    }
}
