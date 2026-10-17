package com.paytech.platform.support.test.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.foundation.testsupport.api.Clear;

/**
 * Webhook 签名密钥的 Vault DTO 配置：WEBHOOK_SIGNING_SECRET_V1 / REFERENCE / WEBHOOK_SIGNING
 * <li>Protect 每次返回新的合成 Token 并记住原值；Reveal 只返回已保存或预先登记的值</li>
 * <li>不替换正式 VaultClient；故障由 Mock HTTP 服务按约定写出</li>
 */
@TestComponent
public class WebhookVaultMock implements Clear {

    private final Map<String, String> values = new HashMap<>();
    private int sequence;
    private int protectCalls;
    private int revealCalls;
    private int unexpected;
    private boolean protectUnavailable;
    private boolean revealUnavailable;

    /** 预先登记一把密钥，供直接插入的端点使用。 */
    public synchronized void allow(String token, String secret) {
        values.put(token, secret);
    }

    /** 保存密钥并返回 43 位合成 Token。 */
    public synchronized String protect(String secret) {
        protectCalls++;
        if (protectUnavailable) {
            throw MockHttpFault.raw(503, "{}");
        }
        String token = token(++sequence);
        values.put(token, secret);
        return token;
    }

    /** 只返回已知 Token 的原值；不可用时写出 503。 */
    public synchronized String reveal(String token) {
        revealCalls++;
        if (revealUnavailable) {
            throw MockHttpFault.raw(503, "{}");
        }
        String value = values.get(token);
        if (value == null) {
            unexpected++;
            throw new IllegalStateException("未配置的 Webhook 密钥 Reveal");
        }
        return value;
    }

    /** 读取某个 Token 对应的密钥原值，用于在接收端验签。 */
    public synchronized String secret(String token) {
        return values.get(token);
    }

    /** 之后的 Protect 返回 503。 */
    public synchronized void protectUnavailable() {
        protectUnavailable = true;
    }

    /** 之后的 Reveal 返回 503。 */
    public synchronized void revealUnavailable(boolean unavailable) {
        revealUnavailable = unavailable;
    }

    /** Protect 次数。 */
    public synchronized int protectCount() {
        return protectCalls;
    }

    /** Reveal 次数。 */
    public synchronized int revealCount() {
        return revealCalls;
    }

    /** 清理前检查意外调用。 */
    public synchronized void assertExpected() {
        assertEquals(0, unexpected, "Webhook Vault 收到未配置的请求");
    }

    /** 每例清理配置与计数。 */
    @Override
    public synchronized void clear() {
        values.clear();
        sequence = 0;
        protectCalls = 0;
        revealCalls = 0;
        unexpected = 0;
        protectUnavailable = false;
        revealUnavailable = false;
    }

    /** vlt_whk 前缀加零填充序号，共 43 位。 */
    public static String token(int sequence) {
        return "vlt_whk" + String.format("%036d", sequence);
    }
}
