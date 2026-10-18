package com.paytech.platform.support.test.mocker.external;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;

/** 登录邮箱 Vault 的 Protect/Reveal DTO；只返回用例显式登记的合成邮箱，调用记录不保存邮箱原文。 */
@TestComponent
public class LoginEmailVaultMocker implements Clear {

    private final Map<String, String> tokens = new LinkedHashMap<>();
    private final Map<String, String> values = new LinkedHashMap<>();
    private final List<String> protectedTokens = new ArrayList<>();
    private final List<String> revealedTokens = new ArrayList<>();
    private int unexpected;

    /** 登记合成规范邮箱并返回其稳定 Token，同一邮箱多次登记得到同一 Token。 */
    public synchronized String allow(String canonicalEmail) {
        String token = tokenOf(canonicalEmail);
        tokens.put(canonicalEmail, token);
        values.put(token, canonicalEmail);
        return token;
    }

    /** 与 Vault 稳定 Token 语义一致的确定性合成值（43 位 Base64URL），供 Mocker 直接插入账号行。 */
    public static String tokenOf(String canonicalEmail) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalEmail.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** 由 Mock Controller 调用；未登记邮箱记为意外调用。 */
    public synchronized String protect(String value) {
        String token = tokens.get(value);
        if (token == null) {
            unexpected++;
            throw new IllegalStateException("未配置登录邮箱 Protect");
        }
        protectedTokens.add(token);
        return token;
    }

    /** 由 Mock Controller 调用；未登记 Token 记为意外调用。 */
    public synchronized String reveal(String token) {
        String value = values.get(token);
        if (value == null) {
            unexpected++;
            throw new IllegalStateException("未配置登录邮箱 Reveal");
        }
        revealedTokens.add(token);
        return value;
    }

    /** 本例 Protect 次数，用于检查前置拒绝没有远端调用。 */
    public synchronized int protectCount() {
        return protectedTokens.size();
    }

    /** 本例 Reveal 次数。 */
    public synchronized int revealCount() {
        return revealedTokens.size();
    }

    /** 每例开始时没有遗留配置。 */
    public synchronized void assertEmpty() {
        assertTrue(tokens.isEmpty() && protectedTokens.isEmpty() && revealedTokens.isEmpty() && unexpected == 0,
            "登录邮箱 Vault Mock 未清理");
    }

    /** 业务捕获 Mock 异常时仍让用例失败。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的登录邮箱 Vault 调用");
    }

    /** 每例清理响应配置及调用记录。 */
    @Override
    public synchronized void clear() {
        tokens.clear();
        values.clear();
        protectedTokens.clear();
        revealedTokens.clear();
        unexpected = 0;
    }
}
