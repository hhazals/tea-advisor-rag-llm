-- V3__food_api_description.sql
-- USDA'da eşleşen kaydın açıklaması (ör. "Pizza, cheese, thin crust").
-- Kullanıcı yanlış eşleşmeyi görebilsin diye api_food_id ile birlikte saklanır.
ALTER TABLE foods ADD COLUMN api_food_description TEXT;
