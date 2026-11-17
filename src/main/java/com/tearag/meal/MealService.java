package com.tearag.meal;

import com.tearag.common.ApiException;
import com.tearag.food.Food;
import com.tearag.food.FoodRepository;
import com.tearag.meal.MealDtos.MealItemRequest;
import com.tearag.meal.MealDtos.MealRequest;
import com.tearag.meal.MealDtos.MealResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MealService {

    private final MealRepository meals;
    private final FoodRepository foods;

    public MealService(MealRepository meals, FoodRepository foods) {
        this.meals = meals;
        this.foods = foods;
    }

    /** Bir günün öğünleri; kahvaltı → öğle → akşam → ara öğün sırasıyla. */
    @Transactional(readOnly = true)
    public List<MealResponse> listByDate(LocalDate date) {
        return meals.findByMealDate(date).stream()
                .sorted(Comparator.comparing(Meal::getMealType).thenComparing(Meal::getId))
                .map(MealResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MealResponse get(Integer id) {
        return MealResponse.from(find(id));
    }

    public MealResponse create(MealRequest req) {
        Meal meal = new Meal();
        apply(meal, req);
        return MealResponse.from(meals.save(meal));
    }

    public MealResponse update(Integer id, MealRequest req) {
        Meal meal = find(id);
        apply(meal, req);
        meals.flush();
        return MealResponse.from(meal);
    }

    /**
     * O gün o öğün türünde bir öğün varsa kalemleri ona ekler (aynı yemek varsa porsiyonlar toplanır),
     * yoksa yeni öğün oluşturur.
     */
    public MealResponse addItems(LocalDate date, MealType type, Map<Integer, BigDecimal> portionsByFoodId) {
        Meal meal = meals.findByMealDate(date).stream()
                .filter(m -> m.getMealType() == type)
                .min(Comparator.comparing(Meal::getId))
                .orElseGet(() -> {
                    Meal m = new Meal();
                    m.setMealDate(date);
                    m.setMealType(type);
                    return m;
                });
        Map<Integer, Food> found = foods.findAllById(portionsByFoodId.keySet()).stream()
                .collect(Collectors.toMap(Food::getId, Function.identity()));
        portionsByFoodId.forEach((foodId, portion) -> {
            Food food = found.get(foodId);
            if (food == null) {
                throw ApiException.badRequest("Bulunamayan yemek id'si: " + foodId);
            }
            meal.getItems().stream().filter(i -> i.getFood().getId().equals(foodId)).findFirst()
                    .ifPresentOrElse(
                            i -> i.setPortionGram(i.getPortionGram().add(portion)),
                            () -> meal.getItems().add(new MealItem(meal, food, portion)));
        });
        Meal saved = meals.save(meal);
        meals.flush();
        return MealResponse.from(saved);
    }

    public void delete(Integer id) {
        meals.delete(find(id)); // meal_items: cascade + DB'de ON DELETE CASCADE
    }

    private Meal find(Integer id) {
        return meals.findWithItemsById(id).orElseThrow(() -> ApiException.notFound("Öğün bulunamadı: " + id));
    }

    private void apply(Meal meal, MealRequest req) {
        meal.setMealDate(req.mealDate() != null ? req.mealDate() : LocalDate.now());
        meal.setMealType(req.mealType());
        syncItems(meal, req.items());
    }

    /**
     * Kalemleri istekle eşitler: mevcut olanın porsiyonunu günceller, istekte olmayanı siler, yenisini ekler.
     * (Hepsini silip yeniden eklemek aynı birleşik anahtarla çakışabilir.)
     */
    private void syncItems(Meal meal, List<MealItemRequest> requested) {
        Set<Integer> ids = new HashSet<>();
        for (MealItemRequest r : requested) {
            if (!ids.add(r.foodId())) {
                throw ApiException.badRequest("Aynı yemek bir öğünde birden fazla kez olamaz: " + r.foodId());
            }
        }
        Map<Integer, Food> found = foods.findAllById(ids).stream()
                .collect(Collectors.toMap(Food::getId, Function.identity()));
        if (found.size() != ids.size()) {
            Set<Integer> missing = new HashSet<>(ids);
            missing.removeAll(found.keySet());
            throw ApiException.badRequest("Bulunamayan yemek id'leri: " + missing);
        }

        meal.getItems().removeIf(item -> !ids.contains(item.getFood().getId()));
        Map<Integer, MealItem> existing = meal.getItems().stream()
                .collect(Collectors.toMap(i -> i.getFood().getId(), Function.identity()));
        for (MealItemRequest r : requested) {
            MealItem item = existing.get(r.foodId());
            if (item != null) {
                item.setPortionGram(r.portionGram());
            } else {
                meal.getItems().add(new MealItem(meal, found.get(r.foodId()), r.portionGram()));
            }
        }
    }
}
