package com.tearag.ingestion;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * HTTP ile indirme. PubMed sayfaları bot kontrolü gösterdiği için PubMed kayıtları
 * NCBI E-utilities API'sinden (efetch, XML) alınır.
 */
@Component
public class HttpSourceFetcher implements SourceFetcher {

    static final int MAX_BYTES = 15 * 1024 * 1024;
    private static final Pattern PUBMED = Pattern.compile("pubmed\\.ncbi\\.nlm\\.nih\\.gov/(\\d+)");
    private static final String EFETCH =
            "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=pubmed&rettype=abstract&retmode=xml&tool=tea-rag&id=";

    private final RestClient http;

    public HttpSourceFetcher(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.http = builder.requestFactory(factory)
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; tea-rag-ingestion; learning project)")
                .build();
    }

    @Override
    public FetchedDocument fetch(String url) {
        Matcher pubmed = PUBMED.matcher(url);
        String target = pubmed.find() ? EFETCH + pubmed.group(1) : url;
        ResponseEntity<byte[]> response;
        try {
            response = http.get().uri(target).retrieve().toEntity(byte[].class);
        } catch (RestClientResponseException e) {
            throw new FetchException("HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new FetchException("indirilemedi (" + e.getClass().getSimpleName() + ")", e);
        }
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            throw new FetchException("boş cevap", null);
        }
        if (body.length > MAX_BYTES) {
            throw new FetchException("belge çok büyük (" + body.length / 1024 + " KB)", null);
        }
        String type = response.getHeaders().getContentType() == null ? ""
                : response.getHeaders().getContentType().toString().toLowerCase(Locale.ROOT);
        Kind kind;
        if (pubmed.find(0)) {
            kind = Kind.PUBMED_XML;
        } else if (type.contains("pdf") || startsWith(body, "%PDF")) {
            kind = Kind.PDF;
        } else if (type.contains("html")) {
            kind = Kind.HTML;
        } else {
            throw new FetchException("desteklenmeyen içerik türü: " + type, null);
        }
        return new FetchedDocument(kind, body, url);
    }

    private static boolean startsWith(byte[] body, String prefix) {
        if (body.length < prefix.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (body[i] != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
