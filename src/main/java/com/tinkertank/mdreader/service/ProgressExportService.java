package com.tinkertank.mdreader.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "mdreader.progress.storage", havingValue = "mysql")
public class ProgressExportService {

    private static final DateTimeFormatter FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final JdbcTemplate jdbcTemplate;
    private final Path exportDir;

    public ProgressExportService(JdbcTemplate jdbcTemplate,
                                 @Value("${mdreader.export.dir:D:/Dropbox/SQL}") String exportDir) {
        this.jdbcTemplate = jdbcTemplate;
        this.exportDir = Paths.get(exportDir);
    }

    public Path exportReadingProgressTable() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT chapter_id, last_sentence_id, total_score, green_score, red_score, manual_red_score, global_expanded, "
                        + "opened_sentence_ids, read_sentence_ids, scored_sentence_ids, explanation_used_sentence_ids, "
                        + "created_at, updated_at FROM reading_progress ORDER BY chapter_id");

        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS reading_progress (\n")
                .append("    chapter_id VARCHAR(255) NOT NULL,\n")
                .append("    last_sentence_id INT NULL,\n")
                .append("    total_score INT NOT NULL DEFAULT 0,\n")
                .append("    green_score INT NOT NULL DEFAULT 0,\n")
                .append("    red_score INT NOT NULL DEFAULT 0,\n")
                .append("    manual_red_score INT NOT NULL DEFAULT 0,\n")
                .append("    global_expanded BOOLEAN NOT NULL DEFAULT FALSE,\n")
                .append("    opened_sentence_ids JSON NOT NULL,\n")
                .append("    read_sentence_ids JSON NOT NULL,\n")
                .append("    scored_sentence_ids JSON NOT NULL,\n")
                .append("    explanation_used_sentence_ids JSON NOT NULL,\n")
                .append("    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,\n")
                .append("    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n")
                .append("    PRIMARY KEY (chapter_id)\n")
                .append(");\n\n");

        for (Map<String, Object> row : rows) {
            sql.append("INSERT INTO reading_progress (chapter_id, last_sentence_id, total_score, green_score, red_score, ")
                    .append("manual_red_score, global_expanded, opened_sentence_ids, read_sentence_ids, scored_sentence_ids, ")
                    .append("explanation_used_sentence_ids, created_at, updated_at) VALUES (")
                    .append(toSqlString(row.get("chapter_id"))).append(", ")
                    .append(toSqlNumber(row.get("last_sentence_id"))).append(", ")
                    .append(toSqlNumber(row.get("total_score"))).append(", ")
                    .append(toSqlNumber(row.get("green_score"))).append(", ")
                    .append(toSqlNumber(row.get("red_score"))).append(", ")
                    .append(toSqlNumber(row.get("manual_red_score"))).append(", ")
                    .append(toSqlBoolean(row.get("global_expanded"))).append(", ")
                    .append(toSqlString(row.get("opened_sentence_ids"))).append(", ")
                    .append(toSqlString(row.get("read_sentence_ids"))).append(", ")
                    .append(toSqlString(row.get("scored_sentence_ids"))).append(", ")
                    .append(toSqlString(row.get("explanation_used_sentence_ids"))).append(", ")
                    .append(toSqlTimestamp(row.get("created_at"))).append(", ")
                    .append(toSqlTimestamp(row.get("updated_at"))).append(")")
                    .append(" ON DUPLICATE KEY UPDATE ")
                    .append("last_sentence_id = VALUES(last_sentence_id), ")
                    .append("total_score = VALUES(total_score), ")
                    .append("green_score = VALUES(green_score), ")
                    .append("red_score = VALUES(red_score), ")
                    .append("manual_red_score = VALUES(manual_red_score), ")
                    .append("global_expanded = VALUES(global_expanded), ")
                    .append("opened_sentence_ids = VALUES(opened_sentence_ids), ")
                    .append("read_sentence_ids = VALUES(read_sentence_ids), ")
                    .append("scored_sentence_ids = VALUES(scored_sentence_ids), ")
                    .append("explanation_used_sentence_ids = VALUES(explanation_used_sentence_ids), ")
                    .append("created_at = VALUES(created_at), ")
                    .append("updated_at = VALUES(updated_at);\n");
        }

        return writeExport(sql.toString());
    }

    private Path writeExport(String sql) {
        try {
            Files.createDirectories(exportDir);
            String fileName = "reading_progress_" + FILE_NAME_FORMAT.format(LocalDateTime.now()) + ".sql";
            Path exportPath = exportDir.resolve(fileName);
            Files.write(exportPath, sql.getBytes(StandardCharsets.UTF_8));
            return exportPath;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to export reading_progress SQL", ex);
        }
    }

    private String toSqlString(Object value) {
        if (value == null) {
            return "NULL";
        }
        String escaped = String.valueOf(value)
                .replace("\\", "\\\\")
                .replace("'", "''");
        return "'" + escaped + "'";
    }

    private String toSqlNumber(Object value) {
        return value == null ? "NULL" : String.valueOf(value);
    }

    private String toSqlBoolean(Object value) {
        if (value == null) {
            return "0";
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? "1" : "0";
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() == 0 ? "0" : "1";
        }
        return Boolean.parseBoolean(String.valueOf(value)) ? "1" : "0";
    }

    private String toSqlTimestamp(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Timestamp) {
            return "'" + value.toString() + "'";
        }
        return "'" + String.valueOf(value) + "'";
    }
}
