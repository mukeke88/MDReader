USE mdreader;

-- Adds an explicit document_id to reading_progress while preserving chapter_id
-- as the existing lookup key. Run this on existing MySQL deployments.

SET @has_document_id = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'reading_progress'
      AND column_name = 'document_id'
);

SET @add_document_id_sql = IF(
    @has_document_id = 1,
    'SELECT ''reading_progress document_id already exists'' AS status',
    'ALTER TABLE reading_progress ADD COLUMN document_id VARCHAR(255) NULL AFTER chapter_id'
);

PREPARE add_document_id_stmt FROM @add_document_id_sql;
EXECUTE add_document_id_stmt;
DEALLOCATE PREPARE add_document_id_stmt;

UPDATE reading_progress
SET document_id = chapter_id
WHERE document_id IS NULL OR document_id = '';

ALTER TABLE reading_progress MODIFY COLUMN document_id VARCHAR(255) NOT NULL;

SET @has_document_index = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'reading_progress'
      AND index_name = 'idx_reading_progress_document'
);

SET @add_document_index_sql = IF(
    @has_document_index = 1,
    'SELECT ''reading_progress document index already exists'' AS status',
    'CREATE INDEX idx_reading_progress_document ON reading_progress (document_id)'
);

PREPARE add_document_index_stmt FROM @add_document_index_sql;
EXECUTE add_document_index_stmt;
DEALLOCATE PREPARE add_document_index_stmt;
