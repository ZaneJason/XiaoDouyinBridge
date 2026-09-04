package com.zanejason.xiaodouyinbridge.server.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DouyinLiveSessionService {
    private final DouyinApiClient apiClient;
    private final ConcurrentHashMap<String, LiveSession> sessionsByRoomId = new ConcurrentHashMap<>();

    public DouyinLiveSessionService(DouyinApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public LiveSession startFromLaunchToken(String launchToken) {
        DouyinApiClient.LiveSessionInfo info = apiClient.getLiveInfo(launchToken);
        return start(info.roomId(), info.anchorOpenId(), info.anchorNickname());
    }

    public LiveSession start(String roomId, String anchorOpenId, String anchorNickname) {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("roomId 不能为空");
        }
        if (anchorOpenId == null || anchorOpenId.isBlank()) {
            throw new IllegalArgumentException("anchorOpenId 不能为空");
        }

        String fansClubTaskId = apiClient.startLiveDataTask(roomId, "live_fansclub");
        String commentTaskId = apiClient.startLiveDataTask(roomId, "live_comment");

        LiveSession session = new LiveSession(
                roomId,
                anchorOpenId,
                anchorNickname == null ? "" : anchorNickname,
                fansClubTaskId,
                commentTaskId,
                Instant.now()
        );
        sessionsByRoomId.put(roomId, session);
        return session;
    }

    public Optional<LiveSession> find(String roomId) {
        return Optional.ofNullable(sessionsByRoomId.get(roomId));
    }

    public Collection<LiveSession> all() {
        return java.util.List.copyOf(sessionsByRoomId.values());
    }

    public record LiveSession(
            String roomId,
            String anchorOpenId,
            String anchorNickname,
            String fansClubTaskId,
            String commentTaskId,
            Instant startedAt
    ) {}
}
