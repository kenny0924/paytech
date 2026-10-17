package com.paytech.foundation.compensation.core.serialization;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 序列化可选且刻意限制大小的补偿 Payload。
 *
 * @author Codex
 */
public final class CompensationPayloadSerializer {

    public static final int MAXIMUM_PAYLOAD_BYTES = 4_096;

    private final ObjectMapper objectMapper;
    private final CompensationPayloadPolicy payloadPolicy;

    public CompensationPayloadSerializer(ObjectMapper objectMapper, CompensationPayloadPolicy payloadPolicy) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.payloadPolicy = Objects.requireNonNull(payloadPolicy, "payloadPolicy must not be null");
    }

    public String serialize(JsonNode payload) {
        if (payload == null) {
            return null;
        }
        payloadPolicy.validate(payload);
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            if (payloadJson.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("payload exceeds " + MAXIMUM_PAYLOAD_BYTES + " UTF-8 bytes");
            }
            return payloadJson;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("payload cannot be serialized as JSON", exception);
        }
    }

    public boolean matches(String existingPayloadJson, JsonNode candidatePayload) {
        if (existingPayloadJson == null || existingPayloadJson.isBlank()) {
            return candidatePayload == null;
        }
        if (candidatePayload == null) {
            return false;
        }
        try {
            return objectMapper.readTree(existingPayloadJson).equals(candidatePayload);
        } catch (Exception exception) {
            return false;
        }
    }
}
