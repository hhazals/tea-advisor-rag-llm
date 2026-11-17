package com.tearag.ingestion;

/** Kaynak URL'sinden ham belgeyi indirir. Testlerde mock'lanır (ağ erişimi yok). */
public interface SourceFetcher {

    /** Bilinen belge türleri; çıkarma (extract) adımı buna göre seçilir. */
    enum Kind { PDF, HTML, PUBMED_XML }

    record FetchedDocument(Kind kind, byte[] body, String url) {
    }

    /** @throws FetchException indirilemezse (403, zaman aşımı, desteklenmeyen tür…) */
    FetchedDocument fetch(String url);

    class FetchException extends RuntimeException {

        public FetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
