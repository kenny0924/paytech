package com.paytech.platform.support.accesscontrol.domain.authentication;

import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;

/** Operations 密码规范化、Argon2id 校验与本地弱密码拒绝。 */
public class OperationsPasswordPolicy {
    // TODO by Codex: 生产接入经过维护的泄漏/常见密码语料；本地集合只用于开发演示。
    private static final Set<String> BLOCKED = Set.of("passwordpassword", "123456789012345", "qwertyuiopasdfgh",
        "letmeinletmeinletmein", "correcthorsebatterystaple", "adminadminadmin", "welcome123456789");
    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    private final String unknownAccountVerifier = encoder.encode("Unknown account timing value");

    public void matchesUnknownAccount(String value) {
        matches(value, unknownAccountVerifier);
    }

    public String validateNewPassword(String value, String canonicalEmail) {
        String normalized = normalize(value);
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (BLOCKED.contains(lower) || lower.contains("paytech") || lower.contains("code-mor")
            || canonicalEmail != null && lower.contains(canonicalEmail.split("@", 2)[0])) {
            throw new BizCheckFailException("密码不能使用常见密码，也不能包含服务名称或登录账号");
        }
        return normalized;
    }

    public String encode(String value) {
        return encoder.encode(value);
    }

    public boolean matches(String value, String phc) {
        String normalized;
        try {
            normalized = normalize(value);
        } catch (FailException exception) {
            return false;
        }
        if (phc == null || !phc.startsWith("$argon2id$v=19$m=")) {
            throw new IllegalStateException("Operations Password PHC 格式无效");
        }
        return encoder.matches(normalized, phc);
    }

    public String normalize(String value) {
        if (value == null) {
            throw new InvalidParameterException("密码不能为空");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > 512) {
            throw new BizCheckFailException("密码的 UTF-8 编码不能超过 512 字节");
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 15 || length > 128 || normalized.getBytes(StandardCharsets.UTF_8).length > 512) {
            throw new BizCheckFailException("密码长度必须为 15 至 128 个字符，且 UTF-8 编码不能超过 512 字节");
        }
        return normalized;
    }

    public String canonicalEmail(String value) {
        if (value == null || value.length() > 320 || value.chars().anyMatch(Character::isWhitespace)) {
            throw new InvalidParameterException("登录邮箱格式无效");
        }
        String[] parts = Normalizer.normalize(value, Normalizer.Form.NFC).split("@", -1);
        if (parts.length != 2 || parts[0].isEmpty() || parts[0].length() > 64
            || !parts[0].matches("[A-Za-z0-9.!#$%&'*+/=?^_\u0060{|}~-]+") || parts[0].startsWith(".")
            || parts[0].endsWith(".") || parts[0].contains("..")) {
            throw new InvalidParameterException("登录邮箱格式无效");
        }
        try {
            String domain = IDN.toASCII(parts[1], IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            if (!domain.contains(".") || domain.endsWith(".") || domain.length() > 253) {
                throw new IllegalArgumentException();
            }
            return parts[0].toLowerCase(Locale.ROOT) + "@" + domain;
        } catch (IllegalArgumentException exception) {
            throw new InvalidParameterException("登录邮箱格式无效", exception);
        }
    }

    public String maskEmail(String canonical) {
        int at = canonical.indexOf('@');
        return canonical.substring(0, 1) + "***" + canonical.substring(at);
    }
}
