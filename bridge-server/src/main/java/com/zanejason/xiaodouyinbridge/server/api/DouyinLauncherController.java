package com.zanejason.xiaodouyinbridge.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.zanejason.xiaodouyinbridge.server.service.DouyinLiveEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/douyin/launcher")
public class DouyinLauncherController {
    private static final Logger log = LoggerFactory.getLogger(DouyinLauncherController.class);

    private final DouyinLiveEventService liveEventService;
    private final String launcherKey;

    public DouyinLauncherController(
            DouyinLiveEventService liveEventService,
            @Value("${bridge.launcher-key}") String launcherKey) {
        this.liveEventService = liveEventService;
        this.launcherKey = launcherKey;
    }

    @GetMapping("/health")
    public ResponseEntity<?> health(
            @RequestHeader(value = "X-Launcher-Key", required = false) String key) {
        if (!authorized(key)) {
            log.warn("[LAUNCHER-API] Unauthorized health request");
            return unauthorized();
        }
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "service", "XiaoDouyinBridge",
                "time", Instant.now().toString()
        ));
    }

    /**
     * 接收直播伴侣 PipeSDK 的 EVENT_MESSAGE JSON。
     * 仅消费 eventName=OPEN_LIVE_DATA；其它 PipeSDK 消息会安全忽略。
     */
    @PostMapping("/event")
    public ResponseEntity<?> event(
            @RequestHeader(value = "X-Launcher-Key", required = false) String key,
            @RequestBody JsonNode message) {
        if (!authorized(key)) {
            log.warn("[LAUNCHER-API] Unauthorized event request");
            return unauthorized();
        }

        String type = message.path("type").asText("");
        String eventName = message.path("eventName").asText("");
        if (!"event".equals(type) || !"OPEN_LIVE_DATA".equals(eventName)) {
            log.debug("[LAUNCHER-API] Ignored PipeSDK message: type={} eventName={}", type, eventName);
            return ResponseEntity.ok(Map.of("ok", true, "ignored", true));
        }

        JsonNode payload = message.path("params").path("payload");
        if (!payload.isArray()) {
            log.warn("[LAUNCHER-API] OPEN_LIVE_DATA missing params.payload array");
            return ResponseEntity.badRequest().body(Map.of("error", "params.payload must be an array"));
        }

        try {
            DouyinLiveEventService.ProcessSummary summary = liveEventService.processPayload(
                    "LIVE_COMPANION", null, payload);
            log.info("[LAUNCHER-API] OPEN_LIVE_DATA accepted: events={} comments={} fansclub={} bindings={}",
                    summary.events(), summary.comments(), summary.fansClubEvents(), summary.bindings());
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "events", summary.events(),
                    "comments", summary.comments(),
                    "fansClubEvents", summary.fansClubEvents(),
                    "bindings", summary.bindings()
            ));
        } catch (Exception e) {
            log.error("[LAUNCHER-API] OPEN_LIVE_DATA processing failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "launcher event processing failed"));
        }
    }

    private boolean authorized(String key) {
        return key != null && launcherKey.equals(key);
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthorized"));
    }
}
