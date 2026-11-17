package com.tearag.ingestion;

import com.tearag.ingestion.SourceFetcher.FetchedDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

/**
 * "Extract" adımı: belgeden temizlenmiş paragraf listesi çıkarır.
 * PDF → sayfa sayfa (kalıp satırlar atılır), HTML → ana içerikteki p/li/başlıklar, PubMed XML → başlık + özet.
 */
public final class TextExtractor {

    /** HTML'de bu kadar kısa ve cümle olmayan bloklar menü/etiket sayılır. */
    static final int MIN_HTML_BLOCK_CHARS = 40;

    private static final String[] MAIN_SELECTORS = {"main article", "article", "main", "#main-content", "[role=main]"};
    private static final String NOISE = "script, style, noscript, nav, header, footer, aside, form, button, figure, "
            + "svg, iframe, [role=navigation], [aria-hidden=true], .breadcrumb, .breadcrumbs, .share, .social, "
            + ".cookie, .ref-list, .references, #references, .c-article-references, "
            + "section[data-title=References], [id~=(?i)^(bib|references)]";

    private TextExtractor() {
    }

    public static List<String> extract(FetchedDocument doc) {
        return switch (doc.kind()) {
            case PDF -> pdf(doc.body());
            case HTML -> html(new String(doc.body(), StandardCharsets.UTF_8), doc.url());
            case PUBMED_XML -> pubmedXml(new String(doc.body(), StandardCharsets.UTF_8));
        };
    }

    static List<String> pdf(byte[] body) {
        List<String> pages = new ArrayList<>();
        try (PDDocument pdf = Loader.loadPDF(body)) {
            PDFTextStripper stripper = new PDFTextStripper();
            boolean inReferences = false;
            for (int page = 1; page <= pdf.getNumberOfPages() && !inReferences; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                StringBuilder text = new StringBuilder();
                for (String line : stripper.getText(pdf).split("\\R")) {
                    if (TextCleaner.isReferencesHeading(line)) {
                        inReferences = true; // kaynakça: belgenin geri kalanı alınmaz
                        break;
                    }
                    if (!TextCleaner.isBoilerplate(line) && !TextCleaner.isCitation(line)) {
                        text.append(line.strip()).append(' ');
                    }
                }
                String cleaned = TextCleaner.normalize(text.toString());
                if (!cleaned.isEmpty()) {
                    pages.add(cleaned);
                }
            }
        } catch (IOException e) {
            throw new SourceFetcher.FetchException("PDF okunamadı", e);
        }
        return pages;
    }

    static List<String> html(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        doc.select(NOISE).remove();
        Element main = null;
        for (String selector : MAIN_SELECTORS) {
            main = doc.selectFirst(selector);
            if (main != null) {
                break;
            }
        }
        if (main == null) {
            main = doc.body();
        }
        List<String> blocks = new ArrayList<>();
        for (Element el : main.select("h1, h2, h3, h4, p, li")) {
            // iç içe bloklarda (ör. <li><p>…</p></li>) yalnızca en içteki metni al; select() öğenin kendisini de döndürür
            boolean hasNestedBlock = el.select("p, li").stream().anyMatch(inner -> inner != el);
            if (hasNestedBlock && !el.tagName().matches("h\\d")) {
                continue;
            }
            String text = TextCleaner.normalize(el.text());
            if (el.tagName().matches("h\\d") && TextCleaner.isReferencesHeading(text)) {
                break; // kaynakça bölümü: sonrası alınmaz
            }
            boolean sentenceLike = text.length() >= MIN_HTML_BLOCK_CHARS || text.matches(".*[.!?:]$");
            if (sentenceLike && !TextCleaner.isBoilerplate(text) && !TextCleaner.isCitation(text)) {
                blocks.add(text);
            }
        }
        return blocks;
    }

    static List<String> pubmedXml(String xml) {
        Document doc = Jsoup.parse(xml, "", Parser.xmlParser());
        List<String> blocks = new ArrayList<>();
        Element title = doc.selectFirst("ArticleTitle");
        if (title != null) {
            blocks.add(TextCleaner.normalize(title.text()));
        }
        for (Element abs : doc.select("Abstract AbstractText")) {
            String label = abs.attr("Label");
            String text = TextCleaner.normalize(abs.text());
            // Locale.ROOT: Türkçe sistemde "OBJECTIVES".toLowerCase() → "objectıves" olur
            blocks.add(label.isEmpty() ? text
                    : label.charAt(0) + label.substring(1).toLowerCase(Locale.ROOT) + ": " + text);
        }
        return blocks;
    }
}
