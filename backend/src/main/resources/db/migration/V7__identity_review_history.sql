ALTER TABLE external_identity
    MODIFY provider_id VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs NOT NULL,
    ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN evidence_text VARCHAR(2000) NULL,
    ADD COLUMN review_reason VARCHAR(500) NOT NULL DEFAULT '',
    ADD COLUMN reviewed_by BIGINT NULL,
    ADD INDEX idx_external_review(status,id);

CREATE TABLE external_identity_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    identity_id BIGINT NOT NULL,
    action VARCHAR(24) NOT NULL,
    actor_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    before_state JSON NULL,
    after_state JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_identity_event(identity_id,id),
    CONSTRAINT fk_identity_event FOREIGN KEY(identity_id) REFERENCES external_identity(id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
