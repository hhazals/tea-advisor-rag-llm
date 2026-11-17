package com.tearag.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.tearag.ingestion.SourceFetcher.FetchedDocument;
import com.tearag.ingestion.SourceFetcher.Kind;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class TextExtractorTest {

    @Test
    void htmlKeepsMainContentAndDropsNavigation() {
        String html = """
                <html><body>
                  <header><nav><ul><li>Home</li><li>Herbal medicines and other things menu entry</li></ul></nav></header>
                  <main>
                    <h1>Chamomile</h1>
                    <p>Chamomile has been used traditionally for mild gastrointestinal complaints such as bloating.</p>
                    <ul><li>People allergic to ragweed may be allergic to chamomile as well.</li><li>Share</li></ul>
                    <p>Contact: someone@example.org for more information about this page and topic.</p>
                    <script>var x = 1;</script>
                  </main>
                  <footer><p>Copyright footer text that is definitely long enough to pass the filter.</p></footer>
                </body></html>""";

        List<String> blocks = TextExtractor.extract(
                new FetchedDocument(Kind.HTML, html.getBytes(StandardCharsets.UTF_8), "https://example.org/"));

        assertThat(blocks).contains(
                "Chamomile has been used traditionally for mild gastrointestinal complaints such as bloating.",
                "People allergic to ragweed may be allergic to chamomile as well.");
        assertThat(String.join(" ", blocks))
                .doesNotContain("Home").doesNotContain("menu entry").doesNotContain("footer")
                .doesNotContain("Share").doesNotContain("var x")
                .doesNotContain("someone@example.org"); // e-posta temizlendi
    }

    @Test
    void referencesSectionAndCitationsAreDropped() {
        String html = """
                <html><body><main>
                  <p>In a 2015 study, 30 participants drank 2-3 cups of kombucha daily for eight weeks.</p>
                  <p>Marco, M. L. et al. Health benefits of fermented foods. Curr. Opin. Biotechnol. 44, 94–102 (2017).</p>
                  <h2>References</h2>
                  <p>This paragraph after the references heading is long enough but must not be kept.</p>
                </main></body></html>""";

        List<String> blocks = TextExtractor.extract(
                new FetchedDocument(Kind.HTML, html.getBytes(StandardCharsets.UTF_8), "https://example.org/"));

        assertThat(blocks).containsExactly(
                "In a 2015 study, 30 participants drank 2-3 cups of kombucha daily for eight weeks.");
    }

    @Test
    void citationDetectionKeepsOrdinarySentencesWithNumbers() {
        assertThat(TextCleaner.isCitation(
                "Bell, V., Ferrao, J. Fermented foods — a world perspective. Food Res. Internl 27, 253–257 (1994).")).isTrue();
        assertThat(TextCleaner.isCitation("Kairey L, et al. Nutr Rev. 2023;81(3):267-286. doi:10.1093/nutrit/nuac054")).isTrue();
        assertThat(TextCleaner.isCitation(
                "J Med Food. Jan 2015;18(1):118-127. Ekpenyong CE, Daniel NE, Antai AB. Effect of lemongrass tea.")).isTrue();
        assertThat(TextCleaner.isCitation(
                "Adults: 1.5 to 4.5 g of the herbal substance in 150 ml of boiling water, 2-3 times daily.")).isFalse();
        assertThat(TextCleaner.isCitation(
                "The HMPC concluded in 2024 that fennel can be used for mild digestive complaints.")).isFalse();
    }

    @Test
    void pubmedXmlGivesTitleAndLabelledAbstract() {
        String xml = """
                <PubmedArticleSet><PubmedArticle><MedlineCitation><Article>
                  <ArticleTitle>Effect of lemongrass tea on renal function.</ArticleTitle>
                  <Abstract>
                    <AbstractText Label="OBJECTIVES">To investigate renal effects.</AbstractText>
                    <AbstractText Label="RESULTS">Dose-dependent decreases were observed.</AbstractText>
                  </Abstract>
                  <AuthorList><Author><AffiliationInfo><Affiliation>Uni. a@b.com</Affiliation></AffiliationInfo></Author></AuthorList>
                </Article></MedlineCitation></PubmedArticle></PubmedArticleSet>""";

        List<String> blocks = TextExtractor.extract(
                new FetchedDocument(Kind.PUBMED_XML, xml.getBytes(StandardCharsets.UTF_8), "x"));

        assertThat(blocks).containsExactly(
                "Effect of lemongrass tea on renal function.",
                "Objectives: To investigate renal effects.",
                "Results: Dose-dependent decreases were observed.");
    }

    @Test
    void pdfDropsEmaBoilerplateAndJoinsLines() throws Exception {
        byte[] pdf = pdf(
                "EMA/HMPC/372841/2016",
                "Traditional herbal medicinal product for symptomatic treatment of mild,",
                "spasmodic gastro-intestinal complaints including bloating and flatulence.",
                "Page 2/9",
                "© European Medicines Agency, 2024. Reproduction is authorised provided the source is acknowledged.");

        List<String> pages = TextExtractor.extract(new FetchedDocument(Kind.PDF, pdf, "x"));

        assertThat(pages).containsExactly("Traditional herbal medicinal product for symptomatic treatment of mild, "
                + "spasmodic gastro-intestinal complaints including bloating and flatulence.");
    }

    private static byte[] pdf(String... lines) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.setLeading(14);
                cs.newLineAtOffset(40, 740);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLine();
                }
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
