CREATE TABLE saved_anime (
    source VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    payload LONGTEXT NULL,
    previous_payload LONGTEXT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    error_message VARCHAR(500) NULL,
    updated_at TIMESTAMP(6) NULL,
    requested_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (source, source_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
