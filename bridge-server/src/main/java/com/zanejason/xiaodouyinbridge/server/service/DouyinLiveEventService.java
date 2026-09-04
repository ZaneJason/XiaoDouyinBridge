package com.zanejason.xiaodouyinbridge.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zanejason.xiaodouyinbridge.server.model.BindingRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DouyinLiveEventService {
    private static final Logger log = LoggerFactory.getLogger(DouyinLiveEventService.class);
    private static final Pattern BIND_PATTERN = Pattern.compile("^(?:绑定|bind)\\s*([0-9]{6})$", Pattern.CASE_INSENSITIVE);

    private final BindingService bindingService;
    private final DouyinMessageDeduplicator deduplicator;
    private final DouyinLiveSessionService liveSessionService;
    private final DouyinApiClient apiClient;

    public DouyinLiveEventService(
            BindingService bindingService,
            DouyinMessageDeduplicator deduplicator,
            DouyinLiveSessionService liveSessionService,
            DouyinApiClient apiClient) {
        this.bindingService = bindingService;
        this.deduplicator = deduplicator;
        this.liveSessionService = liveSessionService;
        this.apiClient = apiClient;
    }

    public ProcessSummary processPayload(String source, String roomId, JsonNode payload) {
        return processPayload(source, roomId, "", payload);
    }

    public ProcessSummary processPayload(String source, String roomId, String defaultMessageType, JsonNode payload) {
        if (payload == null || !payload.isArray()) {
            throw new IllegalArgumentException("payload must be an array");
        }

        int comments = 0;
        int fansClubEvents = 0;
        int bindings = 0;
        int ignored = 0;

        for (JsonNode event : payload) {
            String msgId = event.path("msg_id").asText("");
            if (!deduplicator.firstSeen(msgId)) {
                log.debug("[DOUYIN-EVENT] Duplicate ignored: source={} msgId={}", source, msgId);
                ignored++;
                continue;
            }

            String type = event.path("msg_type_str").asText("");
            if (type.isBlank()) {
                type = defaultMessageType == null ? "" : defaultMessageType;
            }

            switch (type) {
                case "live_comment" -> {
                    comments++;
                    if (handleComment(source, roomId, event)) {
                        bindings++;
                    }
                }
                case "live_fansclub" -> {
                    fansClubEvents++;
                    handleFansClub(source, event);
                }
                default -> ignored++;
            }
        }

        ProcessSummary summary = new ProcessSummary(payload.size(), comments, fansClubEvents, bindings, ignored);
        log.info("[DOUYIN-EVENT] Batch processed: source={} events={} comments={} fansclub={} bindings={} ignored={}",
                source, summary.events(), summary.comments(), summary.fansClubEvents(), summary.bindings(), summary.ignored());
        return summary;
    }

    private boolean handleComment(String source, String roomId, JsonNode event) {
        String content = event.path("content").asText("").trim();
        Matcher matcher = BIND_PATTERN.matcher(content);
        if (!matcher.matches()) {
            return false;
        }

        String code = matcher.group(1);
        String openId = openId(event);
        String nickname = event.path("nickname").asText("");
        int eventLevel = Math.max(0, event.path("fansclub_level").asInt(0));

        log.info("[DOUYIN-COMMENT] Bind command: source={} nickname={} douyin={} code={} level={}",
                source, safe(nickname), shortId(openId), code, eventLevel);

        try {
            BindingRecord record = bindingService.complete(code, openId, nickname, eventLevel);
            log.info("[DOUYIN-COMMENT] Binding SUCCESS: source={} mc={} douyin={} nickname={} level={}",
                    source, record.minecraftName(), shortId(openId), safe(nickname), eventLevel);

            if (roomId != null && !roomId.isBlank()) {
                CompletableFuture.runAsync(() -> refreshFanInfo(roomId, openId, nickname));
            }
            return true;
        } catch (IllegalArgumentException e) {
            log.info("[DOUYIN-COMMENT] Binding ignored: source={} nickname={} douyin={} code={} reason={}",
                    source, safe(nickname), shortId(openId), code, e.getMessage());
            return false;
        }
    }

    private void handleFansClub(String source, JsonNode event) {
        String openId = openId(event);
        String nickname = event.path("nickname").asText("");
        int reasonType = event.path("fansclub_reason_type").asInt(0);
        int level = reasonType == 16 ? 0 : Math.max(0, event.path("fansclub_level").asInt(0));

        log.info("[DOUYIN-FANSCLUB] Event: source={} douyin={} nickname={} reason={} level={}",
                source, shortId(openId), safe(nickname), reasonType, level);

        bindingService.updateLevelByDouyinOpenId(openId, nickname, level)
                .ifPresent(record -> log.info(
                        "[DOUYIN-FANSCLUB] MC target: source={} mc={} reason={} level={}",
                        source, record.minecraftName(), reasonType, level));
    }

    private void refreshFanInfo(String roomId, String openId, String nickname) {
        try {
            DouyinLiveSessionService.LiveSession session = liveSessionService.find(roomId).orElse(null);
            if (session == null) {
                log.debug("[DOUYIN-FANSCLUB] No live-session metadata for room={}", roomId);
                return;
            }

            OptionalInt levelLayer = apiClient.getFansClubLevelLayer(roomId, session.anchorOpenId(), openId);
            if (levelLayer.isEmpty()) {
                return;
            }

            int level = levelLayer.getAsInt();
            bindingService.updateLevelByDouyinOpenId(openId, nickname, level);
            log.info("[DOUYIN-FANSCLUB] Detail query refreshed: room={} douyin={} level={}",
                    roomId, shortId(openId), level);
        } catch (Exception e) {
            log.warn("[DOUYIN-FANSCLUB] Detail refresh FAILED: room={} douyin={} reason={}",
                    roomId, shortId(openId), e.getMessage());
        }
    }

    private String openId(JsonNode event) {
        String value = event.path("sec_open_id").asText("");
        if (value.isBlank()) {
            value = event.path("sec_openid").asText("");
        }
        return value;
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

    public record ProcessSummary(
            int events,
            int comments,
            int fansClubEvents,
            int bindings,
            int ignored) {
    }
}
