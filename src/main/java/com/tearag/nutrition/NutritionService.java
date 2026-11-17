package com.tearag.nutrition;

import com.tearag.food.Food;
import com.tearag.nutrition.CalorieLookup.CalorieLookupException;
import com.tearag.nutrition.CalorieLookup.Match;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Yemeklerin kalorisini dış API'den alıp foods tablosuna yazar (foods.kcal_per_100g = cache).
 * Bir kez bulunan değer tekrar sorgulanmaz; yalnızca isim değişince veya elle yenilenince.
 */
@Service
public class NutritionService {

    private static final Logger log = LoggerFactory.getLogger(NutritionService.class);

    private final CalorieLookup lookup;

    public NutritionService(CalorieLookup lookup) {
        this.lookup = lookup;
    }

    /**
     * Yemek kaydı sırasında çağrılır: API'ye ulaşılamazsa kayıt yine yapılır, kalori boş kalır.
     * Eşleşme yoksa eski API değerleri temizlenir (isim değişmiş olabilir).
     */
    public void fillBestEffort(Food food) {
        try {
            apply(food, lookup.findByName(food.getName()));
        } catch (CalorieLookupException e) {
            log.warn("Kalori alınamadı, yemek kalorisiz kaydediliyor: {}", food.getName());
            apply(food, Optional.empty());
        }
    }

    /**
     * Elle yenileme: hata ve "eşleşme yok" durumları çağırana iletilir, mevcut değer korunur.
     *
     * @return eşleşme bulunduysa true
     * @throws CalorieLookupException API'ye ulaşılamazsa
     */
    public boolean refresh(Food food) {
        Optional<Match> match = lookup.findByName(food.getName());
        match.ifPresent(m -> apply(food, match));
        return match.isPresent();
    }

    /** Kullanıcının elle girdiği kalori: API bilgisi silinir. */
    public void setManual(Food food, BigDecimal kcalPer100g) {
        food.setKcalPer100g(kcalPer100g);
        food.setApiFoodId(null);
        food.setApiFoodDescription(null);
        food.setKcalFetchedAt(null);
    }

    private static void apply(Food food, Optional<Match> match) {
        food.setKcalPer100g(match.map(Match::kcalPer100g).orElse(null));
        food.setApiFoodId(match.map(Match::apiFoodId).orElse(null));
        food.setApiFoodDescription(match.map(Match::description).orElse(null));
        food.setKcalFetchedAt(match.isPresent() ? OffsetDateTime.now() : null);
    }
}
