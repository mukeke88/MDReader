package com.tinkertank.mdreader.service;

import com.tinkertank.mdreader.exception.ResourceNotFoundException;
import com.tinkertank.mdreader.model.ChapterImportRequest;
import com.tinkertank.mdreader.model.ChapterMeta;
import com.tinkertank.mdreader.model.ChapterResponse;
import com.tinkertank.mdreader.model.Sentence;
import com.tinkertank.mdreader.repository.ChapterRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ChapterService {

    private final ChapterRepository chapterRepository;

    public ChapterService(ChapterRepository chapterRepository) {
        this.chapterRepository = chapterRepository;
    }

    public ChapterResponse getChapter(String chapterId) {
        ChapterMeta meta = chapterRepository.findChapter(chapterId)
                .orElseThrow(() -> new ResourceNotFoundException("Chapter not found: " + chapterId));

        ChapterResponse response = new ChapterResponse();
        response.setChapterId(meta.getId());
        response.setTitle(meta.getTitle());
        response.setSentences(chapterRepository.findSentences(chapterId));
        return response;
    }

    public ChapterResponse importMarkdown(String chapterId, ChapterImportRequest request) {
        String normalizedTitle = request.getTitle().trim();
        List<Sentence> sentences = parseMarkdown(request.getMarkdown());
        if (sentences.isEmpty()) {
            throw new IllegalArgumentException("Markdown did not contain any importable sentences");
        }

        ChapterMeta meta = chapterRepository.findChapter(chapterId).orElseGet(ChapterMeta::new);
        meta.setId(chapterId);
        if (meta.getBookId() == null || meta.getBookId().trim().isEmpty()) {
            meta.setBookId("book-1");
        }
        meta.setTitle(normalizedTitle);
        if (meta.getSourceFile() == null || meta.getSourceFile().trim().isEmpty()) {
            meta.setSourceFile(chapterId + ".json");
        }

        chapterRepository.saveChapter(meta);
        chapterRepository.saveSentences(chapterId, sentences);
        return getChapter(chapterId);
    }

    private List<Sentence> parseMarkdown(String markdown) {
        List<Sentence> sentences = new ArrayList<>();
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        int paragraphId = 0;
        Sentence currentSentence = null;
        StringBuilder explanation = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if ("PARAGRAPH".equalsIgnoreCase(line)) {
                flushExplanation(currentSentence, explanation);
                paragraphId++;
                continue;
            }

            String sentenceText = unwrapBoldLine(line);
            if (sentenceText != null) {
                flushExplanation(currentSentence, explanation);
                currentSentence = new Sentence();
                currentSentence.setId(sentences.size() + 1);
                currentSentence.setParagraphId(Math.max(1, paragraphId));
                currentSentence.setText(sentenceText);
                currentSentence.setExplanation("Explanation not provided yet.");
                sentences.add(currentSentence);
                continue;
            }

            if (currentSentence == null) {
                currentSentence = new Sentence();
                currentSentence.setId(sentences.size() + 1);
                currentSentence.setParagraphId(Math.max(1, paragraphId));
                currentSentence.setText(line);
                currentSentence.setExplanation("Explanation not provided yet.");
                sentences.add(currentSentence);
            } else {
                if (explanation.length() > 0) {
                    explanation.append(' ');
                }
                explanation.append(line);
            }
        }

        flushExplanation(currentSentence, explanation);
        return sentences;
    }

    private String unwrapBoldLine(String line) {
        if (!line.startsWith("**")) {
            return null;
        }

        String unwrapped = line.substring(2);
        if (unwrapped.endsWith("**")) {
            unwrapped = unwrapped.substring(0, unwrapped.length() - 2);
        } else if (unwrapped.endsWith("*")) {
            unwrapped = unwrapped.substring(0, unwrapped.length() - 1);
        }
        return unwrapped.trim();
    }

    private void flushExplanation(Sentence sentence, StringBuilder explanation) {
        if (sentence != null && explanation.length() > 0) {
            sentence.setExplanation(explanation.toString());
            explanation.setLength(0);
        }
    }
}
