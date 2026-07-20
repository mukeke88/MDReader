package com.tinkertank.mdreader.controller;

import com.tinkertank.mdreader.model.ChapterImportRequest;
import com.tinkertank.mdreader.model.ChapterMeta;
import com.tinkertank.mdreader.model.ChapterResponse;
import com.tinkertank.mdreader.service.ChapterService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chapter")
public class ChapterController {

    private final ChapterService chapterService;

    public ChapterController(ChapterService chapterService) {
        this.chapterService = chapterService;
    }

    @GetMapping
    public List<ChapterMeta> getChapters() {
        return chapterService.getChapters();
    }

    @GetMapping("/{chapterId}")
    public ChapterResponse getChapter(@PathVariable String chapterId) {
        return chapterService.getChapter(chapterId);
    }

    @PostMapping("/{chapterId}/import")
    public ChapterResponse importMarkdown(@PathVariable String chapterId,
                                          @Valid @RequestBody ChapterImportRequest request) {
        return chapterService.importMarkdown(chapterId, request);
    }
}
