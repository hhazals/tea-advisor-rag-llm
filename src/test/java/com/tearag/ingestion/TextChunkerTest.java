package com.tearag.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TextChunkerTest {

    /** "Sentence N has exactly ten words in it for testing ok." → 10 kelime. */
    private static String sentence(int n) {
        return "Sentence " + n + " has exactly ten words in it for testing.";
    }

    private static String paragraph(int from, int count) {
        return IntStream.range(from, from + count).mapToObj(TextChunkerTest::sentence).collect(Collectors.joining(" "));
    }

    @Test
    void packsSentencesUpToMaxWordsWithOverlap() {
        // 40 cümle × 10 kelime = 400 kelime; max 100, örtüşme 20 → her yeni parça önceki son 2 cümleyle başlar
        List<String> chunks = TextChunker.chunk(List.of(paragraph(0, 40)), 100, 20, 25);

        assertThat(chunks).allSatisfy(c -> assertThat(TextChunker.wordCount(c)).isLessThanOrEqualTo(100));
        assertThat(chunks.get(0)).startsWith("Sentence 0 ").endsWith("Sentence 9 has exactly ten words in it for testing.");
        assertThat(chunks.get(1)).startsWith("Sentence 8 "); // örtüşme: 8 ve 9 tekrar
        // her cümle en az bir parçada
        assertThat(IntStream.range(0, 40)).allSatisfy(i ->
                assertThat(chunks).anyMatch(c -> c.contains("Sentence " + i + " has")));
    }

    @Test
    void neverSplitsInsideASentence() {
        List<String> chunks = TextChunker.chunk(List.of(paragraph(0, 25)), 55, 10, 5);
        assertThat(chunks).allSatisfy(c -> assertThat(c).endsWith("testing."));
    }

    @Test
    void shortTailIsMergedIntoPreviousChunk() {
        // 10 cümle sığar, 11. cümle (10 kelime < min 25) ayrı parça olmaz
        List<String> chunks = TextChunker.chunk(List.of(paragraph(0, 11)), 100, 0, 25);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("Sentence 10 has");
    }

    @Test
    void veryLongSentenceIsSplitByWords() {
        String noPunctuation = IntStream.range(0, 330).mapToObj(i -> "w" + i).collect(Collectors.joining(" "));
        List<String> chunks = TextChunker.chunk(List.of(noPunctuation), 150, 0, 25);
        assertThat(chunks).hasSize(3);
        assertThat(chunks).allSatisfy(c -> assertThat(TextChunker.wordCount(c)).isLessThanOrEqualTo(150));
    }

    @Test
    void emptyInputGivesNoChunks() {
        assertThat(TextChunker.chunk(List.of("", "   "))).isEmpty();
    }
}
