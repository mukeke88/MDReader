package com.tinkertank.mdreader.repository.mysql;

import com.tinkertank.mdreader.model.ChapterMeta;
import com.tinkertank.mdreader.model.Sentence;
import com.tinkertank.mdreader.repository.ChapterRepository;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "mdreader.content.storage", havingValue = "mysql", matchIfMissing = true)
public class MysqlChapterRepository implements ChapterRepository {

    private static final RowMapper<ChapterMeta> CHAPTER_ROW_MAPPER = new RowMapper<ChapterMeta>() {
        @Override
        public ChapterMeta mapRow(ResultSet rs, int rowNum) throws SQLException {
            ChapterMeta chapter = new ChapterMeta();
            chapter.setId(rs.getString("id"));
            chapter.setBookId(rs.getString("book_id"));
            chapter.setTitle(rs.getString("title"));
            chapter.setSourceFile(rs.getString("source_file"));
            return chapter;
        }
    };

    private static final RowMapper<Sentence> SENTENCE_ROW_MAPPER = new RowMapper<Sentence>() {
        @Override
        public Sentence mapRow(ResultSet rs, int rowNum) throws SQLException {
            Sentence sentence = new Sentence();
            sentence.setId(rs.getInt("sentence_id"));
            sentence.setParagraphId(rs.getInt("paragraph_id"));
            sentence.setText(rs.getString("text"));
            sentence.setExplanation(rs.getString("explanation"));
            return sentence;
        }
    };

    private final JdbcTemplate jdbcTemplate;

    public MysqlChapterRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ChapterMeta> findAllChapters() {
        return jdbcTemplate.query(
                "SELECT id, book_id, title, source_file FROM chapters ORDER BY id",
                CHAPTER_ROW_MAPPER);
    }

    @Override
    public Optional<ChapterMeta> findChapter(String chapterId) {
        try {
            ChapterMeta chapter = jdbcTemplate.queryForObject(
                    "SELECT id, book_id, title, source_file FROM chapters WHERE id = ?",
                    new Object[]{chapterId},
                    CHAPTER_ROW_MAPPER);
            return Optional.ofNullable(chapter);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<Sentence> findSentences(String chapterId) {
        return jdbcTemplate.query(
                "SELECT sentence_id, paragraph_id, text, explanation FROM sentences "
                        + "WHERE chapter_id = ? ORDER BY sentence_id",
                new Object[]{chapterId},
                SENTENCE_ROW_MAPPER);
    }

    @Override
    public void saveChapter(ChapterMeta chapter) {
        jdbcTemplate.update(
                "INSERT INTO chapters (id, book_id, title, source_file) VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE book_id = VALUES(book_id), "
                        + "title = VALUES(title), source_file = VALUES(source_file)",
                chapter.getId(),
                chapter.getBookId(),
                chapter.getTitle(),
                chapter.getSourceFile());
    }

    @Override
    public void saveSentences(String chapterId, List<Sentence> sentences) {
        jdbcTemplate.update("DELETE FROM sentences WHERE chapter_id = ?", chapterId);
        if (sentences == null || sentences.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
                "INSERT INTO sentences (chapter_id, sentence_id, paragraph_id, text, explanation) "
                        + "VALUES (?, ?, ?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        Sentence sentence = sentences.get(i);
                        ps.setString(1, chapterId);
                        ps.setInt(2, sentence.getId());
                        ps.setInt(3, sentence.getParagraphId());
                        ps.setString(4, sentence.getText());
                        ps.setString(5, sentence.getExplanation());
                    }

                    @Override
                    public int getBatchSize() {
                        return sentences.size();
                    }
                });
    }

    @Override
    public void updateTitle(String chapterId, String title) {
        int updated = jdbcTemplate.update(
                "UPDATE chapters SET title = ? WHERE id = ?",
                title,
                chapterId);
        if (updated == 0) {
            throw new IllegalStateException("Chapter not found: " + chapterId);
        }
    }

    @Override
    public void deleteChapter(String chapterId) {
        jdbcTemplate.update("DELETE FROM chapters WHERE id = ?", chapterId);
    }
}
