# İçecek Öneri Uygulaması

Günlük yenilen yemeklere göre akşam için kefir, kombucha veya bitki çayı (rezene, melisa, limon otu, papatya, zencefil, nane) öneren tarayıcı uygulaması.

## Geliştirme ortamı
- Windows 10, 7,8 GB RAM, Intel i7-1165G7, NVIDIA MX450 (2 GB). Ağır yerel modeller önerme.

## Teknoloji
- Java 21 + Spring Boot (Spring Web, Spring Data JPA)
- PostgreSQL 16 + pgvector
- Flyway (migration dosyaları: `src/main/resources/db/migration/`)
- Spring AI (embedding + LLM)
- Kalori verisi: dış API (USDA FoodData Central veya Open Food Facts), sonuç `foods.kcal_per_100g` alanına cache'lenir
- Ön yüz: Spring `static` klasöründe HTML/CSS/JS (React yok)

## Mimari kararlar
1. **Karar algoritmada, açıklama LLM'de.** Öneriyi `drink_rules` tablosu ve puanlama algoritması seçer. LLM yemekleri kategoriye bağlar ve gerekçe metnini yazar.
2. **Güvenlik kısıtları kodda.** `safety_notes` içindeki `exclude` kayıtları LLM'e gitmeden önce adaylardan elenir. `caution` kayıtları puan düşürür. Kombucha kafein içerir; akşam saatinde (`evening`) puanı düşer.
3. **Linkler veritabanından gelir, LLM üretmez.** LLM cevabı `{"drink": "...", "reason": "...", "source_ids": [..]}` formatında döner. Backend `source_ids` değerlerini retrieval sonucuyla doğrular, URL'yi `sources` tablosundan çeker.
4. **Doğrulama.** LLM listede olmayan içecek ya da retrieval'da gelmeyen bir `source_id` döndürürse cevap reddedilir ve kural tabanlı öneriye dönülür. Benzerlik skoru eşiğin altındaysa da kural tabanlı öneri kullanılır.
5. **`drink_rules` kaynak iddiası değildir.** Yemek ve içecek eşleştirmeleri uygulamanın kendi kuralıdır. RAG parçalarına (`knowledge_chunks`) yalnızca kaynakta yazan iddialar girer.
6. **Kanıt düzeyi ayrımı.** Geleneksel kullanım, klinik kanıt gibi sunulmaz. Tıbbi fayda kesin sonuç olarak yazılmaz. Arayüzde "sağlık tavsiyesi değildir" notu bulunur.

## Veritabanı (V1 + V2 hazır, test edildi)
- `drinks` (8 kayıt; nane `active = FALSE`, kaynağı eksik)
- `sources` (20 kayıt; `url_verified = FALSE` olanlar elle kontrol edilecek)
- `food_categories` (6), `foods`, `meals`, `meal_items`
- `drink_rules` (primary = 10, alternative = 5, avoid_if_condition = -100)
- `safety_notes` (severity: info / caution / exclude)
- `knowledge_chunks` (21 parça, `embedding vector(1536)` şu an boş)
- `recommendations` (öneri geçmişi ve kullanıcı geri bildirimi)

Embedding: **Ollama `paraphrase-multilingual`** (768 boyut, çok dilli, ~560 MB). Seçim nedeni: bilgisayar 8 GB RAM / 2 GB GPU, parçalar Türkçe, proje amacı API kullanımı (kalite ikincil). V4 ile sütun `vector(768)` oldu, `embedding_model` sütunu eklendi. Model değişirse yeni migration + `POST /admin/embed?force=true`. LLM (chat) yerelde çalıştırılmayacak (8 GB RAM yetmez); RAG adımında bulut sağlayıcı seçilecek, `spring.ai.model.chat` şu an `none`.

## Çalıştırma
- `docker compose up -d` → pgvector veritabanı **5433** portunda (5432'yi bu bilgisayardaki yerel PostgreSQL 16 servisi kullanıyor; onda pgvector yok, kullanma).
- `.\mvnw.cmd spring-boot:run` → http://localhost:8080 (Maven kurulu değil, her zaman wrapper kullan).
- `.\mvnw.cmd test` → Testcontainers ile çalışır, Docker açık olmalı. Docker Engine 29+ için `testcontainers.version` 1.21.4'e sabitlendi. Entegrasyon testleri `IntegrationTest`'ten türer; USDA orada mock'lanır, testler gerçek API'yi çağırmaz.
- Gizli anahtarlar proje kökündeki `.env` dosyasında: `USDA_API_KEY`, `GEMINI_API_KEY`. `USDA_API_KEY` yoksa `DEMO_KEY` (saatte ~10 istek). `.env` içeriğini asla ekrana basma; yalnızca anahtar adı + değer uzunluğu kontrol et (PowerShell regex'leri Türkçe kültürde büyük/küçük harf eşleşmesinde yanıltıcı).
- `target/` altındaki dosyaları elle düzenleme; migration'lar `src/main/resources/db/migration/` altında. Uygulanmış bir migration'ı (yorum dahil) değiştirmek Flyway checksum hatası verir; değişiklik için yeni `V<n>__...sql` ekle.

## Puanlama kuralları (uygulamanın kendi kuralı, kaynak iddiası değil)
- Kategori ağırlığı: akşam yemeği kalemi 2, diğer öğünler 1. Puan += `base_score` × ağırlık.
- `avoid_if_condition` yalnızca durum aktifse uygulanır. Pasif içecekler (`active = FALSE`) aday olmaz.
- Aktif durumla eşleşen `exclude` → elenir (gerekçe + kaynakla `excluded` listesinde). Her `caution` → puan × 0,5. `info` → gösterilir.
- `evening` kullanıcı durumu değil; `evening` parametresi (varsayılan: sunucu saati ≥ 17:00).
- Sıralama: puan ↓, uyarı sayısı ↑, içecek id ↑. 1 öneri + 2 alternatif.

## RAG akışı
- `GET /api/recommendation` önce kural motorunu çalıştırır; içecek oradan gelir. Ardından seçilen içeceğin parçaları (`drink_id` filtresi + aynı `embedding_model`) arasında top-3 retrieval, sonra Gemini (`spring.ai.model.chat: google-genai`, temperature 0.2, JSON).
- Sorgu metni: `"<içecek adı>: <kategoriler> yemeklerinden sonra sindirim, şişkinlik ve rahatlama için kullanımı"` (parçalar `"<içecek adı>: <içerik>"` olarak embed edildiği için).
- Doğrulama: `drink` = seçilen slug; `source_ids` boş değil ve retrieval'daki kaynaklardan; gerekçe boş/1200+ karakter/link içeriyorsa red. Red, hata, eşik altı, anahtar yok → `generatedBy: "rules"` + `fallbackReason`. `?llm=false` LLM'i atlar.
- `GEMINI_API_KEY` yoksa `api-key` = `not-configured` yer tutucusu (Google istemcisi anahtarsız başlamıyor); servis bunu görünce Gemini'yi çağırmaz.
- Eşik 0,4: gerçek sorgularla en iyi parça benzerlikleri 0,445 (rezene) – 0,665 (papatya). Eşik değişecekse önce bu ölçümü tekrarla.
- Model: `gemini-3.5-flash-lite` (`GEMINI_MODEL` ile değişir). 17.11.2026 denemesi: `gemini-2.5-flash` listede olsa da 404, `gemini-3.5/3.8-flash` 503/zaman aşımı; `*-flash-lite` modeller çalışıyor.
- Canlı ölçüm: öneri isteği 6–13 sn (Ollama model yükleme + Gemini). Ön yüzde yükleniyor göstergesi gerekli.
- Gözlem: Gemini bazen kaynak adını metne yazıyor ("NCCIH ... bildirmektedir") — kural 8'e aykırı ama zararsız; doğrulamada reddedilmiyor.

## Belge ingestion (V5, `POST /admin/ingest`)
- Amaç: "bu RAG değil" eleştirisine karşı gerçek ETL hattı. Paket: `com.tearag.ingestion`.
- Fetch (`HttpSourceFetcher`): HTTP; PubMed sayfaları bot kontrolü gösterdiği için NCBI E-utilities efetch (XML). 16 (NIH ODS) 403, 18 (Health Canada) zaman aşımı → elle yazılmış parçaları kalır.
- Extract (`TextExtractor`): PDF → PDFBox sayfa sayfa; HTML → jsoup, ana içerik (main/article) p/li/başlık; PubMed XML → başlık + etiketli özet.
- Clean (`TextCleaner`): NFKC, e-posta/URL, EMA başlık/altbilgi/sayfa no; "References" başlığından sonrası ve kaynakça satırları (sayfa aralığı + yıl + yazar/doi) atılır.
- Chunk (`TextChunker`): cümle sınırlı ~150 kelime, 30 kelime örtüşme, 25 kelimeden kısa parça yok (model 128 token ile eğitilmiş).
- `source_drinks` kaynak→içecek eşleştirmesi; çoklu içecekli kaynak (17, 18) her içecek için ayrı satır. `origin` = curated | ingested; ingested parçada `claim_type` NULL ("belge alıntısı").
- Yeniden ingest: kaynağın eski ingested parçaları silinir. İndirme hatasında eski parçalar korunur; indirilip metin çıkmazsa silinir.
- Embedding öneki artık `"<name_tr> (<name_en>): <içerik>"` (parçalar İngilizce); retrieval sorgusu da aynı önekle. Önek değişirse `POST /admin/embed?force=true`.
- İndirilen belgeler repoya/diske yazılmaz (telif); yalnızca parçalar DB'de.
- 17.11.2026 canlı çalıştırma: 20 kaynaktan 14'ü parça üretti, toplam 267 ingested parça (+21 curated); ingest ~90 sn, tam embedding ~30 sn. EMA ürün özet sayfaları (2, 3, 10, 12) yalnızca başlık içeriyor → "boş". En iyi benzerlikler 0,46–0,69 (eşik 0,4 yeterli). Kalan gürültü: EMA ürün/doz tablosu parçaları (melisa) üst sıralara çıkabiliyor; kefir/kombucha için ISAPP genel fermente gıda metni (kimchi vb.) geliyor.
- top-k 4. Gemini talimatına "parçalar İngilizce olabilir, Türkçe yaz; ilaç formu/dozaj tablosunu kullanma" eklendi.

## Embedding gözlemleri (paraphrase-multilingual, 17.11.2026)
- Kosinüs benzerliği tipik olarak 0,3–0,6 arası. İyi eşleşmeler ~0,5+ ("şişkinlik ve gaz" → zencefil/melisa geleneksel kullanım 0,53; "hamilelikte güvenli mi" → kombucha güvenlik 0,59).
- Zayıf: teknik terimleri günlük dile bağlayamıyor ("böbrek sağlığı" → limon otu "kreatinin klirensi" parçası yalnızca 3. sırada, 0,38). Bu yüzden retrieval her zaman `drink_id` ile filtrelenmeli; eşik RAG adımında ~0,4 civarından başlayıp ayarlanmalı.

## Kalori (USDA) notları
- GET `/v1/foods/search` + `dataType` filtresi bazı sorgularda 400 döndürüyor; bu yüzden POST + JSON gövde kullanılıyor.
- USDA alaka sıralaması her zaman en iyi eşleşmeyi ilk vermiyor (ör. "cheese pizza" → "Topping from cheese pizza"). Eşleşen kayıt `foods.api_food_description` alanında saklanır ve API cevabında `usdaDescription` olarak gösterilir; yanlışsa daha belirgin isim veya elle `kcalPer100g` girilir.
- Enerji besin id önceliği: 1008 (Energy, KCAL) → 2048 → 2047 (Atwater; Foundation kayıtları).

## Yol haritası
- [x] Şema ve seed verisi (V1, V2)
- [x] Spring Boot iskeleti, Postgres bağlantısı, Flyway (Boot 3.5.7, Maven Wrapper, `FlywayMigrationTest` geçiyor)
- [x] Yemek ve öğün CRUD: `/api/foods` (`?q=`, `?categoryId=`), `/api/meals` (`?date=`), `/api/food-categories`; hatalar ProblemDetail (404/400/409)
- [x] `NutritionService`: USDA FoodData Central (`RestClient`, POST `/v1/foods/search`) + `foods` tablosunda cache; `POST /api/foods/{id}/kcal/refresh`. Yemek isimleri İngilizce girilir.
- [x] Ingestion: `POST /admin/embed` ile `knowledge_chunks.embedding` alanını doldur (Spring AI 1.1.8, `EmbeddingIngestionService`; 21 parça ilk yüklemede ~50 sn)
- [x] Kural tabanlı öneri: `GET /api/recommendation?date=&conditions=reflux,pregnancy&evening=` (`RuleBasedRecommender`); geçerli durum kodları `GET /api/recommendation/conditions`. Durumlar istek bazlı (profil tablosu yok). `recommendations` tablosuna henüz yazılmıyor (GET yan etkisiz).
- [x] Gerçek belge ingestion: fetch → extract → clean → chunk → embed (`POST /admin/ingest`, V5)
- [x] RAG katmanı: retrieval (pgvector, cosine) + LLM gerekçe + kaynak doğrulama (`RagRecommendationService`, Gemini `gemini-3.5-flash-lite` / Spring AI google-genai)
- [x] Serbest metinle öğün girişi: `POST /api/meals/text` {mealType, text} → Gemini kalemlere ayırır (name_en, name_tr, category slug|null, portion_gram) → doğrulama (bilinmeyen kategori null, porsiyon 1–2000 g, boşsa 150 g, en fazla 10 kalem, aynı ad birleşir) → `FoodService.findOrCreate` + `MealService.addItems` tek işlemde. LLM çağrısı DB işleminin dışında. Gemini yoksa 503 ve hiçbir şey kaydedilmez. Kullanıcı Türkçe yazabilir.
- [x] Ön yüz (`static/index.html`, `app.js`, `style.css`): öğün + "Ne yedin?" metin kutusu (+ bugünün listesi) ve öneri kartları (1 en uygun + 2 alternatif, gerekçe ve kaynak linkleriyle). Günlük özet bölümü kullanıcı isteğiyle yapılmadı (ticari değil). Tasarım bilerek sade: gölge/süs yok. Metinler her zaman `textContent` ile basılır.
- [ ] JUnit testleri, Docker Compose (Postgres + pgvector)
- [ ] İsteğe bağlı: hava durumu API'si (Open-Meteo) ile sıcak/soğuk içecek kuralı

## Açık işler
- Kategori boşlukları: Gemini'nin dürüstçe "kategori yok" dediği yaygın yemekler var (mercimek çorbası, kızartma/fast food, çay). Yeni kategori = yeni `food_categories` + `drink_rules` satırları (V5 migration); eşleştirmeler kullanıcının kararı, uydurma.
- `url_verified = FALSE` olan 13 kaynağı tarayıcıda kontrol et.
- Nane çayı için doğrudan kaynak bul; bulunca `active = TRUE` yap ve RAG parçası ekle.
- Kefir laktoz ve kombucha asitlik notlarının `source_id` alanı boş; kaynak eklenebilir.
- Melisa EMA monografı revizyon sürecinde; güncel sürümü kontrol et.
