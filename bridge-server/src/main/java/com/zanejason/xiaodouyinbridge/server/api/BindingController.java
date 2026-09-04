package com.zanejason.xiaodouyinbridge.server.api;

import com.zanejason.xiaodouyinbridge.server.model.BindingRecord;
import com.zanejason.xiaodouyinbridge.server.service.BindingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/bindings")
public class BindingController {
    private static final Logger log = LoggerFactory.getLogger(BindingController.class);

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
            log.warn("[MC-API] Unauthorized binding-code request");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthorized"));
        }
        if (request.minecraftUuid() == null || request.minecraftName() == null) {
            log.warn("[MC-API] Invalid binding-code request: missing Minecraft identity");
            return ResponseEntity.badRequest().body(Map.of("error", "minecraftUuid and minecraftName are required"));
        }

        log.info("[MC-API] Binding-code requested: mc={} uuid={}",
                request.minecraftName(), shortId(request.minecraftUuid()));
        BindingService.PendingBinding pending = bindingService.createRequest(
                request.minecraftUuid(), request.minecraftName());
        return ResponseEntity.ok(new BindingCodeResponse(pending.code(), pending.expiresAt().toString()));
    }

    @GetMapping("/minecraft/{uuid}")
    public ResponseEntity<?> getBinding(
            @RequestHeader(value = "X-Bridge-Key", required = false) String key,
            @PathVariable String uuid) {
        if (!authorized(key)) {
            log.warn("[MC-API] Unauthorized binding lookup: uuid={}", shortId(uuid));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthorized"));
        }

        return bindingService.findByMinecraftUuid(uuid)
                .<ResponseEntity<?>>map(record -> {
                    log.debug("[MC-API] Binding sync: mc={} level={}", record.minecraftName(), record.fansClubLevel());
                    return ResponseEntity.ok(record);
                })
                .orElseGet(() -> {
                    log.debug("[MC-API] Player is not bound yet: uuid={}", shortId(uuid));
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_bound"));
                });
    }

    private boolean authorized(String key) {
        return apiKey.equals(key);
    }

    private static String shortId(String value) {
        if (value == null || value.length() <= 10) {
            return value;
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }

    public record BindingRequest(String minecraftUuid, String minecraftName) {}
    public record BindingCodeResponse(String code, String expiresAt) {}
}
