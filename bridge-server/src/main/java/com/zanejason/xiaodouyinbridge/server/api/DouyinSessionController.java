package com.zanejason.xiaodouyinbridge.server.api;

import com.zanejason.xiaodouyinbridge.server.service.DouyinApiClient;
import com.zanejason.xiaodouyinbridge.server.service.DouyinLiveSessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/douyin/session")
public class DouyinSessionController {
    private final DouyinLiveSessionService sessionService;
    private final DouyinApiClient apiClient;
    private final String bridgeApiKey;

    public DouyinSessionController(
            DouyinLiveSessionService sessionService,
            DouyinApiClient apiClient,
            @Value("${bridge.api-key}") String bridgeApiKey) {
        this.sessionService = sessionService;
        this.apiClient = apiClient;
        this.bridgeApiKey = bridgeApiKey;
    }

    /**
     * 推荐方式：把直播伴侣/玩法客户端启动时得到的 launch token 发给 Bridge，
     * Bridge 调用抖音官方直播信息接口换出 roomId 和主播 openId，然后启动真实推送任务。
     */
    @PostMapping("/start")
    public ResponseEntity<?> start(
            @RequestHeader(value = "X-Bridge-Key", required = false) String key,
            @RequestBody StartRequest request) {
        if (!authorized(key)) {
            return unauthorized();
        }
        try {
            return ResponseEntity.ok(sessionService.startFromLaunchToken(request.launchToken()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 调试方式：已经知道 roomId / anchorOpenId 时可直接启动。
     */
    @PostMapping("/start-manual")
    public ResponseEntity<?> startManual(
            @RequestHeader(value = "X-Bridge-Key", required = false) String key,
            @RequestBody ManualStartRequest request) {
        if (!authorized(key)) {
            return unauthorized();
        }
        try {
            return ResponseEntity.ok(sessionService.start(
                    request.roomId(), request.anchorOpenId(), request.anchorNickname()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(
            @RequestHeader(value = "X-Bridge-Key", required = false) String key) {
        if (!authorized(key)) {
            return unauthorized();
        }
        return ResponseEntity.ok(Map.of(
                "douyinConfigured", apiClient.configured(),
                "appId", apiClient.configured() ? apiClient.appId() : "",
                "sessions", sessionService.all()
        ));
    }

    private boolean authorized(String key) {
        return bridgeApiKey.equals(key);
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthorized"));
    }

    public record StartRequest(String launchToken) {}
    public record ManualStartRequest(String roomId, String anchorOpenId, String anchorNickname) {}
}
