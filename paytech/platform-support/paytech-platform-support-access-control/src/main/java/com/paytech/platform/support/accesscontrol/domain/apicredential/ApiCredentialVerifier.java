package com.paytech.platform.support.accesscontrol.domain.apicredential;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/** 只接受 Platform 批准参数范围内的 Argon2id PHC，并校验完整 opaque Credential。 */
public final class ApiCredentialVerifier {

    private static final int MAX_PHC_LENGTH = 512;
    private static final int MIN_MEMORY_KIB = 19_456;
    private static final int MAX_MEMORY_KIB = 262_144;
    private static final int MIN_ITERATIONS = 2;
    private static final int MAX_ITERATIONS = 10;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_LENGTH_BYTES = 32;
    private static final Pattern ARGON2ID_PHC = Pattern.compile(
        "\\$argon2id\\$v=19\\$m=([0-9]{1,6}),t=([0-9]{1,2}),p=1\\$([A-Za-z0-9+/]+={0,2})"
            + "\\$([A-Za-z0-9+/]+={0,2})");

    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, MIN_MEMORY_KIB,
        MIN_ITERATIONS);

    /**
     * @throws IllegalStateException verifier 不是批准的 PHC 结构或校验器拒绝其结构
     */
    public boolean matches(String credential, String verifierPhc) {
        if (credential == null || verifierPhc == null || verifierPhc.length() > MAX_PHC_LENGTH) {
            throw invalidVerifier();
        }
        Matcher matcher = ARGON2ID_PHC.matcher(verifierPhc);
        if (!matcher.matches()) {
            throw invalidVerifier();
        }
        int memory = boundedNumber(matcher.group(1));
        int iterations = boundedNumber(matcher.group(2));
        if (memory < MIN_MEMORY_KIB || memory > MAX_MEMORY_KIB
            || iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS
            || decodedLength(matcher.group(3)) != SALT_LENGTH_BYTES
            || decodedLength(matcher.group(4)) != HASH_LENGTH_BYTES) {
            throw invalidVerifier();
        }
        try {
            return encoder.matches(credential, verifierPhc);
        } catch (RuntimeException exception) {
            throw invalidVerifier();
        }
    }

    private int boundedNumber(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw invalidVerifier();
        }
    }

    private int decodedLength(String value) {
        try {
            return Base64.getDecoder().decode(value).length;
        } catch (IllegalArgumentException exception) {
            throw invalidVerifier();
        }
    }

    private IllegalStateException invalidVerifier() {
        return new IllegalStateException("API Credential PHC 状态无效");
    }
}
