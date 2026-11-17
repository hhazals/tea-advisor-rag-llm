-- V5__document_ingestion.sql
-- Gerçek belge ingestion: kaynak belgeler indirilir, temizlenir, parçalanır ve knowledge_chunks'a yazılır.
-- Elle yazılmış (curated) parçalar kalır; ingestion parçaları origin = 'ingested' ile ayrılır.

-- 1) Kaynak ↔ içecek eşleştirmesi (bir kaynak birden fazla içeceği anlatabilir)
CREATE TABLE source_drinks (
    source_id INT NOT NULL REFERENCES sources(id),
    drink_id  INT NOT NULL REFERENCES drinks(id),
    PRIMARY KEY (source_id, drink_id)
);

INSERT INTO source_drinks (source_id, drink_id) VALUES
(1, 1), (2, 1),                 -- rezene: EMA monograf + EMA sayfası
(3, 2), (4, 2), (5, 2),         -- melisa: EMA sayfası, monograf, değerlendirme raporu
(6, 3), (7, 3), (8, 3),         -- limon otu: MSK, PubMed, PMC derleme
(9, 4), (10, 4), (11, 4),       -- papatya: EMA monograf, EMA sayfası, NCCIH
(12, 5), (13, 5), (14, 5),      -- zencefil: EMA sayfası, monograf, NCCIH
(15, 6), (16, 6),               -- kefir: sistematik derleme, NIH ODS probiyotik
(17, 6), (17, 7),               -- ISAPP fermente gıdalar: kefir + kombucha
(18, 6), (18, 7),               -- Health Canada etanol: kefir + kombucha
(19, 7),                        -- kombucha klinik çalışma
(20, 8);                        -- NHS hazımsızlık: nane (pasif)

-- 2) knowledge_chunks: ingestion parçalarının iddia türü yok (belge alıntısı)
ALTER TABLE knowledge_chunks ALTER COLUMN claim_type DROP NOT NULL;

ALTER TABLE knowledge_chunks
    ADD COLUMN origin VARCHAR(20) NOT NULL DEFAULT 'curated'
        CHECK (origin IN ('curated', 'ingested')),
    ADD COLUMN chunk_index INT,
    ADD CONSTRAINT chk_curated_claim_type CHECK (origin = 'ingested' OR claim_type IS NOT NULL);

CREATE INDEX idx_chunks_source_origin ON knowledge_chunks (source_id, origin);

-- 3) Kaynak bazında son ingestion durumu
ALTER TABLE sources
    ADD COLUMN ingested_at   TIMESTAMPTZ,
    ADD COLUMN ingest_status TEXT;
