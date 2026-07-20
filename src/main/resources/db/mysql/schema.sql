CREATE TABLE IF NOT EXISTS chapters (
    id VARCHAR(255) NOT NULL,
    book_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    source_file VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS sentences (
    chapter_id VARCHAR(255) NOT NULL,
    sentence_id INT NOT NULL,
    paragraph_id INT NOT NULL,
    text TEXT NOT NULL,
    explanation TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (chapter_id, sentence_id),
    CONSTRAINT fk_sentences_chapter
        FOREIGN KEY (chapter_id) REFERENCES chapters (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS reading_progress (
    user_id VARCHAR(255) NOT NULL DEFAULT 'default',
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
    PRIMARY KEY (user_id, chapter_id)
);
