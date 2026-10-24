-- Forward-only initial schema. A rollback requires restoring a backup or a new migration.
CREATE TABLE sys_user (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 username VARCHAR(50) NOT NULL,
 password_hash VARCHAR(100) NOT NULL,
 display_name VARCHAR(100) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 auth_version INT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_user_username(username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE sys_role (id BIGINT PRIMARY KEY AUTO_INCREMENT, code VARCHAR(20) NOT NULL UNIQUE);
CREATE TABLE sys_user_role (
 user_id BIGINT NOT NULL, role_id BIGINT NOT NULL,
 PRIMARY KEY(user_id,role_id),
 FOREIGN KEY(user_id) REFERENCES sys_user(id), FOREIGN KEY(role_id) REFERENCES sys_role(id)
);
CREATE TABLE import_task (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 source VARCHAR(30) NOT NULL, file_hash CHAR(64) NOT NULL, payload_file VARCHAR(100) NOT NULL,
 schema_version INT NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
 total_count INT NOT NULL, success_count INT NOT NULL DEFAULT 0, failed_count INT NOT NULL DEFAULT 0,
 checkpoint INT NOT NULL DEFAULT 0, retry_of BIGINT NULL, created_by BIGINT NULL,
 started_at TIMESTAMP(6) NULL, finished_at TIMESTAMP(6) NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), error_summary VARCHAR(1000) NULL,
 active_hash CHAR(64) GENERATED ALWAYS AS (CASE WHEN status IN ('PENDING','RUNNING') THEN file_hash ELSE NULL END) STORED,
 UNIQUE KEY uk_import_active_file(active_hash), KEY idx_import_created(created_at),
 FOREIGN KEY(retry_of) REFERENCES import_task(id), FOREIGN KEY(created_by) REFERENCES sys_user(id)
);
CREATE TABLE import_record (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, task_id BIGINT NOT NULL,
 record_key VARCHAR(100) NOT NULL, record_type VARCHAR(40) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING', attempts INT NOT NULL DEFAULT 0,
 error_code VARCHAR(60) NULL, error_message VARCHAR(1000) NULL,
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_import_record(task_id,record_key), KEY idx_record_status(task_id,status),
 FOREIGN KEY(task_id) REFERENCES import_task(id)
);
CREATE TABLE operation_log (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, request_id VARCHAR(50) NULL, actor_id BIGINT NULL,
 action VARCHAR(60) NOT NULL, target_type VARCHAR(40) NULL, target_uid VARCHAR(250) NULL,
 result VARCHAR(20) NOT NULL, summary VARCHAR(1000) NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY idx_log_created(created_at), FOREIGN KEY(actor_id) REFERENCES sys_user(id)
);
