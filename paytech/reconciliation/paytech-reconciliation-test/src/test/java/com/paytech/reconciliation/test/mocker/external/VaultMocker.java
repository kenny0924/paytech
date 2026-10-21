package com.paytech.reconciliation.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.vault.client.internal.wire.ProtectRequest;
import com.paytech.vault.client.internal.wire.ProtectResponse;

/**
 * Vault Protect 的 DTO Mock：只接受 reconciliation-service 以预设的数据类型 + 用途发起的 Protect，按顺序返回预设 Token
 * <li>同一幂等键重放返回该键首次分配的 Token（与 Vault REFERENCE 合同一致）；新键依次消费，耗尽或未配置即意外调用。</li>
 * <li>调用记录不保存明文账号，只保存其 SHA-256 与结构字段；Reveal 一律为意外调用（对账不读明文）。</li>
 */
@TestComponent
public class VaultMocker implements Clear {
    /** 期望的调用服务名（vault.client.caller-service）。 */
    public static final String CALLER = "reconciliation-service";

    private final Map<String, ArrayDeque<String>> protectTokens = new HashMap<>();

    private final Map<String, String> tokensByKey = new HashMap<>();

    private final List<ProtectCall> protects = new ArrayList<>();

    private final List<String> unexpected = new ArrayList<>();

    private MockHttpFault protectFault;

    /** 为指定数据类型与用途按顺序准备 Protect 返回的 Token。 */
    public synchronized void expectProtect(String dataType, String purpose, String... tokens) {
        protectTokens.computeIfAbsent(dataType + "|" + purpose, key -> new ArrayDeque<>()).addAll(List.of(tokens));
    }

    /** 让下一次匹配的 Protect 返回 HTTP 故障（仍记录调用）。 */
    public synchronized void failProtect(MockHttpFault fault) {
        protectFault = fault;
    }

    /** Mock Controller 调用：核对调用者与预期，返回 Token 或故障。 */
    public synchronized ProtectResponse protect(String caller, String idempotencyKey, ProtectRequest request) {
        ArrayDeque<String> queue = protectTokens.get(request.dataType() + "|" + request.purpose());
        if (!CALLER.equals(caller) || queue == null) {
            unexpected.add("protect:" + request.dataType() + "|" + request.purpose());
            throw new AssertionError("Vault Protect 未配置或调用者 / 数据类型 / 用途不符");
        }
        protects.add(new ProtectCall(idempotencyKey, request.dataType(), request.tokenMode(), request.purpose(),
            sha256(request.value())));
        if (protectFault != null) {
            throw protectFault;
        }
        String token = tokensByKey.get(idempotencyKey);
        if (token == null) {
            token = queue.poll();
            if (token == null) {
                unexpected.add("protect-exhausted");
                throw new AssertionError("Vault Protect 预设 Token 已耗尽");
            }
            tokensByKey.put(idempotencyKey, token);
        }
        return new ProtectResponse(token);
    }

    /** Mock Controller 调用：对账服务不得 Reveal。 */
    public synchronized void unexpectedReveal() {
        unexpected.add("reveal");
        throw new AssertionError("reconciliation 不得调用 Vault Reveal");
    }

    /** 本例 Protect 调用记录。 */
    public synchronized List<ProtectCall> protectCalls() {
        return List.copyOf(protects);
    }

    /** 零调用路径断言。 */
    public synchronized void assertNoCalls() {
        assertTrue(protects.isEmpty(), "不应调用 Vault Protect");
    }

    /** 每例开始时必须为空基线。 */
    public synchronized void assertEmpty() {
        assertTrue(protectTokens.isEmpty() && tokensByKey.isEmpty() && protects.isEmpty() && unexpected.isEmpty()
            && protectFault == null, "Vault Mock 应处于空基线");
    }

    /** 即使生产代码捕获了 Mock 错误，也由 AfterEach 让测试失败。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertTrue(unexpected.isEmpty(), "存在意外 Vault 操作：" + unexpected);
    }

    /** 由 Foundation TestBase 在每例结束统一调用。 */
    @Override
    public synchronized void clear() {
        protectTokens.clear();
        tokensByKey.clear();
        protects.clear();
        unexpected.clear();
        protectFault = null;
    }

    /** 原值 UTF-8 字节的 SHA-256 hex。 */
    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * 一次 Protect 调用的稳定摘要；不保存原值
     *
     * @param idempotencyKey 请求头中的幂等键
     * @param dataType 数据类型
     * @param tokenMode Token 模式
     * @param purpose 用途
     * @param valueSha256 原值 SHA-256 hex
     */
    public record ProtectCall(String idempotencyKey, String dataType, String tokenMode, String purpose,
        String valueSha256) {
    }
}
