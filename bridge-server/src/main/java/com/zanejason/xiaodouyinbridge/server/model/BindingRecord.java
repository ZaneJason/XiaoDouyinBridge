package com.zanejason.xiaodouyinbridge.server.model;

import java.time.Instant;

public record BindingRecord(
        String minecraftUuid,
        String minecraftName,
        String douyinOpenId,
        String douyinNickname,
        int fansClubLevel,
        Instant updatedAt
) {
}
