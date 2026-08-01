package com.tinkertank.mdreader.service;

import com.tinkertank.mdreader.exception.ResourceNotFoundException;
import com.tinkertank.mdreader.model.ChapterImportRequest;
import com.tinkertank.mdreader.model.ChapterMeta;
import com.tinkertank.mdreader.model.ChapterResponse;
import com.tinkertank.mdreader.model.Sentence;
import com.tinkertank.mdreader.repository.ChapterRepository;
import com.tinkertank.mdreader.repository.ProgressRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final ProgressRepository progressRepository;

    public ChapterService(ChapterRepository chapterRepository, ProgressRepository progressRepository) {
        this.chapterRepository = chapterRepository;
        this.progressRepository = progressRepository;
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

    public List<ChapterMeta> getChapters() {
        return chapterRepository.findAllChapters();
    }

    public void deleteChapter(String chapterId) {
        if (!chapterRepository.findChapter(chapterId).isPresent()) {
            throw new ResourceNotFoundException("Chapter not found: " + chapterId);
        }
        progressRepository.deleteByChapterId(chapterId);
        chapterRepository.deleteChapter(chapterId);
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
        // Re-importing a document replaces its sentence ids, so its old reading
        // position must never be carried into the new text.
        progressRepository.deleteByChapterId(chapterId);
        return getChapter(chapterId);
    }

    public ChapterResponse importMarkdown(ChapterImportRequest request) {
        String normalizedTitle = request.getTitle().trim();
        String chapterId = nextDocumentId(normalizedTitle);
        return importMarkdown(chapterId, request);
    }

    private String nextDocumentId(String title) {
        String baseId = title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (baseId.isEmpty()) {
            baseId = "document";
        }
        if (baseId.length() > 48) {
            baseId = baseId.substring(0, 48).replaceAll("-$", "");
        }

        String candidate = baseId;
        int suffix = 2;
        while (chapterRepository.findChapter(candidate).isPresent()) {
            candidate = baseId + "-" + suffix;
            suffix++;
            if (suffix > 999) {
                candidate = baseId + "-" + UUID.randomUUID().toString().substring(0, 8);
                break;
            }
        }
        return candidate;
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
            if (isParagraphMarker(line)) {
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

    private boolean isParagraphMarker(String line) {
        String normalized = line.trim();

        // Accept Markdown headings (for example, "## PARAGRAPH") and optional
        // closing heading markers before checking the marker text itself.
        normalized = normalized.replaceFirst("^#{1,6}\\s*", "");
        normalized = normalized.replaceFirst("\\s+#{1,6}\\s*$", "");

        // A paragraph marker is structural, so emphasis around it must not
        // turn it into sentence text. This handles *, **, _, and __ wrappers.
        while (normalized.length() >= 2
                && ((normalized.startsWith("*") && normalized.endsWith("*"))
                || (normalized.startsWith("_") && normalized.endsWith("_")))) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }

        // Keep accepting the historical misspelling as a paragraph marker so
        // existing documents import with the same paragraph structure.
        return "PARAGRAPH".equalsIgnoreCase(normalized)
                || "PARAGRAH".equalsIgnoreCase(normalized);
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
