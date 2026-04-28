package com.tinkertank.mdreader.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

    public ExportResult exportReadingProgressTable() {
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

        TableExportResult readingProgressResult = new TableExportResult("reading_progress", true, rows.size(), "exported");
        TableExportResult redGreenCountsResult = appendDynamicTableExport(sql, "red_green_counts");

        Path exportPath = writeExport(sql.toString());
        return new ExportResult(exportPath, readingProgressResult, redGreenCountsResult);
    }

    private TableExportResult appendDynamicTableExport(StringBuilder sql, String tableName) {
        if (!tableExists(tableName)) {
            return new TableExportResult(tableName, false, 0, "missing");
        }

        sql.append("\n");
        sql.append(exportCreateTableStatement(tableName)).append(";\n\n");

        List<String> insertableColumns = getInsertableColumns(tableName);
        TableData tableData = readTableData(tableName, insertableColumns);
        if (tableData.getRows().isEmpty()) {
            return new TableExportResult(tableName, true, 0, "empty");
        }

        String columnList = tableData.getColumns().stream()
                .map(this::quoteIdentifier)
                .collect(Collectors.joining(", "));

        for (List<Object> row : tableData.getRows()) {
            String values = row.stream()
                    .map(this::toSqlLiteral)
                    .collect(Collectors.joining(", "));
            sql.append("INSERT INTO ")
                    .append(quoteIdentifier(tableName))
                    .append(" (")
                    .append(columnList)
                    .append(") VALUES (")
                    .append(values)
                    .append(");\n");
        }

        return new TableExportResult(tableName, true, tableData.getRows().size(), "exported");
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName);
        return count != null && count > 0;
    }

    private String exportCreateTableStatement(String tableName) {
        return jdbcTemplate.queryForObject(
                "SHOW CREATE TABLE " + quoteIdentifier(tableName),
                (rs, rowNum) -> rs.getString("Create Table"));
    }

    private List<String> getInsertableColumns(String tableName) {
        List<Map<String, Object>> columnRows = jdbcTemplate.queryForList(
                "SELECT column_name, extra FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? "
                        + "ORDER BY ordinal_position",
                tableName);

        List<String> insertableColumns = new ArrayList<String>();
        List<String> skippedColumns = new ArrayList<String>();
        for (Map<String, Object> columnRow : columnRows) {
            String columnName = String.valueOf(columnRow.get("column_name"));
            String extra = columnRow.get("extra") == null ? "" : String.valueOf(columnRow.get("extra"));
            if (extra.toLowerCase().contains("generated")) {
                skippedColumns.add(columnName);
                continue;
            }
            insertableColumns.add(columnName);
        }

        return insertableColumns;
    }

    private TableData readTableData(String tableName, List<String> columns) {
        String selectList = columns.stream()
                .map(this::quoteIdentifier)
                .collect(Collectors.joining(", "));
        return jdbcTemplate.query("SELECT " + selectList + " FROM " + quoteIdentifier(tableName), this::extractTableData);
    }

    private TableData extractTableData(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        List<String> columns = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            columns.add(metaData.getColumnLabel(i));
        }

        List<List<Object>> rows = new ArrayList<>();
        while (rs.next()) {
            List<Object> row = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                row.add(rs.getObject(i));
            }
            rows.add(row);
        }

        return new TableData(Collections.unmodifiableList(columns), rows);
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

    private String toSqlLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Boolean || value instanceof Number) {
            return toSqlBooleanOrNumber(value);
        }
        if (value instanceof Timestamp) {
            return toSqlTimestamp(value);
        }
        return toSqlString(value);
    }

    private String toSqlBooleanOrNumber(Object value) {
        if (value instanceof Boolean) {
            return toSqlBoolean(value);
        }
        return toSqlNumber(value);
    }

    private String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private static final class TableData {
        private final List<String> columns;
        private final List<List<Object>> rows;

        private TableData(List<String> columns, List<List<Object>> rows) {
            this.columns = columns;
            this.rows = rows;
        }

        private List<String> getColumns() {
            return columns;
        }

        private List<List<Object>> getRows() {
            return rows;
        }
    }

    public static final class ExportResult {
        private final Path exportPath;
        private final TableExportResult readingProgress;
        private final TableExportResult redGreenCounts;

        private ExportResult(Path exportPath, TableExportResult readingProgress, TableExportResult redGreenCounts) {
            this.exportPath = exportPath;
            this.readingProgress = readingProgress;
            this.redGreenCounts = redGreenCounts;
        }

        public Path getExportPath() {
            return exportPath;
        }

        public TableExportResult getReadingProgress() {
            return readingProgress;
        }

        public TableExportResult getRedGreenCounts() {
            return redGreenCounts;
        }
    }

    public static final class TableExportResult {
        private final String tableName;
        private final boolean included;
        private final int rowCount;
        private final String status;

        private TableExportResult(String tableName, boolean included, int rowCount, String status) {
            this.tableName = tableName;
            this.included = included;
            this.rowCount = rowCount;
            this.status = status;
        }

        public String getTableName() {
            return tableName;
        }

        public boolean isIncluded() {
            return included;
        }

        public int getRowCount() {
            return rowCount;
        }

        public String getStatus() {
            return status;
        }
    }
}
