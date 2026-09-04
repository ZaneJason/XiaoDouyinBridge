package com.zanejason.xiaodouyinbridge.minecraft;

import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class BridgeClient {
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final Gson gson = new Gson();
    private final String baseUrl;
    private final String apiKey;

    public BridgeClient(String baseUrl, String apiKey) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.apiKey = apiKey;
    }

    public CompletableFuture<BindingCode> requestBinding(UUID uuid, String playerName) {
        String body = gson.toJson(new BindingRequest(uuid.toString(), playerName));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/bindings/request"))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("X-Bridge-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    ensureSuccess(response);
                    return gson.fromJson(response.body(), BindingCode.class);
                });
    }

    public CompletableFuture<Optional<BindingInfo>> getBinding(UUID uuid) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/bindings/minecraft/" + uuid))
                .timeout(Duration.ofSeconds(8))
                .header("X-Bridge-Key", apiKey)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 404) {
                        return Optional.empty();
                    }
                    ensureSuccess(response);
                    return Optional.of(gson.fromJson(response.body(), BindingInfo.class));
                });
    }

    private void ensureSuccess(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Bridge HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    private static String stripTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private record BindingRequest(String minecraftUuid, String minecraftName) {}

    public record BindingCode(String code, String expiresAt) {}

    public record BindingInfo(
            String minecraftUuid,
            String minecraftName,
            String douyinOpenId,
            String douyinNickname,
            int fansClubLevel,
            String updatedAt
    ) {}
}
