package com.paytech.foundation.compensation.core.serialization;

import java.util.Locale;
import java.util.Set;

import tools.jackson.databind.JsonNode;

/**
 * 持久化前拒绝二进制值以及明显的密钥、凭据或卡数据。
 *
 * @author Codex
 */
public final class CompensationPayloadPolicy {

    private static final Set<String> FORBIDDEN_FIELDS = Set.of("accesstoken", "apikey", "authorization", "bearer",
        "cardnumber", "clientsecret", "credential", "credentials", "cvc", "cvv", "fulltrackdata", "pan", "password",
        "pin", "pinblock", "privatekey", "refreshtoken", "secret", "signingkey", "trackdata");

    public void validate(JsonNode payload) {
        validateNode(payload);
    }

    private void validateNode(JsonNode node) {
        if (node.isString()) {
            requireWellFormedUnicode(node.stringValue(), "payload contains malformed Unicode string value");
        }
        if (node.isBinary()) {
            throw new IllegalArgumentException("payload must not contain binary data");
        }
        if (node.isPojo() || node.isMissingNode()) {
            throw new IllegalArgumentException("payload contains unsupported JSON node: " + node.getNodeType());
        }
        if (node.isFloatingPointNumber() && !Double.isFinite(node.doubleValue())) {
            throw new IllegalArgumentException("payload contains a non-finite number");
        }
        if (node.isNumber() && (!node.isIntegralNumber() || !node.canConvertToLong())) {
            throw new IllegalArgumentException(
                "payload numbers must be signed 64-bit integers; encode decimals as strings");
        }
        if (node.isArray()) {
            node.forEach(this::validateNode);
            return;
        }
        if (!node.isObject()) {
            return;
        }
        node.properties().forEach(entry -> {
            requireWellFormedUnicode(entry.getKey(), "payload contains malformed Unicode field name");
            if (FORBIDDEN_FIELDS.contains(normalize(entry.getKey()))) {
                throw new IllegalArgumentException("payload contains forbidden field: " + entry.getKey());
            }
            validateNode(entry.getValue());
        });
    }

    private String normalize(String fieldName) {
        return fieldName.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private void requireWellFormedUnicode(String value, String message) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException(message);
            }
            if (!Character.isHighSurrogate(current)) {
                continue;
            }
            if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index))) {
                throw new IllegalArgumentException(message);
            }
        }
    }
}
