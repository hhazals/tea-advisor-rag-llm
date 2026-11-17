-- V1__init_schema.sql
-- İçecek öneri uygulaması: temel şema
-- Konum (Spring Boot + Flyway): src/main/resources/db/migration/

CREATE EXTENSION IF NOT EXISTS vector;

-- 1) İçecekler -------------------------------------------------------------
CREATE TABLE drinks (
    id               SERIAL PRIMARY KEY,
    slug             VARCHAR(40)  NOT NULL UNIQUE,      -- kod içinde kullanılan sabit ad
    name_tr          VARCHAR(80)  NOT NULL,
    name_en          VARCHAR(80)  NOT NULL,
    scientific_name  VARCHAR(120),
    category         VARCHAR(30)  NOT NULL
                     CHECK (category IN ('herbal_tea', 'fermented_beverage')),
    plant_part       VARCHAR(60),
    contains_caffeine BOOLEAN     NOT NULL DEFAULT FALSE,
    contains_lactose  BOOLEAN     NOT NULL DEFAULT FALSE,
    evidence_level   VARCHAR(200) NOT NULL,
    evidence_note    TEXT,
    active           BOOLEAN      NOT NULL DEFAULT TRUE  -- kaynağı eksik içecekler FALSE
);

-- 2) Kaynaklar -------------------------------------------------------------
CREATE TABLE sources (
    id               SERIAL PRIMARY KEY,
    title            VARCHAR(300) NOT NULL,
    url              TEXT         NOT NULL UNIQUE,
    authority        VARCHAR(150) NOT NULL,             -- EMA, NCCIH, Nutrition Reviews...
    source_type      VARCHAR(40)  NOT NULL
                     CHECK (source_type IN ('regulatory_monograph', 'regulatory_summary',
                                            'government_factsheet', 'systematic_review',
                                            'clinical_study', 'narrative_review',
                                            'consensus_statement', 'evidence_database')),
    reference_number VARCHAR(80),                       -- EMA/HMPC/... veya PMID
    published_date   DATE,
    funding_note     TEXT,                              -- çıkar çatışması vb.
    url_verified     BOOLEAN      NOT NULL DEFAULT FALSE,
    accessed_date    DATE
);

-- 3) Yemek kategorileri ----------------------------------------------------
CREATE TABLE food_categories (
    id          SERIAL PRIMARY KEY,
    slug        VARCHAR(40)  NOT NULL UNIQUE,
    name_tr     VARCHAR(80)  NOT NULL,
    description TEXT
);

-- Yemeklerin kendisi (kalori dış API'den, kcal_cache'e yazılır)
CREATE TABLE foods (
    id               SERIAL PRIMARY KEY,
    name             VARCHAR(120) NOT NULL UNIQUE,
    food_category_id INT REFERENCES food_categories(id),
    api_food_id      VARCHAR(60),
    kcal_per_100g    NUMERIC(7,2),
    kcal_fetched_at  TIMESTAMPTZ
);

-- Günlük öğün kayıtları
CREATE TABLE meals (
    id         SERIAL PRIMARY KEY,
    meal_date  DATE        NOT NULL DEFAULT CURRENT_DATE,
    meal_type  VARCHAR(20) NOT NULL
               CHECK (meal_type IN ('breakfast', 'lunch', 'dinner', 'snack')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE meal_items (
    meal_id      INT NOT NULL REFERENCES meals(id) ON DELETE CASCADE,
    food_id      INT NOT NULL REFERENCES foods(id),
    portion_gram NUMERIC(7,2) NOT NULL CHECK (portion_gram > 0),
    PRIMARY KEY (meal_id, food_id)
);

-- 4) Kural tablosu: senin eşleştirmen (kaynak iddiası DEĞİL) ---------------
CREATE TABLE drink_rules (
    food_category_id INT NOT NULL REFERENCES food_categories(id),
    drink_id         INT NOT NULL REFERENCES drinks(id),
    role             VARCHAR(20) NOT NULL
                     CHECK (role IN ('primary', 'alternative', 'avoid_if_condition')),
    base_score       INT NOT NULL DEFAULT 0,             -- puanlama algoritması için
    condition_code   VARCHAR(40),                        -- role = avoid_if_condition ise dolu
    rationale_tr     TEXT,
    PRIMARY KEY (food_category_id, drink_id),
    CHECK ((role = 'avoid_if_condition') = (condition_code IS NOT NULL))
);

-- 5) Güvenlik notları: kod tarafında kesin kısıt olarak uygulanır ----------
CREATE TABLE safety_notes (
    id             SERIAL PRIMARY KEY,
    drink_id       INT NOT NULL REFERENCES drinks(id),
    condition_code VARCHAR(40),     -- reflux, pregnancy, lactose, asteraceae_allergy,
                                    -- under_12, under_18, kidney, anticoagulant, evening
    severity       VARCHAR(10) NOT NULL CHECK (severity IN ('info', 'caution', 'exclude')),
    note_tr        TEXT NOT NULL,
    source_id      INT REFERENCES sources(id)
);

-- 6) RAG bilgi tabanı: her parça tek bir kaynağa bağlı ---------------------
CREATE TABLE knowledge_chunks (
    id          SERIAL PRIMARY KEY,
    drink_id    INT  NOT NULL REFERENCES drinks(id),
    source_id   INT  NOT NULL REFERENCES sources(id),
    claim_type  VARCHAR(30) NOT NULL
                CHECK (claim_type IN ('traditional_use', 'well_established_use',
                                      'clinical_evidence', 'limitation', 'safety')),
    content     TEXT NOT NULL,
    -- Boyut embedding modeline göre değişir (ör. 1536 = OpenAI text-embedding-3-small,
    -- 768 = nomic-embed-text / Ollama). Modeli seçince gerekirse güncelle.
    embedding   vector(1536),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chunks_embedding
    ON knowledge_chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_chunks_drink ON knowledge_chunks (drink_id);

-- 7) Öneri geçmişi ---------------------------------------------------------
CREATE TABLE recommendations (
    id              SERIAL PRIMARY KEY,
    rec_date        DATE  NOT NULL,
    drink_id        INT   NOT NULL REFERENCES drinks(id),
    reason_tr       TEXT,
    used_chunk_ids  INT[] NOT NULL DEFAULT '{}',
    generated_by    VARCHAR(20) NOT NULL CHECK (generated_by IN ('rules', 'rag_llm')),
    user_feedback   SMALLINT CHECK (user_feedback BETWEEN 1 AND 5),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
