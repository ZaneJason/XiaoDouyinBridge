package com.zanejason.xiaodouyinbridge.server.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

@Service
public class DouyinSignatureService {

    public boolean verify(Map<String, String> signedHeaders, String body, String secret, String actualSignature) {
        if (secret == null || secret.isBlank() || actualSignature == null || actualSignature.isBlank()) {
            return false;
        }
        try {
            String expected = generate(signedHeaders, body, secret);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    actualSignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    public String generate(Map<String, String> signedHeaders, String body, String secret) throws Exception {
        TreeMap<String, String> sorted = new TreeMap<>(signedHeaders);
        StringBuilder raw = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!first) {
                raw.append('&');
            }
            first = false;
            raw.append(entry.getKey()).append('=').append(entry.getValue());
        }
        raw.append(body == null ? "" : body).append(secret);

        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[] digest = md5.digest(raw.toString().getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }
}
