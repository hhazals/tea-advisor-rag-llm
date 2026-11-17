package com.tearag.meal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealRepository extends JpaRepository<Meal, Integer> {

    @EntityGraph(attributePaths = {"items", "items.food", "items.food.category"})
    List<Meal> findByMealDate(LocalDate mealDate);

    @EntityGraph(attributePaths = {"items", "items.food", "items.food.category"})
    Optional<Meal> findWithItemsById(Integer id);
}
