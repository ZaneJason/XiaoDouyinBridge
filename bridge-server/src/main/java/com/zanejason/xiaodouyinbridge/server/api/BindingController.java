package com.zanejason.xiaodouyinbridge.server.api;

import com.zanejason.xiaodouyinbridge.server.model.BindingRecord;
import com.zanejason.xiaodouyinbridge.server.service.BindingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/bindings")
public class BindingController {
    private final BindingService bindingService;
    private final String apiKey;

    public BindingController(BindingService bindingService,
                             @Value("${bridge.api-key}") String apiKey) {
        this.bindingService = bindingService;
        this.apiKey = apiKey;
    }

    @PostMapping("/request")
    public ResponseEntity<?> requestBinding(
            @RequestHeader(value = "X-Bridge-Key", required = false) String key,
            @RequestBody BindingRequest request) {
        if (!authorized(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthorized"));
        }
        if (request.minecraftUuid() == null || request.minecraftName() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "minecraftUuid and minecraftName are required"));
        }

        BindingService.PendingBinding pending = bindingService.createRequest(
                request.minecraftUuid(), request.minecraftName());
        return ResponseEntity.ok(new BindingCodeResponse(pending.code(), pending.expiresAt().toString()));
    }

    @GetMapping("/minecraft/{uuid}")
    public ResponseEntity<?> getBinding(
            @RequestHeader(value = "X-Bridge-Key", required = false) String key,
            @PathVariable String uuid) {
        if (!authorized(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthorized"));
        }
        return bindingService.findByMinecraftUuid(uuid)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "not_bound")));
    }

    private boolean authorized(String key) {
        return apiKey.equals(key);
    }

    public record BindingRequest(String minecraftUuid, String minecraftName) {}
    public record BindingCodeResponse(String code, String expiresAt) {}
}
