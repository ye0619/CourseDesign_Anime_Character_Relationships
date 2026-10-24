CREATE TABLE viewer_report (
    viewer_id CHAR(64) NOT NULL PRIMARY KEY,
    binding_revision VARCHAR(64) NOT NULL,
    collection_time TIMESTAMP(6) NULL,
    payload LONGTEXT NOT NULL,
    generated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_viewer_report FOREIGN KEY(viewer_id) REFERENCES viewer_binding(id) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE TABLE recommendation_candidate (
    subject_id BIGINT NOT NULL PRIMARY KEY,
    payload LONGTEXT NOT NULL,
    fetched_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE TABLE viewer_recommendation_feedback (
    viewer_id CHAR(64) NOT NULL,
    subject_id BIGINT NOT NULL,
    feedback_type VARCHAR(24) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY(viewer_id,subject_id),
    CONSTRAINT fk_viewer_feedback FOREIGN KEY(viewer_id) REFERENCES viewer_binding(id) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

