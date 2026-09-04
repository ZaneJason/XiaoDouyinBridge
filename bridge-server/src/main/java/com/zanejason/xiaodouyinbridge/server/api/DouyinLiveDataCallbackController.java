package com.zanejason.xiaodouyinbridge.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zanejason.xiaodouyinbridge.server.service.DouyinLiveEventService;
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

@RestController
@RequestMapping("/api/douyin/live-data/callback")
public class DouyinLiveDataCallbackController {
    private static final Logger log = LoggerFactory.getLogger(DouyinLiveDataCallbackController.class);

    private final ObjectMapper objectMapper;
    private final DouyinSignatureService signatureService;
    private final DouyinLiveEventService liveEventService;
    private final String dataSecret;

    public DouyinLiveDataCallbackController(
            ObjectMapper objectMapper,
            DouyinSignatureService signatureService,
            DouyinLiveEventService liveEventService,
            @Value("${douyin.data-secret:default}") String dataSecret) {
        this.objectMapper = objectMapper;
        this.signatureService = signatureService;
        this.liveEventService = liveEventService;
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

            log.info("[DOUYIN-CALLBACK] Signature OK: room={} type={} events={}", roomId, msgType, payload.size());

            // 服务端回调按消息类型拆批；统一事件处理器仍按每条 msg_type_str 处理。
            DouyinLiveEventService.ProcessSummary summary = liveEventService.processPayload(
                    "SERVER_CALLBACK", roomId, payload);

            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "events", summary.events(),
                    "bindings", summary.bindings(),
                    "fansClubEvents", summary.fansClubEvents()
            ));
        } catch (Exception e) {
            log.error("[DOUYIN-CALLBACK] Processing FAILED: room={} type={}", roomId, msgType, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "callback processing failed"));
        }
    }
}
