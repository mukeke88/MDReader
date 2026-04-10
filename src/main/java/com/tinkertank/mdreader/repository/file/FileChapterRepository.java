package com.tinkertank.mdreader.repository.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinkertank.mdreader.model.ChapterMeta;
import com.tinkertank.mdreader.model.Sentence;
import com.tinkertank.mdreader.repository.ChapterRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class FileChapterRepository implements ChapterRepository {

    private final ObjectMapper objectMapper;
    private final Path dataDirectory;

    public FileChapterRepository(ObjectMapper objectMapper,
                                 @Value("${mdreader.data-dir:data}") String dataDir) {
        this.objectMapper = objectMapper;
        this.dataDirectory = Paths.get(dataDir);
    }

    @Override
    public List<ChapterMeta> findAllChapters() {
        return readAllChapters();
    }

    @Override
    public Optional<ChapterMeta> findChapter(String chapterId) {
        return readAllChapters().stream()
                .filter(chapter -> chapterId.equals(chapter.getId()))
                .findFirst();
    }

    @Override
    public List<Sentence> findSentences(String chapterId) {
        Optional<ChapterMeta> chapter = findChapter(chapterId);
        if (!chapter.isPresent()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(
                    dataDirectory.resolve("sentences").resolve(chapter.get().getSourceFile()).toFile(),
                    new TypeReference<List<Sentence>>() {
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read sentence data", e);
        }
    }

    @Override
    public void saveChapter(ChapterMeta chapter) {
        List<ChapterMeta> chapters = readAllChapters();
        boolean updated = false;
        for (int i = 0; i < chapters.size(); i++) {
            if (chapter.getId().equals(chapters.get(i).getId())) {
                chapters.set(i, chapter);
                updated = true;
                break;
            }
        }
        if (!updated) {
            chapters.add(chapter);
        }
        writeAllChapters(chapters);
    }

    @Override
    public void saveSentences(String chapterId, List<Sentence> sentences) {
        ChapterMeta chapter = findChapter(chapterId)
                .orElseThrow(() -> new IllegalStateException("Chapter not found: " + chapterId));
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                    dataDirectory.resolve("sentences").resolve(chapter.getSourceFile()).toFile(),
                    sentences
            );
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write sentence data", e);
        }
    }

    @Override
    public void updateTitle(String chapterId, String title) {
        List<ChapterMeta> chapters = readAllChapters();
        for (ChapterMeta chapter : chapters) {
            if (chapterId.equals(chapter.getId())) {
                chapter.setTitle(title);
                writeAllChapters(chapters);
                return;
            }
        }
        throw new IllegalStateException("Chapter not found: " + chapterId);
    }

    private List<ChapterMeta> readAllChapters() {
        try {
            return objectMapper.readValue(
                    dataDirectory.resolve("chapters.json").toFile(),
                    new TypeReference<List<ChapterMeta>>() {
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read chapter metadata", e);
        }
    }

    private void writeAllChapters(List<ChapterMeta> chapters) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                    dataDirectory.resolve("chapters.json").toFile(),
                    chapters
            );
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write chapter metadata", e);
        }
    }
}
