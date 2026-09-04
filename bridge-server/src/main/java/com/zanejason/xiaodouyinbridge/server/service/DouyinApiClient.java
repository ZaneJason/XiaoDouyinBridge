package com.zanejason.xiaodouyinbridge.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.OptionalInt;

@Service
public class DouyinApiClient {
    private static final String TOKEN_URL = "https://developer.toutiao.com/api/apps/v2/token";
    private static final String LIVE_INFO_URL = "https://webcast.bytedance.com/api/webcastmate/info";
    private static final String START_TASK_URL = "https://webcast.bytedance.com/api/live_data/task/start";
    private static final String FAN_INFO_URL = "https://webcast.bytedance.com/api/live_data/fans_club/get_info";

    private final RestClient restClient = RestClient.builder().build();
    private final String appId;
    private final String appSecret;

    private volatile String cachedAccessToken;
    private volatile Instant accessTokenRefreshAt = Instant.EPOCH;

    public DouyinApiClient(
            @Value("${douyin.app-id:}") String appId,
            @Value("${douyin.app-secret:}") String appSecret) {
        this.appId = appId;
        this.appSecret = appSecret;
    }

    public boolean configured() {
        return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
    }

    public String appId() {
        return appId;
    }

    public synchronized String accessToken() {
        ensureConfigured();
        if (cachedAccessToken != null && Instant.now().isBefore(accessTokenRefreshAt)) {
            return cachedAccessToken;
        }

        JsonNode response = restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "appid", appId,
                        "secret", appSecret,
                        "grant_type", "client_credential"
                ))
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.path("err_no").asInt(-1) != 0) {
            throw new IllegalStateException("获取抖音 access_token 失败: " + errorText(response));
        }

        JsonNode data = response.path("data");
        String token = data.path("access_token").asText("");
        if (token.isBlank()) {
            throw new IllegalStateException("抖音 access_token 响应中没有 access_token");
        }

        long expiresIn = data.path("expires_in").asLong(7200);
        cachedAccessToken = token;
        // 官方 token 通常有效 2 小时；提前 5 分钟刷新，避免临界点失败。
        accessTokenRefreshAt = Instant.now().plusSeconds(Math.max(60, expiresIn - 300));
        return token;
    }

    public LiveSessionInfo getLiveInfo(String launchToken) {
        if (launchToken == null || launchToken.isBlank()) {
            throw new IllegalArgumentException("直播玩法 launch token 不能为空");
        }

        JsonNode response = restClient.post()
                .uri(LIVE_INFO_URL)
                .header("x-token", accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("token", launchToken))
                .retrieve()
                .body(JsonNode.class);

        JsonNode info = response == null ? null : response.path("data").path("info");
        if (info == null || info.isMissingNode() || info.path("room_id").asText("").isBlank()) {
            throw new IllegalStateException("获取抖音直播信息失败: " + errorText(response));
        }

        return new LiveSessionInfo(
                info.path("room_id").asText(),
                info.path("anchor_open_id").asText(),
                info.path("nick_name").asText(),
                info.path("avatar_url").asText()
        );
    }

    public String startLiveDataTask(String roomId, String msgType) {
        ensureConfigured();
        JsonNode response = restClient.post()
                .uri(START_TASK_URL)
                .header("access-token", accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "roomid", roomId,
                        "appid", appId,
                        "msg_type", msgType
                ))
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.path("err_no").asInt(-1) != 0) {
            throw new IllegalStateException("启动 " + msgType + " 推送任务失败: " + errorText(response));
        }
        return response.path("data").path("task_id").asText("");
    }

    /**
     * 官方接口字段名为 level_layer。若用户未加入粉丝团，官方会返回空对象。
     */
    public OptionalInt getFansClubLevelLayer(String roomId, String anchorOpenId, String userOpenId) {
        ensureConfigured();
        String url = UriComponentsBuilder.fromUriString(FAN_INFO_URL)
                .queryParam("roomid", roomId)
                .queryParam("anchor_openid", anchorOpenId)
                .queryParam("user_openids", userOpenId)
                .build()
                .encode()
                .toUriString();

        JsonNode response = restClient.get()
                .uri(url)
                .header("access-token", accessToken())
                .header("Content-Type", "application/json")
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.path("err_no").asInt(-1) != 0) {
            throw new IllegalStateException("查询粉丝团信息失败: " + errorText(response));
        }

        JsonNode user = response.path("data").path("fans_club_Info").path(userOpenId);
        if (user.isMissingNode() || user.isNull() || !user.has("level_layer")) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Math.max(0, user.path("level_layer").asInt(0)));
    }

    private void ensureConfigured() {
        if (!configured()) {
            throw new IllegalStateException("未配置 DOUYIN_APP_ID / DOUYIN_APP_SECRET");
        }
    }

    private String errorText(JsonNode response) {
        if (response == null) {
            return "empty response";
        }
        String errTips = response.path("err_tips").asText("");
        String errMsg = response.path("err_msg").asText("");
        String logId = response.path("logid").asText("");
        return "err_no=" + response.path("err_no").asText("?")
                + ", message=" + (!errMsg.isBlank() ? errMsg : errTips)
                + (logId.isBlank() ? "" : ", logid=" + logId);
    }

    public record LiveSessionInfo(
            String roomId,
            String anchorOpenId,
            String anchorNickname,
            String anchorAvatarUrl
    ) {}
}
