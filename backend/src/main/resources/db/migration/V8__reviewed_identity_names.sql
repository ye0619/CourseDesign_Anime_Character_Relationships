CREATE TABLE external_identity_name (
    identity_id BIGINT NOT NULL,
    ordinal INT NOT NULL,
    name VARCHAR(200) NOT NULL,
    search_key VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs NOT NULL,
    locale VARCHAR(20) NOT NULL,
    evidence_text VARCHAR(1000) NOT NULL,
    canonical_uid VARCHAR(200) NOT NULL,
    source_url VARCHAR(1000) NOT NULL,
    source_revision VARCHAR(100) NOT NULL,
    license_label VARCHAR(200) NOT NULL,
    review_version BIGINT NOT NULL,
    reviewed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY(identity_id,ordinal),
    CONSTRAINT fk_identity_name FOREIGN KEY(identity_id) REFERENCES external_identity(id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
