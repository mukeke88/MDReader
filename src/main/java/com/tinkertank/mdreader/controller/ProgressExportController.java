package com.tinkertank.mdreader.controller;

import com.tinkertank.mdreader.service.ProgressExportService;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/progress")
@ConditionalOnProperty(name = "mdreader.progress.storage", havingValue = "mysql")
public class ProgressExportController {

    private final ProgressExportService progressExportService;

    public ProgressExportController(ProgressExportService progressExportService) {
        this.progressExportService = progressExportService;
    }

    @PostMapping("/export")
    public Map<String, String> exportReadingProgress() {
        Path exportPath = progressExportService.exportReadingProgressTable();
        Map<String, String> response = new LinkedHashMap<>();
        response.put("path", exportPath.toString());
        return response;
    }
}
