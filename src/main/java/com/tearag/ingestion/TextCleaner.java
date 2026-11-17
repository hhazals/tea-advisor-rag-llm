package com.tearag.ingestion;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/** "Clean" adımı: Unicode/boşluk düzeltme ve kaynaklardaki tekrarlayan kalıp metinleri atma. */
public final class TextCleaner {

    private TextCleaner() {
    }

    /** Tam satır olarak görünen kalıp metinler (EMA PDF başlık/altbilgileri, sayfa numaraları, menüler). */
    private static final List<Pattern> BOILERPLATE = List.of(
            Pattern.compile("^\\d{1,4}$"),
            Pattern.compile("^page \\d+\\s*(/|of)\\s*\\d+$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^EMA/HMPC/\\S+.*$"),
            Pattern.compile(".*©\\s*European Medicines Agency.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*Reproduction is authorised provided the source is acknowledged.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*(Official address|Domenico Scarlattilaan|Send us a question|Telephone \\+31|"
                    + "An agency of the European Union|www\\.ema\\.europa\\.eu/contact).*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(skip to (main )?content|share|print|back to top|menu|search|cookies?.*)$",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(last (updated|reviewed)|this page was last).*$", Pattern.CASE_INSENSITIVE));

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Pattern HYPHEN_BREAK = Pattern.compile("([a-z])-\\s+([a-z])");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    /** NFKC (ﬁ → fi), yumuşak tire, e-posta/URL ve satır sonu tirelemesi temizlenir; boşluklar tekleşir. */
    public static String normalize(String text) {
        String t = Normalizer.normalize(text, Normalizer.Form.NFKC).replace("­", "");
        t = EMAIL.matcher(t).replaceAll("");
        t = URL.matcher(t).replaceAll("");
        t = HYPHEN_BREAK.matcher(t).replaceAll("$1$2");
        return SPACES.matcher(t).replaceAll(" ").strip();
    }

    /** Kaynakça bölümünü başlatan başlık ("References", "4. Bibliography" …): sonrası alınmaz. */
    private static final Pattern REFERENCES_HEADING =
            Pattern.compile("^\\s*(\\d+\\.?\\s*)?(references|bibliography|literature cited|references cited)\\s*$",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern PAGE_RANGE = Pattern.compile("\\b\\d+\\s*[,:;(]\\s*\\d*\\)?\\s*:?\\s*\\d+\\s*[–-]\\s*\\d+");
    private static final Pattern YEAR = Pattern.compile("\\(?\\b(19|20)\\d{2}\\b\\)?");
    /** "et al.", doi/PMID, "Smith, J." (APA/Nature) veya "Smith JA," (Vancouver/PubMed) yazar biçimi. */
    private static final Pattern AUTHOR_SIGNAL = Pattern.compile(
            "et al\\.|doi:|\\bdoi\\.org|PMID|[A-Z][a-z]+, [A-Z]\\. ?[A-Z]?\\.?|\\b[A-Z][a-z]+ [A-Z]{1,3}[,.]");

    public static boolean isReferencesHeading(String line) {
        return REFERENCES_HEADING.matcher(line).matches();
    }

    /**
     * Kaynakça girdisine benzeyen metin: sayfa aralığı (12, 253–257 / 2010;12:34-56) + yıl + yazar/doi işareti.
     * Kaynakça parçaları içerik taşımadığı halde aramada üst sıralara çıkabiliyor.
     */
    public static boolean isCitation(String text) {
        return PAGE_RANGE.matcher(text).find() && YEAR.matcher(text).find() && AUTHOR_SIGNAL.matcher(text).find();
    }

    public static boolean isBoilerplate(String line) {
        String l = line.strip();
        return l.isEmpty() || BOILERPLATE.stream().anyMatch(p -> p.matcher(l).matches());
    }
}
