package com.tinkertank.mdreader.controller;

import com.tinkertank.mdreader.model.ReadingProgress;
import com.tinkertank.mdreader.service.ProgressService;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    @GetMapping("/{chapterId}")
    public ReadingProgress getProgress(@PathVariable String chapterId) {
        return progressService.getProgress(chapterId);
    }

    @PostMapping("/{chapterId}")
    public ReadingProgress saveProgress(@PathVariable String chapterId,
                                        @Valid @RequestBody ReadingProgress progress) {
        return progressService.saveProgress(chapterId, progress);
    }
}
