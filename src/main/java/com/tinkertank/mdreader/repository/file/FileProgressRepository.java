package com.tinkertank.mdreader.repository.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinkertank.mdreader.model.ReadingProgress;
import com.tinkertank.mdreader.repository.ProgressRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "mdreader.progress.storage", havingValue = "file", matchIfMissing = true)
public class FileProgressRepository implements ProgressRepository {

    private final ObjectMapper objectMapper;
    private final Path progressFile;

    public FileProgressRepository(ObjectMapper objectMapper,
                                  @Value("${mdreader.data-dir:data}") String dataDir) {
        this.objectMapper = objectMapper;
        this.progressFile = Paths.get(dataDir).resolve("progress.json");
    }

    @Override
    public ReadingProgress findByUserIdAndChapterId(String userId, String chapterId) {
        Map<String, ReadingProgress> progressMap = readAll();
        ReadingProgress progress = progressMap.get(progressKey(userId, chapterId));
        if (progress == null && "default".equals(userId)) {
            progress = progressMap.get(chapterId);
        }
        if (progress != null) {
            progress.setUserId(userId);
            progress.setChapterId(chapterId);
            return progress;
        }
        ReadingProgress empty = new ReadingProgress();
        empty.setUserId(userId);
        empty.setChapterId(chapterId);
        return empty;
    }

    @Override
    public ReadingProgress save(String userId, String chapterId, ReadingProgress progress) {
        progress.setUserId(userId);
        progress.setChapterId(chapterId);
        Map<String, ReadingProgress> progressMap = readAll();
        progressMap.put(progressKey(userId, chapterId), progress);
        writeAll(progressMap);
        return progress;
    }

    private String progressKey(String userId, String chapterId) {
        return userId + "::" + chapterId;
    }

    private Map<String, ReadingProgress> readAll() {
        try {
            ensureStorageExists();
            return objectMapper.readValue(progressFile.toFile(),
                    new TypeReference<HashMap<String, ReadingProgress>>() {
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read progress data", e);
        }
    }

    private void writeAll(Map<String, ReadingProgress> progressMap) {
        try {
            ensureStorageExists();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(progressFile.toFile(), progressMap);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write progress data", e);
        }
    }

    private void ensureStorageExists() throws IOException {
        Path parent = progressFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!Files.exists(progressFile)) {
            Files.write(progressFile, "{}".getBytes(StandardCharsets.UTF_8));
        }
    }
}
