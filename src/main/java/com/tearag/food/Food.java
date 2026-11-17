package com.tearag.food;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "foods")
public class Food {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_category_id")
    private FoodCategory category;

    /** Kalori API'sindeki kimlik; NutritionService dolduracak. */
    @Column(name = "api_food_id", length = 60)
    private String apiFoodId;

    /** USDA'da eşleşen kaydın açıklaması; yanlış eşleşmeyi fark etmek için. */
    @Column(name = "api_food_description")
    private String apiFoodDescription;

    @Column(name = "kcal_per_100g", precision = 7, scale = 2)
    private BigDecimal kcalPer100g;

    @Column(name = "kcal_fetched_at")
    private OffsetDateTime kcalFetchedAt;

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public FoodCategory getCategory() {
        return category;
    }

    public void setCategory(FoodCategory category) {
        this.category = category;
    }

    public String getApiFoodId() {
        return apiFoodId;
    }

    public void setApiFoodId(String apiFoodId) {
        this.apiFoodId = apiFoodId;
    }

    public String getApiFoodDescription() {
        return apiFoodDescription;
    }

    public void setApiFoodDescription(String apiFoodDescription) {
        this.apiFoodDescription = apiFoodDescription;
    }

    public BigDecimal getKcalPer100g() {
        return kcalPer100g;
    }

    public void setKcalPer100g(BigDecimal kcalPer100g) {
        this.kcalPer100g = kcalPer100g;
    }

    public OffsetDateTime getKcalFetchedAt() {
        return kcalFetchedAt;
    }

    public void setKcalFetchedAt(OffsetDateTime kcalFetchedAt) {
        this.kcalFetchedAt = kcalFetchedAt;
    }
}
