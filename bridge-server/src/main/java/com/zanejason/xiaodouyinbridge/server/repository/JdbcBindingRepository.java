package com.zanejason.xiaodouyinbridge.server.repository;

import com.zanejason.xiaodouyinbridge.server.model.BindingRecord;
import com.zanejason.xiaodouyinbridge.server.service.BindingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcBindingRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcBindingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int deleteExpiredPending(Instant now) {
        return jdbcTemplate.update(
                "DELETE FROM xdb_pending_binding WHERE expires_at < ?",
                Timestamp.from(now)
        );
    }

    public int deletePendingByMinecraftUuid(String minecraftUuid) {
        return jdbcTemplate.update(
                "DELETE FROM xdb_pending_binding WHERE minecraft_uuid = ?",
                minecraftUuid
        );
    }

    public boolean tryInsertPending(BindingService.PendingBinding pending) {
        int affected = jdbcTemplate.update(
                "INSERT IGNORE INTO xdb_pending_binding(code, minecraft_uuid, minecraft_name, expires_at) VALUES (?, ?, ?, ?)",
                pending.code(),
                pending.minecraftUuid(),
                pending.minecraftName(),
                Timestamp.from(pending.expiresAt())
        );
        return affected == 1;
    }

    public Optional<BindingService.PendingBinding> findPendingByCode(String code) {
        List<BindingService.PendingBinding> rows = jdbcTemplate.query(
                "SELECT code, minecraft_uuid, minecraft_name, expires_at FROM xdb_pending_binding WHERE code = ?",
                (rs, rowNum) -> new BindingService.PendingBinding(
                        rs.getString("code"),
                        rs.getString("minecraft_uuid"),
                        rs.getString("minecraft_name"),
                        rs.getTimestamp("expires_at").toInstant()
                ),
                code
        );
        return rows.stream().findFirst();
    }

    public int deletePending(String code) {
        return jdbcTemplate.update("DELETE FROM xdb_pending_binding WHERE code = ?", code);
    }

    public Optional<BindingRecord> findByMinecraftUuid(String minecraftUuid) {
        List<BindingRecord> rows = jdbcTemplate.query(
                "SELECT minecraft_uuid, minecraft_name, douyin_open_id, douyin_nickname, fansclub_level, updated_at " +
                        "FROM xdb_binding WHERE minecraft_uuid = ?",
                this::mapBinding,
                minecraftUuid
        );
        return rows.stream().findFirst();
    }

    public Optional<BindingRecord> findByDouyinOpenId(String douyinOpenId) {
        List<BindingRecord> rows = jdbcTemplate.query(
                "SELECT minecraft_uuid, minecraft_name, douyin_open_id, douyin_nickname, fansclub_level, updated_at " +
                        "FROM xdb_binding WHERE douyin_open_id = ?",
                this::mapBinding,
                douyinOpenId
        );
        return rows.stream().findFirst();
    }

    public List<BindingRecord> findAll() {
        return jdbcTemplate.query(
                "SELECT minecraft_uuid, minecraft_name, douyin_open_id, douyin_nickname, fansclub_level, updated_at " +
                        "FROM xdb_binding ORDER BY updated_at DESC",
                this::mapBinding
        );
    }

    public int deleteByDouyinOpenIdExceptMinecraftUuid(String douyinOpenId, String minecraftUuid) {
        return jdbcTemplate.update(
                "DELETE FROM xdb_binding WHERE douyin_open_id = ? AND minecraft_uuid <> ?",
                douyinOpenId,
                minecraftUuid
        );
    }

    public void upsert(BindingRecord record) {
        jdbcTemplate.update(
                "INSERT INTO xdb_binding(" +
                        "minecraft_uuid, minecraft_name, douyin_open_id, douyin_nickname, fansclub_level, updated_at" +
                        ") VALUES (?, ?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "minecraft_name = VALUES(minecraft_name), " +
                        "douyin_open_id = VALUES(douyin_open_id), " +
                        "douyin_nickname = VALUES(douyin_nickname), " +
                        "fansclub_level = VALUES(fansclub_level), " +
                        "updated_at = VALUES(updated_at)",
                record.minecraftUuid(),
                record.minecraftName(),
                record.douyinOpenId(),
                record.douyinNickname(),
                record.fansClubLevel(),
                Timestamp.from(record.updatedAt())
        );
    }

    private BindingRecord mapBinding(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new BindingRecord(
                rs.getString("minecraft_uuid"),
                rs.getString("minecraft_name"),
                rs.getString("douyin_open_id"),
                rs.getString("douyin_nickname"),
                rs.getInt("fansclub_level"),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
