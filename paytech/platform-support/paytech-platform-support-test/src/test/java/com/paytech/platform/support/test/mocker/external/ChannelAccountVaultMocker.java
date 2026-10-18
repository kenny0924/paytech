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
import com.paytech.foundation.testsupport.api.MockHttpFault;

/**
 * 渠道账号 Vault Reveal（Purpose CHANNEL_EXECUTION）的响应 DTO：只返回用例显式登记的合成账号配置，
 * 或按配置返回 Vault 故障；调用记录只保存 Token，不保存配置原文。
 */
@TestComponent
public class ChannelAccountVaultMocker implements Clear {

    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, Integer> failures = new LinkedHashMap<>();
    private final Map<String, Long> delays = new LinkedHashMap<>();
    private final List<String> revealed = new ArrayList<>();
    private int unexpected;

    /** 合成的 43 位 Base64URL 引用 Token。 */
    public static String token(String seed) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** 登记 Token 对应的账号配置原文。 */
    public synchronized void allow(String token, String configuration) {
        values.put(token, configuration);
    }

    /** 登记 Token 的 Vault 故障状态码。 */
    public synchronized void fail(String token, int status) {
        failures.put(token, status);
    }

    /** 登记 Token 的响应延迟毫秒数，用于预算耗尽场景。 */
    public synchronized void delay(String token, long millis) {
        delays.put(token, millis);
    }

    /** 由 Vault Mock Controller 调用；未登记 Token 记为意外调用。 */
    public String reveal(String token) {
        long delay;
        synchronized (this) {
            delay = delays.getOrDefault(token, 0L);
        }
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        return respond(token);
    }

    synchronized String respond(String token) {
        revealed.add(token);
        Integer status = failures.get(token);
        if (status != null) {
            throw MockHttpFault.json(status, Map.of("code", "VAULT_UNAVAILABLE", "message", "Vault 不可用"));
        }
        String value = values.get(token);
        if (value == null) {
            unexpected++;
            throw new IllegalStateException("未配置的渠道账号 Reveal");
        }
        return value;
    }

    /** 本例 Reveal 次数。 */
    public synchronized int revealCount() {
        return revealed.size();
    }

    /** 每例结束检查没有未登记的 Reveal。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的渠道账号 Vault 调用");
    }

    @Override
    public synchronized void clear() {
        values.clear();
        failures.clear();
        delays.clear();
        revealed.clear();
        unexpected = 0;
    }
}
