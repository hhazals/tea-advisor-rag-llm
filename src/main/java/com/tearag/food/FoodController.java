package com.tearag.food;

import com.tearag.food.FoodDtos.CategoryResponse;
import com.tearag.food.FoodDtos.FoodRequest;
import com.tearag.food.FoodDtos.FoodResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FoodController {

    private final FoodService service;

    public FoodController(FoodService service) {
        this.service = service;
    }

    @GetMapping("/api/food-categories")
    public List<CategoryResponse> categories() {
        return service.listCategories();
    }

    @GetMapping("/api/foods")
    public List<FoodResponse> list(@RequestParam(required = false) String q,
                                   @RequestParam(required = false) Integer categoryId) {
        return service.list(q, categoryId);
    }

    @GetMapping("/api/foods/{id}")
    public FoodResponse get(@PathVariable Integer id) {
        return service.get(id);
    }

    @PostMapping("/api/foods")
    public ResponseEntity<FoodResponse> create(@Valid @RequestBody FoodRequest req) {
        FoodResponse created = service.create(req);
        return ResponseEntity.created(URI.create("/api/foods/" + created.id())).body(created);
    }

    @PutMapping("/api/foods/{id}")
    public FoodResponse update(@PathVariable Integer id, @Valid @RequestBody FoodRequest req) {
        return service.update(id, req);
    }

    /** Kaloriyi USDA'dan yeniden çeker. 422: eşleşme yok, 503: servise ulaşılamadı. */
    @PostMapping("/api/foods/{id}/kcal/refresh")
    public FoodResponse refreshKcal(@PathVariable Integer id) {
        return service.refreshKcal(id);
    }

    @DeleteMapping("/api/foods/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
