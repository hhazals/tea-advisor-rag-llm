package com.tearag.food;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Seed verisinden gelen sabit kategori listesi; uygulama üzerinden değiştirilmez. */
@Entity
@Immutable
@Table(name = "food_categories")
public class FoodCategory {

    @Id
    private Integer id;

    @Column(nullable = false)
    private String slug;

    @Column(name = "name_tr", nullable = false)
    private String nameTr;

    private String description;

    protected FoodCategory() {
    }

    public Integer getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getNameTr() {
        return nameTr;
    }

    public String getDescription() {
        return description;
    }
}
