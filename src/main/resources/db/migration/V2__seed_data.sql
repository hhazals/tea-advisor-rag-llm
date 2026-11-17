-- V2__seed_data.sql
-- Kaynak dosyalarından üretilen başlangıç verisi.
-- url_verified = TRUE olanlar 17.11.2026 itibarıyla doğrulandı; FALSE olanları tarayıcıda bir kez aç.
-- knowledge_chunks.embedding burada boş; Java ingestion adımı (POST /admin/embed) dolduracak.

-- 1) İçecekler -------------------------------------------------------------
INSERT INTO drinks (id, slug, name_tr, name_en, scientific_name, category, plant_part,
                    contains_caffeine, contains_lactose, evidence_level, evidence_note, active) VALUES
(1, 'fennel',     'Rezene',    'Fennel',           'Foeniculum vulgare Mill.', 'herbal_tea', 'Meyve (fructus)',
    FALSE, FALSE, 'Geleneksel kullanım (EMA)',
    'EMA geleneksel kullanımı tanır. Bu, her öğünden sonra hazımsızlığı önlediğine dair güçlü klinik kanıt değildir.', TRUE),
(2, 'melissa',    'Melisa',    'Lemon balm',       'Melissa officinalis L.', 'herbal_tea', 'Yaprak (folium)',
    FALSE, FALSE, 'Geleneksel kullanım (EMA)',
    'Sindirim şikayetlerinde iyi belgelenmiş klinik etkinlik gösterilmemiştir.', TRUE),
(3, 'lemongrass', 'Limon otu', 'Lemongrass',       'Cymbopogon citratus', 'herbal_tea', 'Yaprak',
    FALSE, FALSE, 'Sınırlı insan çalışması',
    'Sindirim faydaları ağırlıkla geleneksel kullanım ve preklinik çalışmalara dayanır. EMA monografı yoktur.', TRUE),
(4, 'chamomile',  'Papatya',   'German chamomile', 'Matricaria recutita L.', 'herbal_tea', 'Çiçek (flos)',
    FALSE, FALSE, 'Geleneksel kullanım (EMA)',
    'NCCIH belirli sindirim sorunlarında etkinliği gösterecek güvenilir klinik kanıtın yetersiz olduğunu belirtir.', TRUE),
(5, 'ginger',     'Zencefil',  'Ginger',           'Zingiber officinale Roscoe', 'herbal_tea', 'Kök sap (rhizoma)',
    FALSE, FALSE, 'Sindirim için geleneksel kullanım; yol tutması için belirli preparatlarda iyi belgelenmiş kullanım',
    'İlaç preparatlarından elde edilen bulgular sıradan zencefil çayına otomatik olarak genellenmemelidir.', TRUE),
(6, 'kefir',      'Kefir',     'Kefir',            NULL, 'fermented_beverage', NULL,
    FALSE, TRUE,  'İnsan klinik çalışmaları var; sınırlı ve heterojen',
    'Mikrobiyal içerik, hazırlama, doz ve çalışma tasarımı farkları sonuçları sınırlar. Kefir otomatik olarak klinik olarak doğrulanmış bir probiyotik değildir.', TRUE),
(7, 'kombucha',   'Kombucha',  'Kombucha',         NULL, 'fermented_beverage', NULL,
    TRUE,  FALSE, 'Sınırlı insan klinik çalışması',
    'Sindirim bozukluklarında tedavi olarak kullanımını destekleyecek kanıt yetersizdir.', TRUE),
(8, 'peppermint', 'Nane',      'Peppermint',       'Mentha x piperita L.', 'herbal_tea', 'Yaprak',
    FALSE, FALSE, 'Kaynak eksik',
    'Nane çayı için doğrudan kaynak eklenene kadar öneri motorunda pasif.', FALSE);

SELECT setval('drinks_id_seq', (SELECT MAX(id) FROM drinks));

-- 2) Kaynaklar -------------------------------------------------------------
INSERT INTO sources (id, title, url, authority, source_type, reference_number,
                     published_date, funding_note, url_verified, accessed_date) VALUES
(1,  'EMA - Final EU Herbal Monograph: Bitter Fennel Fruit (Revision 1)',
     'https://www.ema.europa.eu/en/documents/herbal-monograph/final-european-union-herbal-monograph-foeniculum-vulgare-miller-subsp-vulgare-var-vulgare-fructus-revision-1_en.pdf',
     'European Medicines Agency (HMPC)', 'regulatory_monograph', 'EMA/HMPC/372841/2016', '2024-01-31', NULL, TRUE, '2026-11-17'),
(2,  'EMA - Bitter Fennel Herbal Medicinal Product',
     'https://www.ema.europa.eu/en/medicines/herbal/foeniculi-amari-fructus',
     'European Medicines Agency (HMPC)', 'regulatory_summary', NULL, NULL, NULL, FALSE, NULL),
(3,  'EMA - Melissa Leaf Herbal Medicinal Product',
     'https://www.ema.europa.eu/en/medicines/herbal/melissae-folium',
     'European Medicines Agency (HMPC)', 'regulatory_summary', NULL, NULL, NULL, TRUE, '2026-11-17'),
(4,  'EMA - Community Herbal Monograph on Melissa officinalis L., folium',
     'https://www.ema.europa.eu/en/documents/herbal-monograph/final-community-herbal-monograph-melissa-officinalis-l-folium_en.pdf',
     'European Medicines Agency (HMPC)', 'regulatory_monograph', 'EMA/HMPC/196745/2012', '2013-05-14', NULL, FALSE, NULL),
(5,  'EMA - Final Assessment Report on Melissa officinalis L., folium',
     'https://www.ema.europa.eu/en/documents/herbal-report/final-assessment-report-melissa-officinalis-l-folium_en.pdf',
     'European Medicines Agency (HMPC)', 'regulatory_monograph', NULL, NULL, NULL, FALSE, NULL),
(6,  'Memorial Sloan Kettering - Lemongrass',
     'https://www.mskcc.org/cancer-care/integrative-medicine/herbs/lemongrass',
     'Memorial Sloan Kettering Cancer Center', 'evidence_database', NULL, NULL, NULL, FALSE, NULL),
(7,  'Effects of lemongrass (Cymbopogon citratus) tea on renal function indices in humans',
     'https://pubmed.ncbi.nlm.nih.gov/25440110/',
     'PubMed', 'clinical_study', 'PMID 25440110', NULL, NULL, TRUE, '2026-11-17'),
(8,  'Review of Cymbopogon Phytomedicine and Pharmacology',
     'https://pmc.ncbi.nlm.nih.gov/articles/PMC9465289/',
     'PubMed Central', 'narrative_review', 'PMC9465289', NULL, NULL, FALSE, NULL),
(9,  'EMA - Final EU Herbal Monograph: Matricaria recutita L., flos',
     'https://www.ema.europa.eu/en/documents/herbal-monograph/final-european-union-herbal-monograph-matricaria-recutita-l-flos-first-version_en.pdf',
     'European Medicines Agency (HMPC)', 'regulatory_monograph', 'EMA/HMPC/55843/2011', NULL, NULL, FALSE, NULL),
(10, 'EMA - Matricariae flos',
     'https://www.ema.europa.eu/en/medicines/herbal/matricariae-flos',
     'European Medicines Agency (HMPC)', 'regulatory_summary', NULL, NULL, NULL, FALSE, NULL),
(11, 'NCCIH - Chamomile: Usefulness and Safety',
     'https://www.nccih.nih.gov/health/chamomile',
     'National Center for Complementary and Integrative Health', 'government_factsheet', NULL, NULL, NULL, FALSE, NULL),
(12, 'EMA - Zingiberis rhizoma (Ginger)',
     'https://www.ema.europa.eu/en/medicines/herbal/zingiberis-rhizoma',
     'European Medicines Agency (HMPC)', 'regulatory_summary', NULL, NULL, NULL, TRUE, '2026-11-17'),
(13, 'EMA - Final EU Herbal Monograph on Zingiber officinale Roscoe, rhizoma (Revision 1)',
     'https://www.ema.europa.eu/en/documents/herbal-monograph/final-european-union-herbal-monograph-zingiber-officinale-roscoe-rhizoma-revision-1_en.pdf',
     'European Medicines Agency (HMPC)', 'regulatory_monograph', 'EMA/HMPC/885789/2022', '2025-05-07', NULL, TRUE, '2026-11-17'),
(14, 'NCCIH - Ginger: Usefulness and Safety',
     'https://www.nccih.nih.gov/health/ginger',
     'National Center for Complementary and Integrative Health', 'government_factsheet', NULL, NULL, NULL, FALSE, NULL),
(15, 'Kairey et al. (2023) - The effects of kefir consumption on human health: a systematic review of randomized controlled trials',
     'https://pubmed.ncbi.nlm.nih.gov/35913411/',
     'Nutrition Reviews', 'systematic_review', 'PMID 35913411', '2023-02-10', NULL, TRUE, '2026-11-17'),
(16, 'NIH Office of Dietary Supplements - Probiotics',
     'https://ods.od.nih.gov/factsheets/Probiotics-HealthProfessional/',
     'National Institutes of Health', 'government_factsheet', NULL, NULL, NULL, FALSE, NULL),
(17, 'ISAPP Consensus Statement on Fermented Foods',
     'https://doi.org/10.1038/s41575-020-00390-5',
     'Nature Reviews Gastroenterology & Hepatology', 'consensus_statement', 'DOI 10.1038/s41575-020-00390-5', NULL, NULL, FALSE, NULL),
(18, 'Health Canada - Ethanol in Non-Alcoholic Fermented Beverages',
     'https://www.canada.ca/en/health-canada/services/publications/food-nutrition/ethanol-non-alcoholic-fermented-beverages.html',
     'Health Canada', 'government_factsheet', NULL, NULL, NULL, FALSE, NULL),
(19, 'Ecklu-Mensah et al. (2024) - Modulating the human gut microbiome and health markers through kombucha consumption: a controlled clinical study',
     'https://pubmed.ncbi.nlm.nih.gov/39738315/',
     'Scientific Reports', 'clinical_study', 'DOI 10.1038/s41598-024-80281-w', '2024-12-30',
     'Çalışma bir kombucha üreticisi tarafından finanse edildi.', FALSE, NULL),
(20, 'NHS - Indigestion',
     'https://www.nhs.uk/conditions/indigestion/',
     'NHS', 'government_factsheet', NULL, NULL, NULL, FALSE, NULL);

SELECT setval('sources_id_seq', (SELECT MAX(id) FROM sources));

-- 3) Yemek kategorileri ----------------------------------------------------
INSERT INTO food_categories (id, slug, name_tr, description) VALUES
(1, 'pizza_pide',       'Pizza / Pide',                   'Peynir, sucuk ve yağ içeriği yüksek hamur işleri.'),
(2, 'red_meat_bbq',     'Mangal / Kırmızı et',            'Kebap, mangal, yağlı kırmızı et ve yoğun protein içeren öğünler.'),
(3, 'chicken',          'Tavuk yemeği',                   'Fırında, ızgara ve soslu tavuk yemekleri.'),
(4, 'sweets_chocolate', 'Waffle / Çikolatalı tatlılar',   'Çikolata, waffle, yoğun şeker ve yağ içeren tatlılar.'),
(5, 'pasta',            'Makarna',                        'Kremalı, peynirli, domates soslu ve karbonhidrat ağırlıklı makarnalar.'),
(6, 'asian_fish',       'Asya mutfağı / Balık',           'Sushi, wok, teriyaki, baharatlı Asya yemekleri ve balık.');

SELECT setval('food_categories_id_seq', (SELECT MAX(id) FROM food_categories));

-- 4) Kural tablosu (uygulamanın kendi eşleştirmesi, kaynak iddiası değil) ---
-- base_score: primary = 10, alternative = 5
INSERT INTO drink_rules (food_category_id, drink_id, role, base_score, condition_code, rationale_tr) VALUES
-- Pizza / Pide
(1, 1, 'primary',     10, NULL, 'Şişkinlik ve gaz hissi için geleneksel kullanım.'),
(1, 4, 'alternative',  5, NULL, 'Hafif ve kafeinsiz alternatif.'),
(1, 6, 'alternative',  5, NULL, 'Fermente süt içeceği alternatifi.'),
(1, 7, 'alternative',  5, NULL, 'Fermente çay alternatifi.'),
-- Mangal / Kırmızı et
(2, 5, 'primary',     10, NULL, 'Mide rahatsızlığı ve şişkinlik için geleneksel kullanım.'),
(2, 1, 'alternative',  5, NULL, 'Şişkinlik ve gaz hissi için geleneksel kullanım.'),
(2, 6, 'alternative',  5, NULL, 'Fermente süt içeceği alternatifi.'),
-- Tavuk
(3, 4, 'primary',     10, NULL, 'Hafif ve kafeinsiz içecek.'),
(3, 1, 'alternative',  5, NULL, 'Şişkinlik ve gaz hissi için geleneksel kullanım.'),
(3, 6, 'alternative',  5, NULL, 'Fermente süt içeceği alternatifi.'),
(3, 7, 'alternative',  5, NULL, 'Fermente çay alternatifi.'),
-- Waffle / Çikolata
(4, 2, 'primary',     10, NULL, 'Kafeinsiz, hafif aromalı içecek.'),
(4, 4, 'alternative',  5, NULL, 'Kafeinsiz ve hafif alternatif.'),
(4, 8, 'avoid_if_condition', -100, 'reflux', 'Reflüsü olanlarda mide yanmasını artırabilir.'),
-- Makarna
(5, 1, 'primary',     10, NULL, 'Şişkinlik ve gaz hissi için geleneksel kullanım.'),
(5, 4, 'alternative',  5, NULL, 'Hafif ve kafeinsiz alternatif.'),
(5, 6, 'alternative',  5, NULL, 'Fermente süt içeceği alternatifi.'),
-- Asya / Balık
(6, 5, 'primary',     10, NULL, 'Mide rahatsızlığı hissi için geleneksel kullanım.'),
(6, 3, 'alternative',  5, NULL, 'Hafif ve aromatik alternatif.'),
(6, 4, 'alternative',  5, NULL, 'Kafeinsiz ve hafif alternatif.'),
(6, 7, 'alternative',  5, NULL, 'Fermente çay alternatifi.');

-- 5) Güvenlik notları ------------------------------------------------------
INSERT INTO safety_notes (drink_id, condition_code, severity, note_tr, source_id) VALUES
(1, 'under_12',           'caution', 'EMA monografı estragol içeriği nedeniyle 12 yaş altı çocuklar için ayrı uyarılar içerir.', 1),
(2, 'under_12',           'exclude', 'Yalnızca yetişkinler ve 12 yaş üstü çocuklar için.', 3),
(2, NULL,                 'info',    'Belirtiler 2 hafta sonra sürüyorsa doktora danışılmalı.', 3),
(3, 'kidney',             'caution', 'Yüksek doz veya uzun süreli tüketimde böbrek fonksiyon göstergelerinde doza ve süreye bağlı olumsuz etkiler gözlendi.', 7),
(3, 'pregnancy',          'caution', 'Hamilelikte dikkatli olunmalı.', 6),
(3, NULL,                 'info',    'Limon otu, melisa veya sitronella ile karıştırılmamalı.', 6),
(4, 'asteraceae_allergy', 'exclude', 'Papatyagillere alerjisi olanlarda alerjik reaksiyon görülebilir.', 11),
(4, 'anticoagulant',      'caution', 'Varfarin ve bazı ilaçlarla etkileşim ihtimali vardır.', 11),
(4, 'pregnancy',          'info',    'Hamilelik ve emzirme dönemi için güvenlik verisi sınırlıdır.', 11),
(5, 'under_18',           'caution', 'EMA, ilgili endikasyonlarda 18 yaş altında tıbbi kullanımı önermez.', 12),
(5, 'pregnancy',          'caution', 'EMA, belirtilen endikasyonlarda hamilelik ve emzirme döneminde tıbbi kullanımı önermez.', 13),
(5, NULL,                 'info',    'Mide rahatsızlığına neden olabilir.', 14),
(6, 'lactose',            'caution', 'Laktoza hassas kişilerde rahatsızlık yapabilir.', NULL),
(6, NULL,                 'info',    'Fermente kefir az miktarda etanol içerebilir.', 18),
(7, 'reflux',             'caution', 'Asitliği reflü veya mide hassasiyetini artırabilir.', NULL),
(7, 'pregnancy',          'exclude', 'Alkol içeriği ve mikrobiyolojik güvenlik belirsiz olabileceği için hamilelikte önerilmez.', 18),
(7, 'evening',            'caution', 'Çaydan üretildiği için kafein içerir; akşam saatinde puan düşür.', NULL),
(7, NULL,                 'info',    'Evde fermente edilen ürünlerde hatalı hazırlama veya saklamada kontaminasyon riski vardır.', NULL),
(8, 'reflux',             'exclude', 'Nane bazı kişilerde reflü belirtilerini artırabilir.', 20);

-- 6) RAG parçaları (her biri tek bir kaynağa bağlı) ------------------------
INSERT INTO knowledge_chunks (drink_id, source_id, claim_type, content) VALUES
-- Rezene
(1, 1, 'traditional_use',
 'EMA HMPC, rezene meyvesi (Foeniculum vulgare, fructus) için geleneksel kullanımı tanır: hafif spazmodik sindirim şikayetleri, şişkinlik ve gaz. Bu değerlendirme uzun süreli kullanıma dayanır, klinik çalışma sonuçlarına değil.'),
(1, 1, 'limitation',
 'Rezenenin geleneksel kullanımının tanınması, her öğünden sonra hazımsızlığı önlediğine dair güçlü klinik kanıt olduğu anlamına gelmez.'),
(1, 1, 'safety',
 'EMA rezene monografı, estragol içeriği nedeniyle 12 yaş altı çocuklar için yaş grubuna göre ayrı uyarılar içerir.'),
-- Melisa
(2, 3, 'traditional_use',
 'EMA HMPC, uzun süreli kullanıma dayanarak melisa yaprağının hafif stres belirtilerinin giderilmesinde, uykuya yardımcı olarak ve şişkinlik ile gaz dahil hafif sindirim şikayetlerinde kullanılabileceği sonucuna varmıştır.'),
(2, 5, 'limitation',
 'EMA değerlendirmesi melisa için geleneksel kullanıma dayanır. Sindirim bozukluklarında iyi belgelenmiş klinik etkinlik gösterilmemiştir.'),
(2, 3, 'safety',
 'Melisa yaprağı yalnızca yetişkinlerde ve 12 yaş üstü çocuklarda kullanılmalıdır. Belirtiler 2 haftalık kullanımdan sonra sürerse doktora danışılmalıdır.'),
-- Limon otu
(3, 6, 'traditional_use',
 'Limon otu (Cymbopogon citratus) sindirim şikayetleri ve rahatlama için geleneksel olarak kullanılır. Bu bitki için EMA monografı bulunmamaktadır.'),
(3, 8, 'limitation',
 'Limon otunun sindirim faydaları ağırlıkla geleneksel kullanım ve preklinik çalışmalarla desteklenir. Sindirim etkinliğini gösterecek insan kanıtı yetersizdir.'),
(3, 7, 'safety',
 'İnsanlarda yapılan bir çalışmada limon otu infüzyonunun kreatinin klirensi ve tahmini glomerüler filtrasyon hızı üzerinde doza ve süreye bağlı olumsuz etkileri gözlenmiştir.'),
-- Papatya
(4, 9, 'traditional_use',
 'EMA HMPC, papatya çiçeği (Matricaria recutita, flos) için hafif sindirim şikayetleri, şişkinlik ve küçük sindirim spazmlarında geleneksel kullanımı tanır.'),
(4, 11, 'limitation',
 'NCCIH, papatyanın belirli sindirim sorunlarındaki etkinliğini gösterecek güvenilir klinik kanıtın yetersiz olduğunu bildirir.'),
(4, 11, 'safety',
 'Papatya, özellikle papatyagiller (Asteraceae) ailesine alerjisi olanlarda alerjik reaksiyona yol açabilir. Varfarin ve bazı ilaçlarla etkileşim ihtimali vardır.'),
-- Zencefil
(5, 13, 'traditional_use',
 'EMA HMPC, zencefil kök sapı için geleneksel kullanımı tanır: hafif spazmodik sindirim şikayetleri, şişkinlik, gaz, geçici iştah kaybı ve yol tutmasının belirtilerinin hafifletilmesi.'),
(5, 12, 'well_established_use',
 'EMA, belirli bir toz zencefil preparatının yol tutmasına bağlı bulantı ve kusmanın önlenmesinde iyi belgelenmiş kullanımını kabul eder. Bu bulgu sıradan zencefil çayına otomatik olarak genellenmemelidir.'),
(5, 14, 'safety',
 'Zencefil mide rahatsızlığına, bulantıya veya alerjik reaksiyonlara neden olabilir.'),
-- Kefir
(6, 15, 'clinical_evidence',
 'Randomize kontrollü çalışmaları inceleyen bir sistematik derleme, kefir tüketiminin insan sağlığı üzerindeki olası etkilerini raporlar. Mikrobiyal içerik, hazırlama, doz ve çalışma tasarımındaki farklılıklar kesin sonuç çıkarmayı sınırlar.'),
(6, 17, 'limitation',
 'Fermente gıdalar ile probiyotikler aynı şey değildir. Kefir canlı mikroorganizma içerse de otomatik olarak klinik olarak doğrulanmış bir probiyotik ürün sayılmaz.'),
(6, 18, 'safety',
 'Fermente içecekler fermantasyon sırasında az miktarda etanol oluşturabilir.'),
-- Kombucha
(7, 19, 'clinical_evidence',
 'Batı tipi beslenen sağlıklı yetişkinlerle yapılan sekiz haftalık küçük bir kontrollü çalışmada (kombucha grubu 16, kontrol grubu 8 kişi) bağırsak mikrobiyotasında bazı türlerde değişiklikler gözlenmiş, ancak genel biyokimyasal ve inflamasyon göstergelerinde anlamlı değişiklik bulunmamıştır. Çalışma bir kombucha üreticisi tarafından finanse edilmiştir.'),
(7, 19, 'limitation',
 'Mevcut kanıt, kombuchayı sindirim bozukluklarında bir tedavi olarak kabul etmek için yetersizdir.'),
(7, 18, 'safety',
 'Kombucha fermantasyonu etanol üretebilir. Alkol içeriği ve mikrobiyolojik güvenlik belirsiz olabileceği için hamilelikte önerilmez.');
