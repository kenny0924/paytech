package com.paytech.vault.client.internal;
import com.paytech.foundation.common.error.FailException;
import com.paytech.vault.client.VaultClientErrorCode;
import java.nio.charset.StandardCharsets;

/** 共享协议约束；拒绝非法输入，绝不修复或规范化内容。 */
public final class VaultValidation {
    private VaultValidation() {}
    public static void required(Object value) {
        if (value == null) throw new FailException(VaultClientErrorCode.INVALID_REQUEST);
    }
    public static void code(String value) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,63}")) invalid();
    }
    public static void token(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) invalid();
    }
    public static boolean validHeader(String value, int maximum) {
        return value != null && value.length() >= 1 && value.length() <= maximum && value.matches("[A-Za-z0-9._~-]+");
    }
    public static void safeHeader(String value, int maximum) {
        if (!validHeader(value, maximum)) invalid();
    }
    public static void value(String value) {
        required(value);
        unicode(value);
        if (value.getBytes(StandardCharsets.UTF_8).length > 32768) throw new FailException(VaultClientErrorCode.VALUE_TOO_LARGE);
    }
    public static void unicode(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isLowSurrogate(current)) invalid();
            if (!Character.isHighSurrogate(current)) continue;
            if (++index == value.length() || !Character.isLowSurrogate(value.charAt(index))) invalid();
        }
    }
    private static void invalid() { throw new FailException(VaultClientErrorCode.INVALID_REQUEST); }
}
