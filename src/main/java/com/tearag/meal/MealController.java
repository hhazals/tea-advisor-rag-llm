package com.tearag.meal;

import com.tearag.meal.MealDtos.MealRequest;
import com.tearag.meal.MealDtos.MealResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meals")
public class MealController {

    private final MealService service;
    private final MealTextService textService;

    public MealController(MealService service, MealTextService textService) {
        this.service = service;
        this.textService = textService;
    }

    /**
     * Serbest metinden öğün: {"mealType": "lunch", "text": "big mac, 6 nugget ve kola zero"}.
     * 503: Gemini'ye ulaşılamadı (hiçbir şey kaydedilmez), 422: metinde yemek yok.
     */
    @PostMapping("/text")
    public MealTextService.MealTextResponse addFromText(@Valid @RequestBody MealTextService.MealTextRequest req) {
        return textService.addFromText(req);
    }

    /** ?date=YYYY-MM-DD; verilmezse bugün. */
    @GetMapping
    public List<MealResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.listByDate(date != null ? date : LocalDate.now());
    }

    @GetMapping("/{id}")
    public MealResponse get(@PathVariable Integer id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<MealResponse> create(@Valid @RequestBody MealRequest req) {
        MealResponse created = service.create(req);
        return ResponseEntity.created(URI.create("/api/meals/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public MealResponse update(@PathVariable Integer id, @Valid @RequestBody MealRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
