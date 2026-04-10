package com.tinkertank.mdreader.service;

import com.tinkertank.mdreader.exception.ResourceNotFoundException;
import com.tinkertank.mdreader.model.ChapterMeta;
import com.tinkertank.mdreader.model.ChapterResponse;
import com.tinkertank.mdreader.model.ImportChapterRequest;
import com.tinkertank.mdreader.repository.ChapterRepository;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChapterService {

    private static final String DEFAULT_CHAPTER_ID = "chapter-1";
    private static final String DEFAULT_BOOK_ID = "book-1";
    private static final String TEMP_CHAPTER_NAME = "temp";

    private final ChapterRepository chapterRepository;
    private final MarkdownImportService markdownImportService;

    public ChapterService(ChapterRepository chapterRepository,
                          MarkdownImportService markdownImportService) {
        this.chapterRepository = chapterRepository;
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
        String targetChapterId = resolveChapterId(importResult.getTitle());

        ChapterMeta chapterMeta = chapterRepository.findChapter(targetChapterId)
                .orElseGet(() -> createChapterMeta(targetChapterId, importResult.getTitle()));
        chapterMeta.setTitle(importResult.getTitle());
        chapterRepository.saveChapter(chapterMeta);
        chapterRepository.saveSentences(targetChapterId, importResult.getSentences());

        return getChapter(targetChapterId);
    }

    private ChapterMeta createChapterMeta(String chapterId, String title) {
        ChapterMeta chapterMeta = new ChapterMeta();
        chapterMeta.setId(chapterId);
        chapterMeta.setBookId(DEFAULT_BOOK_ID);
        chapterMeta.setTitle(title);
        chapterMeta.setSourceFile(chapterId + ".json");
        return chapterMeta;
    }

    private String resolveChapterId(String title) {
        String normalized = StringUtils.hasText(title) ? title.trim() : DEFAULT_CHAPTER_ID;
        if (TEMP_CHAPTER_NAME.equalsIgnoreCase(normalized)) {
            return DEFAULT_CHAPTER_ID;
        }

        String slug = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return StringUtils.hasText(slug) ? slug : DEFAULT_CHAPTER_ID;
    }
}
