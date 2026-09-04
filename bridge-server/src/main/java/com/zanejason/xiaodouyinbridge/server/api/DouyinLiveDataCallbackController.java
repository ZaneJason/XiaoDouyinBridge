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
            return ResponseEntity.badRequest().body(Map.of("error", "missing signed headers"));
        }

        Map<String, String> signedHeaders = new LinkedHashMap<>();
        signedHeaders.put("x-nonce-str", nonce);
        signedHeaders.put("x-timestamp", timestamp);
        signedHeaders.put("x-roomid", roomId);
        signedHeaders.put("x-msg-type", msgType);

        String rawBody = body == null ? "" : body;
        if (!signatureService.verify(signedHeaders, rawBody, dataSecret, signature)) {
            log.warn("Rejected Douyin callback: signature mismatch, room={}, type={}", roomId, msgType);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid signature"));
        }

        try {
            JsonNode payload = objectMapper.readTree(rawBody);
            if (!payload.isArray()) {
                return ResponseEntity.badRequest().body(Map.of("error", "payload must be an array"));
            }

            switch (msgType) {
                case "live_comment" -> handleComments(roomId, payload);
                case "live_fansclub" -> handleFansClub(payload);
                default -> log.debug("Ignoring supported-signed but unused Douyin message type: {}", msgType);
            }
            // 官方以 2XX 作为成功 ACK；回调处理必须尽快返回。
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            log.error("Failed to process Douyin callback, room={}, type={}", roomId, msgType, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "callback processing failed"));
        }
    }

    private void handleComments(String roomId, JsonNode payload) {
        for (JsonNode event : payload) {
            String msgId = event.path("msg_id").asText("");
            if (!deduplicator.firstSeen(msgId)) {
                continue;
            }

            String content = event.path("content").asText("").trim();
            Matcher matcher = BIND_PATTERN.matcher(content);
            if (!matcher.matches()) {
                continue;
            }

            String code = matcher.group(1);
            String openId = event.path("sec_openid").asText("");
            String nickname = event.path("nickname").asText("");
            int eventLevel = Math.max(0, event.path("fansclub_level").asInt(0));

            try {
                BindingRecord record = bindingService.complete(code, openId, nickname, eventLevel);
                log.info("Real Douyin binding completed: mc={} douyin={} openId={} initialLevel={}",
                        record.minecraftName(), nickname, openId, eventLevel);

                // 查询当前粉丝团信息可能产生外网延迟，因此异步做，不阻塞抖音的 2 秒 ACK 时限。
                CompletableFuture.runAsync(() -> refreshFanInfo(roomId, openId, nickname));
            } catch (IllegalArgumentException e) {
                // 绑定码错误不能让整批抖音消息失败，否则平台会重试整批数据。
                log.info("Ignored Douyin bind comment from {}: {}", nickname, e.getMessage());
            }
        }
    }

    private void handleFansClub(JsonNode payload) {
        for (JsonNode event : payload) {
            String msgId = event.path("msg_id").asText("");
            if (!deduplicator.firstSeen(msgId)) {
                continue;
            }

            String openId = event.path("sec_openid").asText("");
            String nickname = event.path("nickname").asText("");
            int reasonType = event.path("fansclub_reason_type").asInt(0);
            int level = reasonType == 16 ? 0 : Math.max(0, event.path("fansclub_level").asInt(0));

            bindingService.updateLevelByDouyinOpenId(openId, nickname, level)
                    .ifPresent(record -> log.info(
                            "Real Douyin fansclub update: mc={} douyin={} reason={} level={}",
                            record.minecraftName(), nickname, reasonType, level));
        }
    }

    private void refreshFanInfo(String roomId, String openId, String nickname) {
        try {
            DouyinLiveSessionService.LiveSession session = liveSessionService.find(roomId).orElse(null);
            if (session == null) {
                log.info("No live session metadata for room {}; exact fansclub event will update level later", roomId);
                return;
            }

            OptionalInt levelLayer = apiClient.getFansClubLevelLayer(roomId, session.anchorOpenId(), openId);
            int level = levelLayer.orElse(0);
            bindingService.updateLevelByDouyinOpenId(openId, nickname, level);
            log.info("Refreshed Douyin fansclub level_layer for openId={} to {}", openId, level);
        } catch (Exception e) {
            log.warn("Failed to refresh Douyin fansclub info for openId={}: {}", openId, e.getMessage());
        }
    }
}
