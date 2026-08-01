package com.tinkertank.mdreader.repository.mysql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinkertank.mdreader.model.ReadingProgress;
import com.tinkertank.mdreader.repository.ProgressRepository;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "mdreader.progress.storage", havingValue = "mysql")
public class MysqlProgressRepository implements ProgressRepository {

    private static final RowMapper<ReadingProgress> ROW_MAPPER = new RowMapper<ReadingProgress>() {
        @Override
        public ReadingProgress mapRow(ResultSet rs, int rowNum) throws SQLException {
            ReadingProgress progress = new ReadingProgress();
            progress.setUserId(rs.getString("user_id"));
            progress.setChapterId(rs.getString("chapter_id"));
            progress.setDocumentId(rs.getString("document_id"));
            int lastSentenceId = rs.getInt("last_sentence_id");
            if (rs.wasNull()) {
                progress.setLastSentenceId(null);
            } else {
                progress.setLastSentenceId(lastSentenceId);
            }
            progress.setTotalScore(rs.getInt("total_score"));
            progress.setGreenScore(rs.getInt("green_score"));
            progress.setRedScore(rs.getInt("red_score"));
            progress.setManualRedScore(rs.getInt("manual_red_score"));
            progress.setGlobalExpanded(rs.getBoolean("global_expanded"));
            return progress;
        }
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private Boolean hasDocumentIdColumn;

    public MysqlProgressRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReadingProgress findByUserIdAndChapterId(String userId, String chapterId) {
        try {
            String documentIdSelection = hasDocumentIdColumn() ? "document_id" : "chapter_id AS document_id";
            ReadingProgress progress = jdbcTemplate.queryForObject(
                    "SELECT user_id, chapter_id, " + documentIdSelection + ", last_sentence_id, total_score, green_score, red_score, manual_red_score, global_expanded, "
                            + "opened_sentence_ids, read_sentence_ids, scored_sentence_ids, explanation_used_sentence_ids "
                            + "FROM reading_progress WHERE user_id = ? AND chapter_id = ?",
                    new Object[]{userId, chapterId},
                    new RowMapper<ReadingProgress>() {
                        @Override
                        public ReadingProgress mapRow(ResultSet rs, int rowNum) throws SQLException {
                            ReadingProgress progress = ROW_MAPPER.mapRow(rs, rowNum);
                            progress.setOpenedSentenceIds(readIntList(rs.getString("opened_sentence_ids")));
                            progress.setReadSentenceIds(readIntList(rs.getString("read_sentence_ids")));
                            progress.setScoredSentenceIds(readIntList(rs.getString("scored_sentence_ids")));
                            progress.setExplanationUsedSentenceIds(readIntList(rs.getString("explanation_used_sentence_ids")));
                            return progress;
                        }
                    });
            return progress;
        } catch (EmptyResultDataAccessException ex) {
            ReadingProgress empty = new ReadingProgress();
            empty.setUserId(userId);
            empty.setChapterId(chapterId);
            empty.setDocumentId(chapterId);
            return empty;
        }
    }

    @Override
    public ReadingProgress save(String userId, String chapterId, ReadingProgress progress) {
        progress.setUserId(userId);
        progress.setChapterId(chapterId);
        progress.setDocumentId(chapterId);
        boolean writeDocumentId = hasDocumentIdColumn();
        String columns = writeDocumentId
                ? "user_id, chapter_id, document_id, last_sentence_id, total_score, green_score, red_score, "
                : "user_id, chapter_id, last_sentence_id, total_score, green_score, red_score, ";
        String values = writeDocumentId
                ? "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?"
                : "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?";
        String sql = "INSERT INTO reading_progress (" + columns
                + "manual_red_score, global_expanded, opened_sentence_ids, read_sentence_ids, scored_sentence_ids, "
                + "explanation_used_sentence_ids) VALUES (" + values + ") "
                + "ON DUPLICATE KEY UPDATE last_sentence_id = VALUES(last_sentence_id), "
                + "total_score = VALUES(total_score), green_score = VALUES(green_score), red_score = VALUES(red_score), "
                + "manual_red_score = VALUES(manual_red_score), "
                + (writeDocumentId ? "document_id = VALUES(document_id), " : "")
                + "global_expanded = VALUES(global_expanded), opened_sentence_ids = VALUES(opened_sentence_ids), "
                + "read_sentence_ids = VALUES(read_sentence_ids), scored_sentence_ids = VALUES(scored_sentence_ids), "
                + "explanation_used_sentence_ids = VALUES(explanation_used_sentence_ids)";

        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            int index = 1;
            statement.setString(index++, userId);
            statement.setString(index++, chapterId);
            if (writeDocumentId) {
                statement.setString(index++, chapterId);
            }
            if (progress.getLastSentenceId() == null) {
                statement.setNull(index++, Types.INTEGER);
            } else {
                statement.setInt(index++, progress.getLastSentenceId());
            }
            statement.setInt(index++, progress.getTotalScore());
            statement.setInt(index++, progress.getGreenScore());
            statement.setInt(index++, progress.getRedScore());
            statement.setInt(index++, progress.getManualRedScore());
            statement.setBoolean(index++, progress.isGlobalExpanded());
            statement.setString(index++, writeIntList(progress.getOpenedSentenceIds()));
            statement.setString(index++, writeIntList(progress.getReadSentenceIds()));
            statement.setString(index++, writeIntList(progress.getScoredSentenceIds()));
            statement.setString(index, writeIntList(progress.getExplanationUsedSentenceIds()));
            return statement;
        });
        return progress;
    }

    @Override
    public void deleteByChapterId(String chapterId) {
        jdbcTemplate.update("DELETE FROM reading_progress WHERE chapter_id = ?", chapterId);
    }

    private boolean hasDocumentIdColumn() {
        if (hasDocumentIdColumn == null) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'reading_progress' AND column_name = 'document_id'",
                    Integer.class);
            hasDocumentIdColumn = count != null && count > 0;
        }
        return hasDocumentIdColumn;
    }

    private List<Integer> readIntList(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return new ArrayList<Integer>();
        }
        try {
            return objectMapper.readValue(rawValue, new TypeReference<List<Integer>>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to parse progress list JSON", ex);
        }
    }

    private String writeIntList(List<Integer> values) {
        List<Integer> safeValues = values == null ? new ArrayList<Integer>() : values;
        try {
            return objectMapper.writeValueAsString(safeValues);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize progress list JSON", ex);
        }
    }
}
