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
            progress.setChapterId(rs.getString("chapter_id"));
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

    public MysqlProgressRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReadingProgress findByChapterId(String chapterId) {
        try {
            ReadingProgress progress = jdbcTemplate.queryForObject(
                    "SELECT chapter_id, last_sentence_id, total_score, green_score, red_score, manual_red_score, global_expanded, "
                            + "opened_sentence_ids, read_sentence_ids, scored_sentence_ids, explanation_used_sentence_ids "
                            + "FROM reading_progress WHERE chapter_id = ?",
                    new Object[]{chapterId},
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
            empty.setChapterId(chapterId);
            return empty;
        }
    }

    @Override
    public ReadingProgress save(String chapterId, ReadingProgress progress) {
        progress.setChapterId(chapterId);
        String sql = "INSERT INTO reading_progress (chapter_id, last_sentence_id, total_score, green_score, red_score, "
                + "manual_red_score, global_expanded, opened_sentence_ids, read_sentence_ids, scored_sentence_ids, "
                + "explanation_used_sentence_ids) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE last_sentence_id = VALUES(last_sentence_id), "
                + "total_score = VALUES(total_score), green_score = VALUES(green_score), red_score = VALUES(red_score), "
                + "manual_red_score = VALUES(manual_red_score), "
                + "global_expanded = VALUES(global_expanded), opened_sentence_ids = VALUES(opened_sentence_ids), "
                + "read_sentence_ids = VALUES(read_sentence_ids), scored_sentence_ids = VALUES(scored_sentence_ids), "
                + "explanation_used_sentence_ids = VALUES(explanation_used_sentence_ids)";

        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, chapterId);
            if (progress.getLastSentenceId() == null) {
                statement.setNull(2, Types.INTEGER);
            } else {
                statement.setInt(2, progress.getLastSentenceId());
            }
            statement.setInt(3, progress.getTotalScore());
            statement.setInt(4, progress.getGreenScore());
            statement.setInt(5, progress.getRedScore());
            statement.setInt(6, progress.getManualRedScore());
            statement.setBoolean(7, progress.isGlobalExpanded());
            statement.setString(8, writeIntList(progress.getOpenedSentenceIds()));
            statement.setString(9, writeIntList(progress.getReadSentenceIds()));
            statement.setString(10, writeIntList(progress.getScoredSentenceIds()));
            statement.setString(11, writeIntList(progress.getExplanationUsedSentenceIds()));
            return statement;
        });
        return progress;
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
