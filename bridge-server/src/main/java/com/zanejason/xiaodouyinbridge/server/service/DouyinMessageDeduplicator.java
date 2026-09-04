package com.zanejason.xiaodouyinbridge.server.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DouyinMessageDeduplicator {
    private static final Duration KEEP = Duration.ofHours(24);
    private final Map<String, Instant> seen = new ConcurrentHashMap<>();

    public boolean firstSeen(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return true;
        }
        if (seen.size() > 20_000) {
            cleanup();
        }
        return seen.putIfAbsent(messageId, Instant.now()) == null;
    }

    private void cleanup() {
        Instant cutoff = Instant.now().minus(KEEP);
        seen.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }
}
