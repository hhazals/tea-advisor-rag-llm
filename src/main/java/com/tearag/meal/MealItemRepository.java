package com.tearag.meal;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MealItemRepository extends JpaRepository<MealItem, MealItem.Id> {

    boolean existsByFood_Id(Integer foodId);
}
