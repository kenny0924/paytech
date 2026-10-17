package com.paytech.gateway.guard.policy;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** 校验 Redis Guard Key 中保持原文的可信身份值。 */
public final class RawGuardIdentity {
    public static final int MAX_UTF8_BYTES = 512;

    private RawGuardIdentity() {}

    public static boolean isValid(String value) {
        if (value == null || value.isBlank() || value.codePoints().anyMatch(Character::isISOControl)) {
            return false;
        }
        try {
            return StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value))
                .remaining() <= MAX_UTF8_BYTES;
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    public static String requireValid(String value) {
        if (!isValid(value)) {
            throw new IllegalArgumentException("Redis Guard 原始身份格式无效");
        }
        return value;
    }
}
