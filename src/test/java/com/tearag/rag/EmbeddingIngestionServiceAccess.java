package com.tearag.rag;

public final class EmbeddingIngestionServiceAccess {

    private EmbeddingIngestionServiceAccess() {
    }

    public static String toPgVector(float[] v) {
        return EmbeddingIngestionService.toPgVector(v);
    }
}
