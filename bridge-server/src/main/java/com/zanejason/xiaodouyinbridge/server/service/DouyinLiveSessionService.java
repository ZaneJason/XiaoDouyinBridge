package com.zanejason.xiaodouyinbridge.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DouyinLiveSessionService {
    private static final Logger log = LoggerFactory.getLogger(DouyinLiveSessionService.class);

    private final DouyinApiClient apiClient;
    private final ConcurrentHashMap<String, LiveSession> sessionsByRoomId = new ConcurrentHashMap<>();

    public DouyinLiveSessionService(DouyinApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public LiveSession startFromLaunchToken(String launchToken) {
        if (launchToken == null || launchToken.isBlank()) {
            throw new IllegalArgumentException("launchToken 不能为空");
        }
        log.info("[DOUYIN-SESSION] Resolving live room from launch token");
        DouyinApiClient.LiveSessionInfo info = apiClient.getLiveInfo(launchToken);
        log.info("[DOUYIN-SESSION] Live room resolved: roomId={} anchor={} anchorOpenId={}",
                info.roomId(), safe(info.anchorNickname()), shortId(info.anchorOpenId()));
        return start(info.roomId(), info.anchorOpenId(), info.anchorNickname());
    }

    public LiveSession start(String roomId, String anchorOpenId, String anchorNickname) {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("roomId 不能为空");
        }
        if (anchorOpenId == null || anchorOpenId.isBlank()) {
            throw new IllegalArgumentException("anchorOpenId 不能为空");
        }

        log.info("[DOUYIN-SESSION] Starting live data tasks: roomId={} anchor={}", roomId, safe(anchorNickname));
        String fansClubTaskId = apiClient.startLiveDataTask(roomId, "live_fansclub");
        log.info("[DOUYIN-SESSION] live_fansclub task started: roomId={} taskId={}", roomId, fansClubTaskId);

        String commentTaskId = apiClient.startLiveDataTask(roomId, "live_comment");
        log.info("[DOUYIN-SESSION] live_comment task started: roomId={} taskId={}", roomId, commentTaskId);

        LiveSession session = new LiveSession(
                roomId,
                anchorOpenId,
                anchorNickname == null ? "" : anchorNickname,
                fansClubTaskId,
                commentTaskId,
                Instant.now()
        );
        sessionsByRoomId.put(roomId, session);
        log.info("[DOUYIN-SESSION] Session READY: roomId={} anchor={} fansclubTask={} commentTask={}",
                roomId, safe(anchorNickname), fansClubTaskId, commentTaskId);
        return session;
    }

    public Optional<LiveSession> find(String roomId) {
        return Optional.ofNullable(sessionsByRoomId.get(roomId));
    }

    public Collection<LiveSession> all() {
        return java.util.List.copyOf(sessionsByRoomId.values());
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

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        return value.replace('\n', ' ').replace('\r', ' ');
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
