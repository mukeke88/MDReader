package com.tinkertank.mdreader.controller;

import com.tinkertank.mdreader.service.ProgressExportService;
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
    public Map<String, Object> exportReadingProgress() {
        ProgressExportService.ExportResult result = progressExportService.exportReadingProgressTable();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("path", result.getExportPath().toString());
        response.put("readingProgressTable", toTableResponse(result.getReadingProgress()));
        response.put("redGreenCountsTable", toTableResponse(result.getRedGreenCounts()));
        return response;
    }

    private Map<String, Object> toTableResponse(ProgressExportService.TableExportResult tableResult) {
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("tableName", tableResult.getTableName());
        table.put("included", tableResult.isIncluded());
        table.put("rowCount", tableResult.getRowCount());
        table.put("status", tableResult.getStatus());
        return table;
    }
}
