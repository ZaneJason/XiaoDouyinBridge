package com.zanejason.xiaodouyinbridge.server.service;

import com.zanejason.xiaodouyinbridge.server.model.BindingRecord;
import com.zanejason.xiaodouyinbridge.server.repository.JdbcBindingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

@Service
public class BindingService {
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
        repository.deletePendingByMinecraftUuid(minecraftUuid);

        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = String.format("%06d", random.nextInt(1_000_000));
            PendingBinding pending = new PendingBinding(
                    code,
                    minecraftUuid,
                    minecraftName,
                    Instant.now().plus(CODE_TTL)
            );
            if (repository.tryInsertPending(pending)) {
                return pending;
            }
        }

        throw new IllegalStateException("暂时无法生成绑定码，请稍后重试");
    }

    @Transactional
    public BindingRecord complete(String code, String douyinOpenId, String douyinNickname, int fansClubLevel) {
        cleanupExpired();

        PendingBinding pending = repository.findPendingByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("绑定码不存在或已失效"));

        if (douyinOpenId == null || douyinOpenId.isBlank()) {
            throw new IllegalArgumentException("抖音 openId 不能为空");
        }

        // 消费绑定码。若并发请求已经先消费，则本次绑定失败，避免一个码被重复使用。
        if (repository.deletePending(code) != 1) {
            throw new IllegalArgumentException("绑定码不存在或已失效");
        }

        // 一个抖音账号只允许绑定一个 Minecraft UUID；重新绑定时自动解除旧关系。
        repository.deleteByDouyinOpenIdExceptMinecraftUuid(douyinOpenId, pending.minecraftUuid());

        BindingRecord record = new BindingRecord(
                pending.minecraftUuid(),
                pending.minecraftName(),
                douyinOpenId,
                douyinNickname == null ? "" : douyinNickname,
                Math.max(0, fansClubLevel),
                Instant.now()
        );
        repository.upsert(record);
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
            return Optional.empty();
        }

        BindingRecord old = existing.get();
        String nickname = douyinNickname == null || douyinNickname.isBlank()
                ? old.douyinNickname()
                : douyinNickname;
        return Optional.of(replace(old, nickname, fansClubLevel));
    }

    private BindingRecord replace(BindingRecord old, String douyinNickname, int fansClubLevel) {
        BindingRecord updated = new BindingRecord(
                old.minecraftUuid(),
                old.minecraftName(),
                old.douyinOpenId(),
                douyinNickname,
                Math.max(0, fansClubLevel),
                Instant.now()
        );
        repository.upsert(updated);
        return updated;
    }

    private void cleanupExpired() {
        repository.deleteExpiredPending(Instant.now());
    }

    public record PendingBinding(
            String code,
            String minecraftUuid,
            String minecraftName,
            Instant expiresAt
    ) {
    }
}
