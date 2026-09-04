package com.zanejason.xiaodouyinbridge.server.api;

import com.zanejason.xiaodouyinbridge.server.model.BindingRecord;
import com.zanejason.xiaodouyinbridge.server.service.BindingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dev/fansclub")
public class DevDouyinController {
    private final BindingService bindingService;
    private final String apiKey;

    public DevDouyinController(BindingService bindingService,
                               @Value("${bridge.api-key}") String apiKey) {
        this.bindingService = bindingService;
        this.apiKey = apiKey;
    }

    @PostMapping("/complete")
    public ResponseEntity<?> completeBinding(
            @RequestHeader(value = "X-Bridge-Key", required = false) String key,
            @RequestBody CompleteRequest request) {
        if (!authorized(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthorized"));
        }
        try {
            BindingRecord record = bindingService.complete(
                    request.code(), request.douyinOpenId(), request.douyinNickname(), request.fansClubLevel());
            return ResponseEntity.ok(record);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/level")
    public ResponseEntity<?> updateLevel(
            @RequestHeader(value = "X-Bridge-Key", required = false) String key,
            @RequestBody LevelRequest request) {
        if (!authorized(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthorized"));
        }
        try {
            return ResponseEntity.ok(bindingService.updateLevel(request.minecraftUuid(), request.fansClubLevel()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private boolean authorized(String key) {
        return apiKey.equals(key);
    }

    public record CompleteRequest(
            String code,
            String douyinOpenId,
            String douyinNickname,
            int fansClubLevel
    ) {}

    public record LevelRequest(String minecraftUuid, int fansClubLevel) {}
}
