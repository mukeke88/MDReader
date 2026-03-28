package com.tinkertank.mdreader.service;

import com.tinkertank.mdreader.model.ReadingProgress;
import com.tinkertank.mdreader.repository.ProgressRepository;
import org.springframework.stereotype.Service;

@Service
public class ProgressService {

    private final ProgressRepository progressRepository;

    public ProgressService(ProgressRepository progressRepository) {
        this.progressRepository = progressRepository;
    }

    public ReadingProgress getProgress(String chapterId) {
        return progressRepository.findByChapterId(chapterId);
    }

    public ReadingProgress saveProgress(String chapterId, ReadingProgress progress) {
        return progressRepository.save(chapterId, progress);
    }
}
