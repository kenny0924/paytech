package com.paytech.platform.support.test.notification;

import java.util.HashMap;
import java.util.Map;
import com.paytech.foundation.testsupport.api.Clear;
import org.springframework.boot.test.context.TestComponent;
import static org.junit.jupiter.api.Assertions.*;

/** 仅保存当前用例的合成邮箱与 DTO，不替换正式 VaultClient。 */
@TestComponent
public class NotificationVaultMock implements Clear {

    private final Map<String, String> tokens = new HashMap<>();
    private final Map<String, String> values = new HashMap<>();
    private final Map<String, String> credentials = new HashMap<>();
    private boolean credentialUnavailable;
    private int protectCalls;
    private int revealCalls;
    private int credentialRevealCalls;
    private int unexpected;

    /** 明确准备每个合成邮箱的稳定 Token。 */
    public synchronized void allow(String email, String token) { tokens.put(email, token); values.put(token, email); }

    /** 仅已配置地址返回 Protect DTO。 */
    public synchronized String protect(String value) {
        protectCalls++;
        if (!tokens.containsKey(value)) { unexpected++; throw new IllegalStateException("未配置邮件 Protect"); }
        return tokens.get(value);
    }

    /** 仅已配置 Token 返回 Reveal DTO。 */
    public synchronized String reveal(String token) {
        revealCalls++;
        if (!values.containsKey(token)) { unexpected++; throw new IllegalStateException("未配置邮件 Reveal"); }
        return values.get(token);
    }

    /** 准备 SES 凭据 Token 对应的合成密钥对 JSON（NOTIFICATION_DELIVERY 用途），可为不合法值以验证失败关闭。 */
    public synchronized void allowCredential(String token, String value) {
        credentials.put(token, value);
        credentialUnavailable = false;
    }

    /** 之后的凭据 Reveal 返回 503，模拟 Vault 暂不可用。 */
    public synchronized void credentialUnavailable() { credentialUnavailable = true; }

    /** 仅已配置 Token 返回凭据；不可用时写出约定故障，不计意外调用。 */
    public synchronized String revealCredential(String token) {
        credentialRevealCalls++;
        if (credentialUnavailable) {
            throw com.paytech.foundation.testsupport.api.MockHttpFault.raw(503, "{}");
        }
        if (!credentials.containsKey(token)) { unexpected++; throw new IllegalStateException("未配置凭据 Reveal"); }
        return credentials.get(token);
    }

    /** 凭据 Reveal 次数，与地址 Reveal 分开计数。 */
    public synchronized int credentialRevealCount() { return credentialRevealCalls; }

    /** 检查前置拒绝没有远端调用。 */
    public synchronized void assertNoCalls() { assertEquals(0, protectCalls + revealCalls); }

    /** 暴露 Reveal 次数以检查恢复查询没有再次读取地址。 */
    public synchronized int revealCount() { return revealCalls; }

    /** 在清理前检查未预期调用。 */
    public synchronized void assertExpected() { assertEquals(0, unexpected); }

    /** 每例清理响应配置及计数。 */
    @Override
    public synchronized void clear() {
        tokens.clear(); values.clear(); credentials.clear(); credentialUnavailable = false;
        protectCalls = 0; revealCalls = 0; credentialRevealCalls = 0; unexpected = 0;
    }
}
