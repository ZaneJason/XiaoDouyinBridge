CREATE TABLE IF NOT EXISTS xdb_pending_binding (
    code VARCHAR(6) NOT NULL,
    minecraft_uuid VARCHAR(64) NOT NULL,
    minecraft_name VARCHAR(64) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    PRIMARY KEY (code),
    KEY idx_xdb_pending_minecraft_uuid (minecraft_uuid),
    KEY idx_xdb_pending_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xdb_binding (
    minecraft_uuid VARCHAR(64) NOT NULL,
    minecraft_name VARCHAR(64) NOT NULL,
    douyin_open_id VARCHAR(255) NOT NULL,
    douyin_nickname VARCHAR(255) NOT NULL DEFAULT '',
    fansclub_level INT NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (minecraft_uuid),
    UNIQUE KEY uk_xdb_binding_douyin_open_id (douyin_open_id),
    KEY idx_xdb_binding_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
