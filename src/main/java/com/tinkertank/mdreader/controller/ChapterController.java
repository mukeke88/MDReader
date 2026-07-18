package com.tinkertank.mdreader.controller;

import com.tinkertank.mdreader.model.ChapterResponse;
import com.tinkertank.mdreader.service.ChapterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chapter")
public class ChapterController {

    private final ChapterService chapterService;

    public ChapterController(ChapterService chapterService) {
        this.chapterService = chapterService;
    }

    @GetMapping("/{chapterId}")
    public ChapterResponse getChapter(@PathVariable String chapterId) {
        return chapterService.getChapter(chapterId);
    }
}
