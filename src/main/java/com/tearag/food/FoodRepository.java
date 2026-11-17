package com.tearag.food;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FoodRepository extends JpaRepository<Food, Integer>, JpaSpecificationExecutor<Food> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Integer id);

    Optional<Food> findFirstByNameIgnoreCase(String name);
}
