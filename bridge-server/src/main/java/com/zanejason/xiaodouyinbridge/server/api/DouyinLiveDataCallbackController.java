package com.zanejason.xiaodouyinbridge.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zanejason.xiaodouyinbridge.server.model.BindingRecord;
import com.zanejason.xiaodouyinbridge.server.service.BindingService;
import com.zanejason.xiaodouyinbridge.server.service.DouyinApiClient;
import com.zanejason.xiaodouyinbridge.server.service.DouyinLiveSessionService;
import com.zanejason.xiaodouyinbridge.server.service.DouyinMessageDeduplicator;
import com.zanejason.xiaodouyinbridge.server.service.DouyinSignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/douyin/live-data/callback")
public class DouyinLiveDataCallbackController {
    private static final Logger log = LoggerFactory.getLogger(DouyinLiveDataCallbackController.class);
    private static final Pattern BIND_PATTERN = Pattern.compile("^(?:绑定|bind)\\s*([0-9]{6})$", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;
    private final BindingService bindingService;
    private final DouyinSignatureService signatureService;
    private final DouyinMessageDeduplicator deduplicator;
    private final DouyinLiveSessionService liveSessionService;
    private final DouyinApiClient apiClient;
    private final String dataSecret;

    public DouyinLiveDataCallbackController(
            ObjectMapper objectMapper,
            BindingService bindingService,
            DouyinSignatureService signatureService,
            DouyinMessageDeduplicator deduplicator,
            DouyinLiveSessionService liveSessionService,
            DouyinApiClient apiClient,
            @Value("${douyin.data-secret:default}") String dataSecret) {
        this.objectMapper = objectMapper;
        this.bindingService = bindingService;
        this.signatureService = signatureService;
        this.deduplicator = deduplicator;
        this.liveSessionService = liveSessionService;
        this.apiClient = apiClient;
        this.dataSecret = dataSecret;
    }

    /** 抖音开放平台自测工具会先用 HEAD 检查回调域名是否可用。 */
    @RequestMapping(method = RequestMethod.HEAD)
    public ResponseEntity<Void> head() {
        log.info("[DOUYIN-CALLBACK] HEAD self-test reached Bridge successfully");
        return ResponseEntity.ok().build();
    }

    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity<?> callback(
            @RequestHeader(value = "x-nonce-str", required = false) String nonce,
            @RequestHeader(value = "x-timestamp", required = false) String timestamp,
            @RequestHeader(value = "x-roomid", required = false) String roomId,
            @RequestHeader(value = "x-msg-type", required = false) String msgType,
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestBody(required = false) String body) {

        if (nonce == null || timestamp == null || roomId == null || msgType == null) {
            log.warn("[DOUYIN-CALLBACK] Rejected request with missing signed headers: room={} type={}", roomId, msgType);
            return ResponseEntity.badRequest().body(Map.of("error", "missing signed headers"));
        }

        Map<String, String> signedHeaders = new LinkedHashMap<>();
        signedHeaders.put("x-nonce-str", nonce);
        signedHeaders.put("x-timestamp", timestamp);
        signedHeaders.put("x-roomid", roomId);
        signedHeaders.put("x-msg-type", msgType);

        String rawBody = body == null ? "" : body;
        if (!signatureService.verify(signedHeaders, rawBody, dataSecret, signature)) {
            log.warn("[DOUYIN-CALLBACK] Signature FAILED: room={} type={} timestamp={}", roomId, msgType, timestamp);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid signature"));
        }

        try {
            JsonNode payload = objectMapper.readTree(rawBody);
            if (!payload.isArray()) {
                log.warn("[DOUYIN-CALLBACK] Invalid payload shape: room={} type={}", roomId, msgType);
                return ResponseEntity.badRequest().body(Map.of("error", "payload must be an array"));
            }

            log.info("[DOUYIN-CALLBACK] Signature OK: room={} type={} events={}",
                    roomId, msgType, payload.size());

            switch (msgType) {
                case "live_comment" -> handleComments(roomId, payload);
                case "live_fansclub" -> handleFansClub(payload);
                default -> log.info("[DOUYIN-CALLBACK] Signed message type currently unused: room={} type={} events={}",
                        roomId, msgType, payload.size());
            }
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            log.error("[DOUYIN-CALLBACK] Processing FAILED: room={} type={}", roomId, msgType, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "callback processing failed"));
        }
    }

    private void handleComments(String roomId, JsonNode payload) {
        int bindCommands = 0;
        for (JsonNode event : payload) {
            String msgId = event.path("msg_id").asText("");
            if (!deduplicator.firstSeen(msgId)) {
                log.debug("[DOUYIN-COMMENT] Duplicate msg ignored: msgId={}", msgId);
                continue;
            }

            String content = event.path("content").asText("").trim();
            Matcher matcher = BIND_PATTERN.matcher(content);
            if (!matcher.matches()) {
                continue;
            }
            bindCommands++;

            String code = matcher.group(1);
            String openId = event.path("sec_openid").asText("");
            String nickname = event.path("nickname").asText("");
            int eventLevel = Math.max(0, event.path("fansclub_level").asInt(0));

            log.info("[DOUYIN-COMMENT] Bind command received: nickname={} douyin={} code={} eventLevel={}",
                    safe(nickname), shortId(openId), code, eventLevel);

            try {
                BindingRecord record = bindingService.complete(code, openId, nickname, eventLevel);
                log.info("[DOUYIN-COMMENT] Real binding SUCCESS: mc={} douyin={} nickname={} initialLevel={}",
                        record.minecraftName(), shortId(openId), safe(nickname), eventLevel);

                CompletableFuture.runAsync(() -> refreshFanInfo(roomId, openId, nickname));
            } catch (IllegalArgumentException e) {
                log.info("[DOUYIN-COMMENT] Bind command ignored: nickname={} douyin={} code={} reason={}",
                        safe(nickname), shortId(openId), code, e.getMessage());
            }
        }
        if (bindCommands == 0) {
            log.debug("[DOUYIN-COMMENT] Batch contained no binding command: events={}", payload.size());
        }
    }

    private void handleFansClub(JsonNode payload) {
        for (JsonNode event : payload) {
            String msgId = event.path("msg_id").asText("");
            if (!deduplicator.firstSeen(msgId)) {
                log.debug("[DOUYIN-FANSCLUB] Duplicate msg ignored: msgId={}", msgId);
                continue;
            }

            String openId = event.path("sec_openid").asText("");
            String nickname = event.path("nickname").asText("");
            int reasonType = event.path("fansclub_reason_type").asInt(0);
            int level = reasonType == 16 ? 0 : Math.max(0, event.path("fansclub_level").asInt(0));

            log.info("[DOUYIN-FANSCLUB] Event received: douyin={} nickname={} reason={} level={}",
                    shortId(openId), safe(nickname), reasonType, level);

            bindingService.updateLevelByDouyinOpenId(openId, nickname, level)
                    .ifPresent(record -> log.info(
                            "[DOUYIN-FANSCLUB] MC sync target found: mc={} reason={} level={}",
                            record.minecraftName(), reasonType, level));
        }
    }

    private void refreshFanInfo(String roomId, String openId, String nickname) {
        try {
            DouyinLiveSessionService.LiveSession session = liveSessionService.find(roomId).orElse(null);
            if (session == null) {
                log.info("[DOUYIN-FANSCLUB] No local live-session metadata for room={}; waiting for event-driven level update", roomId);
                return;
            }

            OptionalInt levelLayer = apiClient.getFansClubLevelLayer(roomId, session.anchorOpenId(), openId);
            if (levelLayer.isEmpty()) {
                log.info("[DOUYIN-FANSCLUB] Detail query returned no level_layer: room={} douyin={}", roomId, shortId(openId));
                return;
            }

            int level = levelLayer.getAsInt();
            bindingService.updateLevelByDouyinOpenId(openId, nickname, level);
            log.info("[DOUYIN-FANSCLUB] Detail query refreshed level_layer: room={} douyin={} level={}",
                    roomId, shortId(openId), level);
        } catch (Exception e) {
            log.warn("[DOUYIN-FANSCLUB] Detail refresh FAILED: room={} douyin={} reason={}",
                    roomId, shortId(openId), e.getMessage());
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

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        return value.replace('\n', ' ').replace('\r', ' ');
    }
}
