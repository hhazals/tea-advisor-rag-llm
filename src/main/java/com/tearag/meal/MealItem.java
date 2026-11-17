package com.tearag.meal;

import com.tearag.food.Food;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/** meal_items: birleşik anahtar (meal_id, food_id); aynı yemek bir öğünde bir kez bulunur. */
@Entity
@Table(name = "meal_items")
public class MealItem {

    @EmbeddedId
    private Id id = new Id();

    @MapsId("mealId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_id")
    private Meal meal;

    @MapsId("foodId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_id")
    private Food food;

    @Column(name = "portion_gram", nullable = false, precision = 7, scale = 2)
    private BigDecimal portionGram;

    protected MealItem() {
    }

    public MealItem(Meal meal, Food food, BigDecimal portionGram) {
        this.meal = meal;
        this.food = food;
        this.portionGram = portionGram;
    }

    public Meal getMeal() {
        return meal;
    }

    public Food getFood() {
        return food;
    }

    public BigDecimal getPortionGram() {
        return portionGram;
    }

    public void setPortionGram(BigDecimal portionGram) {
        this.portionGram = portionGram;
    }

    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "meal_id")
        private Integer mealId;

        @Column(name = "food_id")
        private Integer foodId;

        @Override
        public boolean equals(Object o) {
            return o instanceof Id other && Objects.equals(mealId, other.mealId) && Objects.equals(foodId, other.foodId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mealId, foodId);
        }
    }
}
