package com.paytech.platform.support.test.notification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private boolean protectUnavailable;
    private String credentialToken;
    private Integer credentialProtectStatus;
    private final List<String> protectedCredentials = new ArrayList<>();
    private final List<String> credentialKeys = new ArrayList<>();

    /** 明确准备每个合成邮箱的稳定 Token。 */
    public synchronized void allow(String email, String token) { tokens.put(email, token); values.put(token, email); }

    /** 仅已配置地址返回 Protect DTO。 */
    public synchronized String protect(String value) {
        protectCalls++;
        if (protectUnavailable) {
            throw com.paytech.foundation.testsupport.api.MockHttpFault.disconnect();
        }
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

    /** 之后的地址 Protect 断开连接，模拟受理时 Vault 不可达。 */
    public synchronized void protectUnavailable() { protectUnavailable = true; }

    /** 地址 Protect 次数。 */
    public synchronized int protectCount() { return protectCalls; }

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

    /** 下一次发送账号密钥 Protect 返回该合成 Token。 */
    public synchronized void expectCredentialProtect(String token) {
        credentialToken = token;
        credentialProtectStatus = null;
    }

    /** 之后的密钥 Protect 返回该 HTTP 状态（如 403 未授权、503 不可用），不计意外调用。 */
    public synchronized void credentialProtectFails(int status) { credentialProtectStatus = status; }

    /** 密钥 Protect：记录合成密钥 JSON 与幂等键供断言；未配置时作为意外调用失败。 */
    public synchronized String protectCredential(String key, String value) {
        if (credentialProtectStatus != null) {
            throw com.paytech.foundation.testsupport.api.MockHttpFault.raw(credentialProtectStatus, "{}");
        }
        if (credentialToken == null) { unexpected++; throw new IllegalStateException("未配置密钥 Protect"); }
        protectedCredentials.add(value);
        credentialKeys.add(key);
        String token = credentialToken;
        credentialToken = null;
        return token;
    }

    /** 已 Protect 的合成密钥 JSON，按调用顺序。 */
    public synchronized List<String> protectedCredentials() { return List.copyOf(protectedCredentials); }

    /** 密钥 Protect 使用的幂等键，按调用顺序。 */
    public synchronized List<String> credentialProtectKeys() { return List.copyOf(credentialKeys); }

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
        tokens.clear(); values.clear(); credentials.clear(); credentialUnavailable = false; protectUnavailable = false;
        credentialToken = null; credentialProtectStatus = null; protectedCredentials.clear(); credentialKeys.clear();
        protectCalls = 0; revealCalls = 0; credentialRevealCalls = 0; unexpected = 0;
    }
}
