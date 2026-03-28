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
    public Optional<ChapterMeta> findChapter(String chapterId) {
        try {
            List<ChapterMeta> chapters = objectMapper.readValue(
                    dataDirectory.resolve("chapters.json").toFile(),
                    new TypeReference<List<ChapterMeta>>() {
                    });
            return chapters.stream().filter(chapter -> chapterId.equals(chapter.getId())).findFirst();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read chapter metadata", e);
        }
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
}
