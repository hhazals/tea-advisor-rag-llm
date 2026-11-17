package com.tearag.rag;

/** Başka paketlerdeki testlerin pgvector biçimlendiricisini kullanabilmesi için. */
public final class EmbeddingIngestionServiceAccess {

    private EmbeddingIngestionServiceAccess() {
    }

    public static String toPgVector(float[] v) {
        return EmbeddingIngestionService.toPgVector(v);
    }
}
