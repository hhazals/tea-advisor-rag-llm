package com.tearag.meal;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** meals.meal_type CHECK kısıtındaki değerler; JSON'da ve DB'de küçük harfle tutulur. */
public enum MealType {
    @JsonProperty("breakfast") BREAKFAST,
    @JsonProperty("lunch") LUNCH,
    @JsonProperty("dinner") DINNER,
    @JsonProperty("snack") SNACK;

    @Converter(autoApply = true)
    public static class DbConverter implements AttributeConverter<MealType, String> {

        @Override
        public String convertToDatabaseColumn(MealType type) {
            return type == null ? null : type.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public MealType convertToEntityAttribute(String value) {
            return value == null ? null : MealType.valueOf(value.toUpperCase(Locale.ROOT));
        }
    }
}
