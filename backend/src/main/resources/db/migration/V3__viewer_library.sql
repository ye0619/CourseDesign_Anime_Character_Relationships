CREATE TABLE viewer_binding (
  id CHAR(64) PRIMARY KEY,
  bangumi_id BIGINT NOT NULL,
  username VARCHAR(64) NOT NULL,
  nickname VARCHAR(200) NOT NULL,
  avatar VARCHAR(1000),
  binding_mode VARCHAR(16) NOT NULL,
  revision CHAR(36) NOT NULL,
  sync_status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
  sync_processed INT NOT NULL DEFAULT 0,
  remote_total INT NOT NULL DEFAULT 0,
  truncated BOOLEAN NOT NULL DEFAULT FALSE,
  sync_error VARCHAR(500),
  last_synced_at TIMESTAMP(6) NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE viewer_collection (
  viewer_id CHAR(64) NOT NULL,
  subject_id BIGINT NOT NULL,
  status INT NOT NULL,
  personal_rating INT NOT NULL DEFAULT 0,
  title VARCHAR(1000) NOT NULL,
  original_name VARCHAR(1000) NOT NULL,
  image_url VARCHAR(1000),
  release_date VARCHAR(20),
  episodes INT NOT NULL DEFAULT 0,
  watched_episodes INT NOT NULL DEFAULT 0,
  community_score DOUBLE NOT NULL DEFAULT 0,
  updated_at VARCHAR(50),
  personal_tags JSON NOT NULL,
  community_tags JSON NOT NULL,
  PRIMARY KEY (viewer_id, subject_id),
  INDEX idx_viewer_status (viewer_id,status),
  CONSTRAINT fk_viewer_collection FOREIGN KEY (viewer_id) REFERENCES viewer_binding(id) ON DELETE CASCADE
);
