package com.tearag.food;

import com.tearag.common.ApiException;
import com.tearag.food.FoodDtos.CategoryResponse;
import com.tearag.food.FoodDtos.FoodRequest;
import com.tearag.food.FoodDtos.FoodResponse;
import com.tearag.meal.MealItemRepository;
import com.tearag.nutrition.CalorieLookup.CalorieLookupException;
import com.tearag.nutrition.NutritionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FoodService {

    private final FoodRepository foods;
    private final FoodCategoryRepository categories;
    private final MealItemRepository mealItems;
    private final NutritionService nutrition;

    public FoodService(FoodRepository foods, FoodCategoryRepository categories, MealItemRepository mealItems,
                       NutritionService nutrition) {
        this.foods = foods;
        this.categories = categories;
        this.mealItems = mealItems;
        this.nutrition = nutrition;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categories.findAll(Sort.by("id")).stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<FoodResponse> list(String q, Integer categoryId) {
        List<Specification<Food>> specs = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            String pattern = "%" + escapeLike(q.strip().toLowerCase(Locale.ROOT)) + "%";
            specs.add((root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, '\\'));
        }
        if (categoryId != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }
        return foods.findAll(Specification.allOf(specs), Sort.by("name"))
                .stream().map(FoodResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public FoodResponse get(Integer id) {
        return FoodResponse.from(find(id));
    }

    /** kcalPer100g verilmezse kalori USDA'dan alınır (ulaşılamazsa boş kalır). */
    public FoodResponse create(FoodRequest req) {
        String name = req.name().strip();
        if (foods.existsByName(name)) {
            throw ApiException.conflict("Bu isimde bir yemek zaten var: " + name);
        }
        Food food = new Food();
        apply(food, name, req);
        if (req.kcalPer100g() != null) {
            nutrition.setManual(food, req.kcalPer100g());
        } else {
            nutrition.fillBestEffort(food);
        }
        return FoodResponse.from(foods.save(food));
    }

    /**
     * kcalPer100g verilirse elle girilmiş sayılır. Verilmezse mevcut kalori korunur;
     * yalnızca isim değiştiyse USDA'dan yeniden alınır.
     */
    public FoodResponse update(Integer id, FoodRequest req) {
        Food food = find(id);
        String name = req.name().strip();
        if (foods.existsByNameAndIdNot(name, id)) {
            throw ApiException.conflict("Bu isimde bir yemek zaten var: " + name);
        }
        boolean renamed = !name.equals(food.getName());
        apply(food, name, req);
        if (req.kcalPer100g() != null) {
            nutrition.setManual(food, req.kcalPer100g());
        } else if (renamed) {
            nutrition.fillBestEffort(food);
        }
        return FoodResponse.from(food);
    }

    /**
     * Aynı isimde (büyük/küçük harf duyarsız) yemek varsa onu döndürür; kategorisi boşsa verileni atar.
     * Yoksa oluşturur (kalori USDA'dan). Serbest metin girişinde kullanılır.
     */
    public FoodResponse findOrCreate(String name, Integer categoryId) {
        String clean = name.strip();
        return foods.findFirstByNameIgnoreCase(clean)
                .map(existing -> {
                    if (existing.getCategory() == null && categoryId != null) {
                        existing.setCategory(categories.findById(categoryId).orElse(null));
                    }
                    return FoodResponse.from(existing);
                })
                .orElseGet(() -> create(new FoodRequest(clean, categoryId, null)));
    }

    /** Kaloriyi USDA'dan yeniden çeker; elle girilmiş değerin üzerine yazar. */
    public FoodResponse refreshKcal(Integer id) {
        Food food = find(id);
        boolean found;
        try {
            found = nutrition.refresh(food);
        } catch (CalorieLookupException e) {
            throw ApiException.serviceUnavailable("Kalori servisine şu an ulaşılamıyor, daha sonra tekrar deneyin.");
        }
        if (!found) {
            throw ApiException.unprocessable("USDA'da '" + food.getName()
                    + "' için eşleşme bulunamadı. İngilizce ve daha genel bir isim deneyin veya kaloriyi elle girin.");
        }
        return FoodResponse.from(food);
    }

    public void delete(Integer id) {
        Food food = find(id);
        if (mealItems.existsByFood_Id(id)) {
            throw ApiException.conflict("Bu yemek kayıtlı öğünlerde kullanılıyor; önce öğünlerden çıkarın.");
        }
        foods.delete(food);
    }

    Food find(Integer id) {
        return foods.findById(id).orElseThrow(() -> ApiException.notFound("Yemek bulunamadı: " + id));
    }

    private void apply(Food food, String name, FoodRequest req) {
        food.setName(name);
        food.setCategory(req.categoryId() == null ? null : categories.findById(req.categoryId())
                .orElseThrow(() -> ApiException.badRequest("Geçersiz kategori: " + req.categoryId())));
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
