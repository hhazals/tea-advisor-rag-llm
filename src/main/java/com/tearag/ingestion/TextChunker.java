package com.tearag.ingestion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public final class TextChunker {

    public static final int MAX_WORDS = 150;
    public static final int OVERLAP_WORDS = 30;
    public static final int MIN_WORDS = 25;

    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+(?=[A-Z0-9(\"“])");

    private TextChunker() {
    }

    public static List<String> chunk(List<String> paragraphs) {
        return chunk(paragraphs, MAX_WORDS, OVERLAP_WORDS, MIN_WORDS);
    }

    public static List<String> chunk(List<String> paragraphs, int maxWords, int overlapWords, int minWords) {
        List<String> sentences = new ArrayList<>();
        for (String p : paragraphs) {
            for (String s : SENTENCE_END.split(p.strip())) {
                if (!s.isBlank()) {
                    sentences.addAll(splitLong(s.strip(), maxWords));
                }
            }
        }

        List<String> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int overlapCount = 0; // current'ın başındaki, önceki parçadan taşınan cümle sayısı
        for (String sentence : sentences) {
            boolean hasNewText = current.size() > overlapCount;
            if (hasNewText && wordCount(current) + wordCount(sentence) > maxWords) {
                chunks.add(String.join(" ", current));
                List<String> overlap = tail(current, overlapWords);
                current = new ArrayList<>(overlap);
                overlapCount = overlap.size();
            }
            current.add(sentence);
        }

        if (current.size() > overlapCount) {
            List<String> fresh = current.subList(overlapCount, current.size());
            if (wordCount(fresh) < minWords && !chunks.isEmpty()) {
                int last = chunks.size() - 1;
                chunks.set(last, chunks.get(last) + " " + String.join(" ", fresh));
            } else {
                chunks.add(String.join(" ", current));
            }
        }
        return chunks;
    }

    static int wordCount(String s) {
        String t = s.strip();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
    }

    static int wordCount(List<String> sentences) {
        return sentences.stream().mapToInt(TextChunker::wordCount).sum();
    }

    /** Son cümlelerden, toplamı overlapWords'ü aşmayanlar. */
    private static List<String> tail(List<String> sentences, int overlapWords) {
        List<String> out = new ArrayList<>();
        int total = 0;
        for (int i = sentences.size() - 1; i >= 0; i--) {
            int w = wordCount(sentences.get(i));
            if (total + w > overlapWords) {
                break;
            }
            out.add(0, sentences.get(i));
            total += w;
        }
        return out;
    }
    private static List<String> splitLong(String sentence, int maxWords) {
        String[] words = sentence.split("\\s+");
        if (words.length <= maxWords) {
            return List.of(sentence);
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < words.length; i += maxWords) {
            parts.add(String.join(" ", Arrays.copyOfRange(words, i, Math.min(words.length, i + maxWords))));
        }
        return parts;
    }
}
