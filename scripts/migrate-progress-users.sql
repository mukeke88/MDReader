USE mdreader;

-- Run this after importing an old dump such as scripts/mdreader_dbwithData.sql.
-- It keeps existing reading_progress rows under the default user and changes the
-- progress key from chapter_id to user_id + chapter_id.

SET @has_user_id = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'reading_progress'
      AND column_name = 'user_id'
);

SET @add_user_id_sql = IF(
    @has_user_id = 1,
    'SELECT ''reading_progress user_id already exists'' AS status',
    'ALTER TABLE reading_progress ADD COLUMN user_id VARCHAR(255) NOT NULL DEFAULT ''default'' FIRST'
);

PREPARE add_user_id_stmt FROM @add_user_id_sql;
EXECUTE add_user_id_stmt;
DEALLOCATE PREPARE add_user_id_stmt;

INSERT IGNORE INTO reading_progress (
    user_id,
    chapter_id,
    last_sentence_id,
    total_score,
    green_score,
    red_score,
    manual_red_score,
    global_expanded,
    opened_sentence_ids,
    read_sentence_ids,
    scored_sentence_ids,
    explanation_used_sentence_ids,
    created_at,
    updated_at
)
SELECT
    progress.user_id,
    chapters.id,
    progress.last_sentence_id,
    progress.total_score,
    progress.green_score,
    progress.red_score,
    progress.manual_red_score,
    progress.global_expanded,
    progress.opened_sentence_ids,
    progress.read_sentence_ids,
    progress.scored_sentence_ids,
    progress.explanation_used_sentence_ids,
    progress.created_at,
    progress.updated_at
FROM reading_progress progress
JOIN chapters ON chapters.title = progress.chapter_id
WHERE progress.user_id = 'default'
  AND progress.chapter_id <> chapters.id;

SET @current_primary_key = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'reading_progress'
      AND index_name = 'PRIMARY'
);

SET @migrate_primary_key_sql = IF(
    @current_primary_key = 'user_id,chapter_id',
    'SELECT ''reading_progress primary key already migrated'' AS status',
    'ALTER TABLE reading_progress DROP PRIMARY KEY, ADD PRIMARY KEY (user_id, chapter_id)'
);

PREPARE migrate_primary_key_stmt FROM @migrate_primary_key_sql;
EXECUTE migrate_primary_key_stmt;
DEALLOCATE PREPARE migrate_primary_key_stmt;
