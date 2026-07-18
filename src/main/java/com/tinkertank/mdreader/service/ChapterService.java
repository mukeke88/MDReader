package com.tinkertank.mdreader.service;

import com.tinkertank.mdreader.exception.ResourceNotFoundException;
import com.tinkertank.mdreader.model.ChapterMeta;
import com.tinkertank.mdreader.model.ChapterResponse;
import com.tinkertank.mdreader.repository.ChapterRepository;
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
}
