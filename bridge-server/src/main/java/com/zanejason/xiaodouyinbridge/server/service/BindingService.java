package com.zanejason.xiaodouyinbridge.server.service;

import com.zanejason.xiaodouyinbridge.server.model.BindingRecord;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BindingService {
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingBinding> pendingByCode = new ConcurrentHashMap<>();
    private final Map<String, BindingRecord> bindingsByMinecraftUuid = new ConcurrentHashMap<>();
    private final Map<String, String> minecraftUuidByDouyinOpenId = new ConcurrentHashMap<>();

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

    public synchronized BindingRecord complete(String code, String douyinOpenId, String douyinNickname, int fansClubLevel) {
        cleanupExpired();
        PendingBinding pending = pendingByCode.remove(code);
        if (pending == null) {
            throw new IllegalArgumentException("绑定码不存在或已失效");
        }
        if (douyinOpenId == null || douyinOpenId.isBlank()) {
            throw new IllegalArgumentException("抖音 openId 不能为空");
        }

        String previousMinecraftUuid = minecraftUuidByDouyinOpenId.get(douyinOpenId);
        if (previousMinecraftUuid != null && !previousMinecraftUuid.equals(pending.minecraftUuid())) {
            BindingRecord previous = bindingsByMinecraftUuid.remove(previousMinecraftUuid);
            if (previous != null) {
                minecraftUuidByDouyinOpenId.remove(previous.douyinOpenId());
            }
        }

        BindingRecord oldForMinecraft = bindingsByMinecraftUuid.get(pending.minecraftUuid());
        if (oldForMinecraft != null) {
            minecraftUuidByDouyinOpenId.remove(oldForMinecraft.douyinOpenId());
        }

        BindingRecord record = new BindingRecord(
                pending.minecraftUuid(),
                pending.minecraftName(),
                douyinOpenId,
                douyinNickname == null ? "" : douyinNickname,
                Math.max(0, fansClubLevel),
                Instant.now()
        );
        bindingsByMinecraftUuid.put(record.minecraftUuid(), record);
        minecraftUuidByDouyinOpenId.put(record.douyinOpenId(), record.minecraftUuid());
        return record;
    }

    public Optional<BindingRecord> findByMinecraftUuid(String minecraftUuid) {
        return Optional.ofNullable(bindingsByMinecraftUuid.get(minecraftUuid));
    }

    public Optional<BindingRecord> findByDouyinOpenId(String douyinOpenId) {
        String minecraftUuid = minecraftUuidByDouyinOpenId.get(douyinOpenId);
        return minecraftUuid == null ? Optional.empty() : findByMinecraftUuid(minecraftUuid);
    }

    public Collection<BindingRecord> allBindings() {
        return ListCopy.copyOf(bindingsByMinecraftUuid.values());
    }

    public BindingRecord updateLevel(String minecraftUuid, int fansClubLevel) {
        BindingRecord old = bindingsByMinecraftUuid.get(minecraftUuid);
        if (old == null) {
            throw new IllegalArgumentException("Minecraft 玩家尚未绑定抖音账号");
        }
        return replace(old, old.douyinNickname(), fansClubLevel);
    }

    public Optional<BindingRecord> updateLevelByDouyinOpenId(String douyinOpenId, String douyinNickname, int fansClubLevel) {
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
        bindingsByMinecraftUuid.put(old.minecraftUuid(), updated);
        minecraftUuidByDouyinOpenId.put(old.douyinOpenId(), old.minecraftUuid());
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

    private static final class ListCopy {
        private ListCopy() {}

        static <T> Collection<T> copyOf(Collection<T> values) {
            return java.util.List.copyOf(values);
        }
    }
}
