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
    private int protectCalls;
    private int revealCalls;
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

    /** 检查前置拒绝没有远端调用。 */
    public synchronized void assertNoCalls() { assertEquals(0, protectCalls + revealCalls); }

    /** 暴露 Reveal 次数以检查恢复查询没有再次读取地址。 */
    public synchronized int revealCount() { return revealCalls; }

    /** 在清理前检查未预期调用。 */
    public synchronized void assertExpected() { assertEquals(0, unexpected); }

    /** 每例清理响应配置及计数。 */
    @Override
    public synchronized void clear() { tokens.clear(); values.clear(); protectCalls = 0; revealCalls = 0; unexpected = 0; }
}
