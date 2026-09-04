package com.zanejason.xiaodouyinbridge.server.service;

import com.zanejason.xiaodouyinbridge.server.model.BindingRecord;
import com.zanejason.xiaodouyinbridge.server.repository.JdbcBindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

@Service
public class BindingService {
    private static final Logger log = LoggerFactory.getLogger(BindingService.class);
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final int MAX_CODE_ATTEMPTS = 20;

    private final SecureRandom random = new SecureRandom();
    private final JdbcBindingRepository repository;

    public BindingService(JdbcBindingRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PendingBinding createRequest(String minecraftUuid, String minecraftName) {
        cleanupExpired();
        int replaced = repository.deletePendingByMinecraftUuid(minecraftUuid);
        if (replaced > 0) {
            log.info("[BIND] Replaced previous pending code: mc={} uuid={}", minecraftName, shortId(minecraftUuid));
        }

        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = String.format("%06d", random.nextInt(1_000_000));
            PendingBinding pending = new PendingBinding(
                    code,
                    minecraftUuid,
                    minecraftName,
                    Instant.now().plus(CODE_TTL)
            );
            if (repository.tryInsertPending(pending)) {
                log.info("[BIND] Created code: mc={} uuid={} code={} expiresAt={}",
                        minecraftName, shortId(minecraftUuid), code, pending.expiresAt());
                return pending;
            }
        }

        log.error("[BIND] Failed to generate unique binding code after {} attempts: mc={} uuid={}",
                MAX_CODE_ATTEMPTS, minecraftName, shortId(minecraftUuid));
        throw new IllegalStateException("暂时无法生成绑定码，请稍后重试");
    }

    @Transactional
    public BindingRecord complete(String code, String douyinOpenId, String douyinNickname, int fansClubLevel) {
        cleanupExpired();

        PendingBinding pending = repository.findPendingByCode(code)
                .orElseThrow(() -> {
                    log.warn("[BIND] Invalid or expired binding code received: code={}", code);
                    return new IllegalArgumentException("绑定码不存在或已失效");
                });

        if (douyinOpenId == null || douyinOpenId.isBlank()) {
            log.warn("[BIND] Refused binding without Douyin openId: code={} mc={}", code, pending.minecraftName());
            throw new IllegalArgumentException("抖音 openId 不能为空");
        }

        if (repository.deletePending(code) != 1) {
            log.warn("[BIND] Binding code was already consumed concurrently: code={} mc={}", code, pending.minecraftName());
            throw new IllegalArgumentException("绑定码不存在或已失效");
        }

        int removedOldBindings = repository.deleteByDouyinOpenIdExceptMinecraftUuid(
                douyinOpenId, pending.minecraftUuid());
        if (removedOldBindings > 0) {
            log.info("[BIND] Douyin account was rebound to another MC account: douyin={} removedOld={}",
                    shortId(douyinOpenId), removedOldBindings);
        }

        BindingRecord record = new BindingRecord(
                pending.minecraftUuid(),
                pending.minecraftName(),
                douyinOpenId,
                douyinNickname == null ? "" : douyinNickname,
                Math.max(0, fansClubLevel),
                Instant.now()
        );
        repository.upsert(record);

        log.info("[BIND] Binding completed: mc={} uuid={} douyin={} nickname={} fansClubLevel={}",
                record.minecraftName(), shortId(record.minecraftUuid()), shortId(record.douyinOpenId()),
                safeNickname(record.douyinNickname()), record.fansClubLevel());
        return record;
    }

    public Optional<BindingRecord> findByMinecraftUuid(String minecraftUuid) {
        return repository.findByMinecraftUuid(minecraftUuid);
    }

    public Optional<BindingRecord> findByDouyinOpenId(String douyinOpenId) {
        if (douyinOpenId == null || douyinOpenId.isBlank()) {
            return Optional.empty();
        }
        return repository.findByDouyinOpenId(douyinOpenId);
    }

    public Collection<BindingRecord> allBindings() {
        return repository.findAll();
    }

    @Transactional
    public BindingRecord updateLevel(String minecraftUuid, int fansClubLevel) {
        BindingRecord old = repository.findByMinecraftUuid(minecraftUuid)
                .orElseThrow(() -> new IllegalArgumentException("Minecraft 玩家尚未绑定抖音账号"));
        return replace(old, old.douyinNickname(), fansClubLevel);
    }

    @Transactional
    public Optional<BindingRecord> updateLevelByDouyinOpenId(
            String douyinOpenId,
            String douyinNickname,
            int fansClubLevel) {
        Optional<BindingRecord> existing = findByDouyinOpenId(douyinOpenId);
        if (existing.isEmpty()) {
            log.info("[FANSCLUB] Event belongs to unbound Douyin account: douyin={} nickname={} level={}",
                    shortId(douyinOpenId), safeNickname(douyinNickname), Math.max(0, fansClubLevel));
            return Optional.empty();
        }

        BindingRecord old = existing.get();
        String nickname = douyinNickname == null || douyinNickname.isBlank()
                ? old.douyinNickname()
                : douyinNickname;
        return Optional.of(replace(old, nickname, fansClubLevel));
    }

    private BindingRecord replace(BindingRecord old, String douyinNickname, int fansClubLevel) {
        int newLevel = Math.max(0, fansClubLevel);
        BindingRecord updated = new BindingRecord(
                old.minecraftUuid(),
                old.minecraftName(),
                old.douyinOpenId(),
                douyinNickname,
                newLevel,
                Instant.now()
        );
        repository.upsert(updated);

        if (old.fansClubLevel() != newLevel) {
            log.info("[FANSCLUB] Level changed: mc={} douyin={} {} -> {}",
                    old.minecraftName(), shortId(old.douyinOpenId()), old.fansClubLevel(), newLevel);
        } else {
            log.debug("[FANSCLUB] Level refreshed without change: mc={} douyin={} level={}",
                    old.minecraftName(), shortId(old.douyinOpenId()), newLevel);
        }
        return updated;
    }

    private void cleanupExpired() {
        int deleted = repository.deleteExpiredPending(Instant.now());
        if (deleted > 0) {
            log.info("[BIND] Cleaned {} expired pending binding code(s)", deleted);
        }
    }

    private static String shortId(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        if (value.length() <= 10) {
            return value;
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }

    private static String safeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return "<empty>";
        }
        return nickname.replace('\n', ' ').replace('\r', ' ');
    }

    public record PendingBinding(
            String code,
            String minecraftUuid,
            String minecraftName,
            Instant expiresAt
    ) {
    }
}
