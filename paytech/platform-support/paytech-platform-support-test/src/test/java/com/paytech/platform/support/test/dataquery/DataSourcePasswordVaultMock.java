package com.paytech.platform.support.test.dataquery;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/**
 * 数据源密码的 Vault DTO 配置：DATA_SOURCE_PASSWORD_V1 / REFERENCE；Protect 用途 DATA_SOURCE_CONFIGURATION，
 * Reveal 用途 DATA_SOURCE_CONNECTION。
 * <li>同一幂等键的 Protect 返回同一 Token；Reveal 只返回已保存或预先登记的值，未知 Token 记为意外调用。</li>
 * <li>调用记录只保存 Token 与幂等键，不保存密码原值；不替换正式 VaultClient。</li>
 */
@TestComponent
public class DataSourcePasswordVaultMock implements Clear {

    private final Map<String, String> values = new HashMap<>();
    private final Map<String, String> tokensByKey = new HashMap<>();
    private final List<String> protectKeys = new ArrayList<>();
    private final List<String> revealTokens = new ArrayList<>();
    private final List<String> unexpected = new ArrayList<>();
    private int sequence;
    private boolean protectUnavailable;

    /** 预先登记直接插入的数据源密码。 */
    public synchronized void allow(String token, String password) {
        values.put(token, password);
    }

    /** 由 Mock Controller 调用：保存密码并返回合成 Token。 */
    public synchronized String protect(String idempotencyKey, String password) {
        protectKeys.add(idempotencyKey);
        if (protectUnavailable) {
            throw MockHttpFault.raw(503, "{}");
        }
        String token = tokensByKey.computeIfAbsent(idempotencyKey, key -> token(++sequence));
        values.put(token, password);
        return token;
    }

    /** 由 Mock Controller 调用：返回已保存的密码。 */
    public synchronized String reveal(String token) {
        revealTokens.add(token);
        String value = values.get(token);
        if (value == null) {
            unexpected.add(token);
            throw new IllegalStateException("未配置的数据源密码 Reveal");
        }
        return value;
    }

    /** 之后的 Protect 返回 503。 */
    public synchronized void protectUnavailable() {
        protectUnavailable = true;
    }

    /** Protect 次数。 */
    public synchronized int protectCount() {
        return protectKeys.size();
    }

    /** Reveal 次数。 */
    public synchronized int revealCount() {
        return revealTokens.size();
    }

    /** 某 Token 当前保存的密码，用于断言保存的是新密码。 */
    public synchronized String password(String token) {
        return values.get(token);
    }

    /** 每例开始时为空。 */
    public synchronized void assertEmpty() {
        assertAll("数据源密码 Vault Mock 应处于空基线", () -> assertTrue(values.isEmpty()),
            () -> assertTrue(protectKeys.isEmpty()), () -> assertTrue(revealTokens.isEmpty()));
    }

    /** 清理前检查意外调用。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(List.of(), unexpected, "数据源密码 Vault 收到未配置的 Reveal");
    }

    /** 每例清理。 */
    @Override
    public synchronized void clear() {
        values.clear();
        tokensByKey.clear();
        protectKeys.clear();
        revealTokens.clear();
        unexpected.clear();
        sequence = 0;
        protectUnavailable = false;
    }

    /** vlt_dsp 前缀加零填充序号，共 43 位。 */
    public static String token(int sequence) {
        return "vlt_dsp" + String.format("%036d", sequence);
    }
}
