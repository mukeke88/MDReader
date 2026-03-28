package com.tinkertank.mdreader.service;

import com.tinkertank.mdreader.exception.ResourceNotFoundException;
import com.tinkertank.mdreader.model.ChapterMeta;
import com.tinkertank.mdreader.model.ChapterResponse;
import com.tinkertank.mdreader.model.ImportChapterRequest;
import com.tinkertank.mdreader.model.ReadingProgress;
import com.tinkertank.mdreader.repository.ChapterRepository;
import com.tinkertank.mdreader.repository.ProgressRepository;
import org.springframework.stereotype.Service;

@Service
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final ProgressRepository progressRepository;
    private final MarkdownImportService markdownImportService;

    public ChapterService(ChapterRepository chapterRepository,
                          ProgressRepository progressRepository,
                          MarkdownImportService markdownImportService) {
        this.chapterRepository = chapterRepository;
        this.progressRepository = progressRepository;
        this.markdownImportService = markdownImportService;
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

    public ChapterResponse importChapter(String chapterId, ImportChapterRequest request) {
        chapterRepository.findChapter(chapterId)
                .orElseThrow(() -> new ResourceNotFoundException("Chapter not found: " + chapterId));

        MarkdownImportService.ImportResult importResult = markdownImportService.parse(request);
        chapterRepository.saveSentences(chapterId, importResult.getSentences());
        chapterRepository.updateTitle(chapterId, importResult.getTitle());

        ReadingProgress resetProgress = importResult.getProgress();
        resetProgress.setChapterId(chapterId);
        resetProgress.setGlobalExpanded(false);
        progressRepository.save(chapterId, resetProgress);

        return getChapter(chapterId);
    }
}
