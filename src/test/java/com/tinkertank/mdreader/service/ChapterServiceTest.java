package com.tinkertank.mdreader.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tinkertank.mdreader.model.ChapterImportRequest;
import com.tinkertank.mdreader.model.ChapterMeta;
import com.tinkertank.mdreader.model.ReadingProgress;
import com.tinkertank.mdreader.model.Sentence;
import com.tinkertank.mdreader.repository.ChapterRepository;
import com.tinkertank.mdreader.repository.ProgressRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChapterServiceTest {

    @Test
    void treatsFormattedParagraphMarkersAsPlainParagraphMarkers() {
        InMemoryChapterRepository chapters = new InMemoryChapterRepository();
        ChapterService service = new ChapterService(chapters, new NoOpProgressRepository());

        ChapterImportRequest request = new ChapterImportRequest();
        request.setTitle("Formatting Test");
        request.setMarkdown(String.join("\n",
                "# PARAGRAH",
                "**Sentence one**",
                "## PARAGRAPH",
                "**Sentence two**",
                "*PARAGRAH*",
                "**Sentence three**",
                "**PARAGRAPH**",
                "**Sentence four**",
                "PARAGRAPH",
                "**Sentence five**"));

        List<Sentence> sentences = service.importMarkdown(request).getSentences();

        assertEquals(5, sentences.size());
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), paragraphIds(sentences));
        assertEquals(Arrays.asList(
                "Sentence one",
                "Sentence two",
                "Sentence three",
                "Sentence four",
                "Sentence five"), sentenceTexts(sentences));
    }

    private List<Integer> paragraphIds(List<Sentence> sentences) {
        List<Integer> paragraphIds = new ArrayList<>();
        for (Sentence sentence : sentences) {
            paragraphIds.add(sentence.getParagraphId());
        }
        return paragraphIds;
    }

    private List<String> sentenceTexts(List<Sentence> sentences) {
        List<String> texts = new ArrayList<>();
        for (Sentence sentence : sentences) {
            texts.add(sentence.getText());
        }
        return texts;
    }

    private static class InMemoryChapterRepository implements ChapterRepository {

        private final Map<String, ChapterMeta> chapters = new LinkedHashMap<>();
        private final Map<String, List<Sentence>> sentences = new LinkedHashMap<>();

        @Override
        public List<ChapterMeta> findAllChapters() {
            return new ArrayList<>(chapters.values());
        }

        @Override
        public Optional<ChapterMeta> findChapter(String chapterId) {
            return Optional.ofNullable(chapters.get(chapterId));
        }

        @Override
        public List<Sentence> findSentences(String chapterId) {
            return sentences.getOrDefault(chapterId, Collections.emptyList());
        }

        @Override
        public void saveChapter(ChapterMeta chapter) {
            chapters.put(chapter.getId(), chapter);
        }

        @Override
        public void saveSentences(String chapterId, List<Sentence> sentences) {
            this.sentences.put(chapterId, new ArrayList<>(sentences));
        }

        @Override
        public void updateTitle(String chapterId, String title) {
            chapters.get(chapterId).setTitle(title);
        }

        @Override
        public void deleteChapter(String chapterId) {
            chapters.remove(chapterId);
            sentences.remove(chapterId);
        }
    }

    private static class NoOpProgressRepository implements ProgressRepository {

        @Override
        public ReadingProgress findByUserIdAndChapterId(String userId, String chapterId) {
            return null;
        }

        @Override
        public ReadingProgress save(String userId, String chapterId, ReadingProgress progress) {
            return progress;
        }

        @Override
        public void deleteByChapterId(String chapterId) {
            // No progress is needed for this parser test.
        }
    }
}
