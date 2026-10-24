CREATE TABLE public_credit_snapshot (
    subject_id BIGINT NOT NULL,
    kind VARCHAR(8) NOT NULL,
    payload LONGTEXT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (subject_id, kind)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE public_metadata_task (
    id CHAR(36) NOT NULL PRIMARY KEY,
    subject_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    processed INT NOT NULL DEFAULT 0,
    error_message VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    finished_at TIMESTAMP(6) NULL,
    INDEX idx_metadata_subject (subject_id, created_at)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
