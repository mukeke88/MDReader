package com.tinkertank.mdreader.service;

import com.tinkertank.mdreader.model.ImportChapterRequest;
import com.tinkertank.mdreader.model.ReadingProgress;
import com.tinkertank.mdreader.model.Sentence;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MarkdownImportService {

    private static final Pattern BOLD_LINE_PATTERN = Pattern.compile("^\\*\\*(.+?)\\*\\*$");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6}\\s+(.+)$");
    private static final Pattern SENTENCE_SPLIT_PATTERN = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern PARAGRAPH_MARKER_PATTERN = Pattern.compile("(?m)^PARAGRAPH\\s*$");
    private static final String DEFAULT_EXPLANATION = "Explanation not provided yet.";

    public ImportResult parse(ImportChapterRequest request) {
        String markdown = request.getMarkdown() == null ? "" : request.getMarkdown().replace("\r\n", "\n");
        boolean hasExplicitParagraphMarkers = PARAGRAPH_MARKER_PATTERN.matcher(markdown).find();
        List<Sentence> sentences = new ArrayList<Sentence>();
        List<String> genericBuffer = new ArrayList<String>();
        String title = StringUtils.hasText(request.getTitle()) ? request.getTitle().trim() : "Imported Material";
        String[] lines = markdown.split("\n");
        int paragraphId = 1;
        int sentenceId = 1;

        for (int i = 0; i < lines.length; i++) {
            String rawLine = lines[i];
            String line = rawLine == null ? "" : rawLine.trim();

            if (!StringUtils.hasText(line)) {
                boolean hadBufferedText = !genericBuffer.isEmpty();
                sentenceId = flushGenericBuffer(sentences, genericBuffer, paragraphId, sentenceId);
                if (!hasExplicitParagraphMarkers && hadBufferedText) {
                    paragraphId++;
                }
                continue;
            }

            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.matches()) {
                sentenceId = flushGenericBuffer(sentences, genericBuffer, paragraphId, sentenceId);
                if (!StringUtils.hasText(request.getTitle())) {
                    title = cleanInlineMarkdown(headingMatcher.group(1));
                }
                continue;
            }

            if ("PARAGRAPH".equalsIgnoreCase(line)) {
                sentenceId = flushGenericBuffer(sentences, genericBuffer, paragraphId, sentenceId);
                if (!sentences.isEmpty()) {
                    paragraphId++;
                }
                continue;
            }

            Matcher boldMatcher = BOLD_LINE_PATTERN.matcher(line);
            if (boldMatcher.matches()) {
                sentenceId = flushGenericBuffer(sentences, genericBuffer, paragraphId, sentenceId);
                Sentence sentence = new Sentence();
                sentence.setId(sentenceId++);
                sentence.setParagraphId(paragraphId);
                sentence.setText(cleanInlineMarkdown(boldMatcher.group(1)));
                sentence.setExplanation(findExplanation(lines, i + 1));
                sentences.add(sentence);
                continue;
            }

            genericBuffer.add(line);
        }

        flushGenericBuffer(sentences, genericBuffer, paragraphId, sentenceId);

        if (sentences.isEmpty()) {
            throw new IllegalArgumentException("No readable sentences were found in the pasted markdown.");
        }

        return new ImportResult(title, sentences, new ReadingProgress());
    }

    private int flushGenericBuffer(List<Sentence> sentences,
                                   List<String> genericBuffer,
                                   int paragraphId,
                                   int nextSentenceId) {
        if (genericBuffer.isEmpty()) {
            return nextSentenceId;
        }

        String combined = cleanInlineMarkdown(String.join(" ", genericBuffer));
        genericBuffer.clear();
        if (!StringUtils.hasText(combined)) {
            return nextSentenceId;
        }

        String[] parts = SENTENCE_SPLIT_PATTERN.split(combined);
        for (String part : parts) {
            String text = part.trim();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            Sentence sentence = new Sentence();
            sentence.setId(nextSentenceId++);
            sentence.setParagraphId(paragraphId);
            sentence.setText(text);
            sentence.setExplanation(DEFAULT_EXPLANATION);
            sentences.add(sentence);
        }
        return nextSentenceId;
    }

    private String findExplanation(String[] lines, int startIndex) {
        for (int i = startIndex; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (!StringUtils.hasText(line)) {
                continue;
            }
            if ("PARAGRAPH".equalsIgnoreCase(line)) {
                break;
            }
            if (HEADING_PATTERN.matcher(line).matches()) {
                break;
            }
            if (BOLD_LINE_PATTERN.matcher(line).matches()) {
                break;
            }
            return cleanInlineMarkdown(line);
        }
        return DEFAULT_EXPLANATION;
    }

    private String cleanInlineMarkdown(String text) {
        String cleaned = text.replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replace("[", "")
                .replace("]", "")
                .replace("(", "")
                .replace(")", "")
                .replace("#", "");
        return cleaned.trim();
    }

    public static class ImportResult {

        private final String title;
        private final List<Sentence> sentences;
        private final ReadingProgress progress;

        public ImportResult(String title, List<Sentence> sentences, ReadingProgress progress) {
            this.title = title;
            this.sentences = sentences;
            this.progress = progress;
        }

        public String getTitle() {
            return title;
        }

        public List<Sentence> getSentences() {
            return sentences;
        }

        public ReadingProgress getProgress() {
            return progress;
        }
    }
}
