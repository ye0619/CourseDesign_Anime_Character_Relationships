CREATE TABLE external_identity (
    provider VARCHAR(24) NOT NULL,
    provider_id VARCHAR(150) NOT NULL,
    entity_type VARCHAR(24) NOT NULL,
    canonical_uid VARCHAR(200) NOT NULL,
    status VARCHAR(24) NOT NULL,
    source_url VARCHAR(1000) NOT NULL,
    source_revision VARCHAR(100) NULL,
    match_method VARCHAR(100) NOT NULL,
    license_label VARCHAR(200) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY(provider,entity_type,provider_id),
    INDEX idx_external_canonical(canonical_uid)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
