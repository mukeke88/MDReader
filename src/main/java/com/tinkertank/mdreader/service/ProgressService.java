package com.tinkertank.mdreader.service;

import com.tinkertank.mdreader.model.ReadingProgress;
import com.tinkertank.mdreader.repository.ProgressRepository;
import org.springframework.stereotype.Service;

@Service
public class ProgressService {

    public static final String DEFAULT_USER_ID = "default";

    private final ProgressRepository progressRepository;

    public ProgressService(ProgressRepository progressRepository) {
        this.progressRepository = progressRepository;
    }

    public ReadingProgress getProgress(String userId, String chapterId) {
        ReadingProgress progress = progressRepository.findByUserIdAndChapterId(normalizeUserId(userId), chapterId);
        progress.setDocumentId(chapterId);
        return progress;
    }

    public ReadingProgress saveProgress(String userId, String chapterId, ReadingProgress progress) {
        progress.setDocumentId(chapterId);
        return progressRepository.save(normalizeUserId(userId), chapterId, progress);
    }

    private String normalizeUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return DEFAULT_USER_ID;
        }
        return userId.trim();
    }
}
