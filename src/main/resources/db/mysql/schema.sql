CREATE TABLE IF NOT EXISTS reading_progress (
    chapter_id VARCHAR(255) NOT NULL,
    last_sentence_id INT NULL,
    total_score INT NOT NULL DEFAULT 0,
    green_score INT NOT NULL DEFAULT 0,
    red_score INT NOT NULL DEFAULT 0,
    manual_red_score INT NOT NULL DEFAULT 0,
    global_expanded BOOLEAN NOT NULL DEFAULT FALSE,
    opened_sentence_ids JSON NOT NULL,
    read_sentence_ids JSON NOT NULL,
    scored_sentence_ids JSON NOT NULL,
    explanation_used_sentence_ids JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (chapter_id)
);
