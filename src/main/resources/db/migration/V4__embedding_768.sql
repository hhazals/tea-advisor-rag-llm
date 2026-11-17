-- V4__embedding_768.sql
-- Embedding modeli: Ollama paraphrase-multilingual (768 boyut, çok dilli, hafif).
-- Model değişirse boyutu yeni bir migration ile güncelle ve POST /admin/embed?force=true çalıştır.

DROP INDEX IF EXISTS idx_chunks_embedding;

-- Tüm embedding'ler henüz boş olduğu için tip değişimi veri kaybettirmez
ALTER TABLE knowledge_chunks
    ALTER COLUMN embedding TYPE vector(768) USING NULL;

-- Hangi modelle üretildiği: model değişince eski vektörler tespit edilip yeniden üretilebilsin
ALTER TABLE knowledge_chunks
    ADD COLUMN embedding_model VARCHAR(80);

CREATE INDEX idx_chunks_embedding
    ON knowledge_chunks USING hnsw (embedding vector_cosine_ops);
