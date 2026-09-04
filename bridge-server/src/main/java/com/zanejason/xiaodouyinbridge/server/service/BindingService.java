package com.zanejason.xiaodouyinbridge.server.service;

import com.zanejason.xiaodouyinbridge.server.model.BindingRecord;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BindingService {
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingBinding> pendingByCode = new ConcurrentHashMap<>();
    private final Map<String, BindingRecord> bindingsByMinecraftUuid = new ConcurrentHashMap<>();

    public PendingBinding createRequest(String minecraftUuid, String minecraftName) {
        cleanupExpired();
        String code;
        do {
            code = String.format("%06d", random.nextInt(1_000_000));
        } while (pendingByCode.containsKey(code));

        PendingBinding pending = new PendingBinding(
                code,
                minecraftUuid,
                minecraftName,
                Instant.now().plus(CODE_TTL)
        );
        pendingByCode.put(code, pending);
        return pending;
    }

    public BindingRecord complete(String code, String douyinOpenId, String douyinNickname, int fansClubLevel) {
        cleanupExpired();
        PendingBinding pending = pendingByCode.remove(code);
        if (pending == null) {
            throw new IllegalArgumentException("绑定码不存在或已失效");
        }

        BindingRecord record = new BindingRecord(
                pending.minecraftUuid(),
                pending.minecraftName(),
                douyinOpenId,
                douyinNickname,
                Math.max(0, fansClubLevel),
                Instant.now()
        );
        bindingsByMinecraftUuid.put(record.minecraftUuid(), record);
        return record;
    }

    public Optional<BindingRecord> findByMinecraftUuid(String minecraftUuid) {
        return Optional.ofNullable(bindingsByMinecraftUuid.get(minecraftUuid));
    }

    public BindingRecord updateLevel(String minecraftUuid, int fansClubLevel) {
        BindingRecord old = bindingsByMinecraftUuid.get(minecraftUuid);
        if (old == null) {
            throw new IllegalArgumentException("Minecraft 玩家尚未绑定抖音账号");
        }

        BindingRecord updated = new BindingRecord(
                old.minecraftUuid(),
                old.minecraftName(),
                old.douyinOpenId(),
                old.douyinNickname(),
                Math.max(0, fansClubLevel),
                Instant.now()
        );
        bindingsByMinecraftUuid.put(minecraftUuid, updated);
        return updated;
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        pendingByCode.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    public record PendingBinding(
            String code,
            String minecraftUuid,
            String minecraftName,
            Instant expiresAt
    ) {
    }
}
