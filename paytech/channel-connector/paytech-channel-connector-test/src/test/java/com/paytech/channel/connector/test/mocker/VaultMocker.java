package com.paytech.channel.connector.test.mocker;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.channel.connector.domain.ChannelAccountConfiguration;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.vault.client.internal.wire.ProtectRequest;
import com.paytech.vault.client.internal.wire.ProtectResponse;
import com.paytech.vault.client.internal.wire.RevealResponse;

import tools.jackson.databind.json.JsonMapper;

/**
 * 按账号 Token 配置合成 Vault Reveal DTO：只接受 channel-connector 以 CHANNEL_EXECUTION 用途读取已配置的账号，其他请求都使测试失败
 * <li>同时承载渠道协议执行的账号读取（2026-10-20 自 Platform ChannelAccountVaultMocker 合并）：登记账号配置、Vault 故障与响应延迟。</li>
 * <li>调用记录只保存 Token，不保存配置原文。</li>
 */
@TestComponent
public class VaultMocker implements Clear {
    private static final String CALLER = "channel-connector-service";

    private final Map<String, RevealResponse> responses = new HashMap<>();
    private final Map<String, MockHttpFault> faults = new HashMap<>();
    private final Map<String, String> errorRequestIds = new HashMap<>();
    private final Map<String, Long> delays = new HashMap<>();
    private final List<String> calls = new ArrayList<>();
    private final List<String> unexpected = new ArrayList<>();
    private final Map<String, ArrayDeque<String>> protectTokens = new HashMap<>();
    private final Map<String, String> protectByKey = new HashMap<>();
    private final List<ProtectCall> protects = new ArrayList<>();
    private MockHttpFault protectFault;
    private String protectErrorRequestId;

    /** 按冻结账号生成有明确归属的合成配置。 */
    public void configure(ChannelDataMocker.Fixture fixture) {
        var config = new ChannelAccountConfiguration();
        config.setSchemaVersion(1);
        config.setChannelCode(fixture.getChannel().getChannelCode());
        config.setChannelAccountId(fixture.getAccount().getChannelAccountId().toString());
        config.setEntityNo(fixture.getAccount().getEntityNo());
        config.setParameters(Map.of("outletId", "synthetic-outlet"));
        config.setCredentials(Map.of("apiKey", "synthetic-api-key"));
        respond(fixture.getAccount().getAccountVaultToken(), JsonMapper.builder().build().writeValueAsString(config));
    }

    /** 保存独立的正常响应 DTO，也允许负例提供非法 Payload。 */
    public synchronized void respond(String token, String value) {
        responses.put(token, new RevealResponse(value));
    }

    /**
     * 合成的 43 位 Base64URL 引用 Token
     *
     * @param seed 生成种子，同一种子得到同一 Token
     * @return 合成 Token
     */
    public static String token(String seed) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * 登记 Token 对应的账号配置原文（渠道协议执行的 CHANNEL_ACCOUNT_CONFIG 初始化读取）
     *
     * @param token 账号 Vault Token
     * @param configuration 账号配置 JSON 原文
     */
    public synchronized void allow(String token, String configuration) {
        respond(token, configuration);
    }

    /**
     * 登记 Token 的 Vault 故障状态码
     *
     * @param token 账号 Vault Token
     * @param status HTTP 状态码
     */
    public synchronized void fail(String token, int status) {
        fault(token, MockHttpFault.json(status, Map.of("code", "VAULT_UNAVAILABLE", "message", "Vault 不可用")));
    }

    /**
     * 登记 Token 的响应延迟，用于流程预算耗尽场景
     *
     * @param token 账号 Vault Token
     * @param millis 返回前等待的毫秒数
     */
    public synchronized void delay(String token, long millis) {
        delays.put(token, millis);
    }

    /**
     * HTTP Mock 实际调用此处：按登记延迟等待（不持有锁）后返回预设响应
     *
     * @param caller 调用方服务名
     * @param token 账号 Vault Token
     * @param purpose 读取用途
     * @return 预设 Reveal 响应
     */
    public RevealResponse reveal(String caller, String token, String purpose) {
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
        return answer(caller, token, purpose);
    }

    /** 测试只记录合成 Token，不记录 Payload。 */
    synchronized RevealResponse answer(String caller, String token, String purpose) {
        if (!CALLER.equals(caller) || !"CHANNEL_EXECUTION".equals(purpose)
            || !(responses.containsKey(token) || faults.containsKey(token))) {
            unexpected.add("unconfigured-reveal");
            throw new AssertionError("Vault 请求未配置或调用者/用途不符");
        }
        calls.add(token);
        if (faults.containsKey(token)) {
            throw faults.get(token);
        }
        return responses.get(token);
    }

    // ---- CC-B 追加：账号配置写入的 Protect ----

    /**
     * 为指定数据类型与用途按顺序准备 Protect 返回的 Token
     * <li>同一幂等键重放返回该键首次分配的 Token（与 Vault REFERENCE 合同一致）；新键依次消费，耗尽即意外调用。</li>
     */
    public synchronized void expectProtect(String dataType, String purpose, String... tokens) {
        protectTokens.computeIfAbsent(dataType + "|" + purpose, key -> new ArrayDeque<>()).addAll(List.of(tokens));
    }

    /** 让下一次匹配的 Protect 返回 HTTP 故障；Problem 的 Request ID 同步写入响应 Header。 */
    public synchronized void protectProblem(int status, ApiProblemResponse problem) {
        protectFault = MockHttpFault.json(status, problem);
        protectErrorRequestId = problem.requestId();
    }

    /** Protect 故障的 Request ID，供测试 HTTP 层写出 Header。 */
    public synchronized String protectErrorRequestId() {
        return protectFault == null ? null : protectErrorRequestId;
    }

    /** HTTP Mock 实际调用此处：核对调用者与预期，记录不含原值的调用摘要。 */
    public synchronized ProtectResponse protect(String caller, String idempotencyKey, ProtectRequest request) {
        var queue = protectTokens.get(request.dataType() + "|" + request.purpose());
        if (!CALLER.equals(caller) || queue == null) {
            unexpected.add("unconfigured-protect");
            throw new AssertionError("Vault Protect 未配置或调用者/数据类型/用途不符");
        }
        protects.add(ProtectCall.of(idempotencyKey, request));
        if (protectFault != null) {
            throw protectFault;
        }
        String token = protectByKey.get(idempotencyKey);
        if (token == null) {
            token = queue.poll();
            if (token == null) {
                unexpected.add("protect-exhausted");
                throw new AssertionError("Vault Protect 预设 Token 已耗尽");
            }
            protectByKey.put(idempotencyKey, token);
        }
        return new ProtectResponse(token);
    }

    /** 本例 Protect 调用记录。 */
    public synchronized List<ProtectCall> protectCalls() {
        return List.copyOf(protects);
    }

    /**
     * 一次 Protect 调用的稳定摘要；不保存原值，只保存其 SHA-256 与非凭据的结构字段
     *
     * @param idempotencyKey 请求头中的幂等键
     * @param dataType 数据类型
     * @param tokenMode Token 模式
     * @param purpose 用途
     * @param valueSha256 原值 UTF-8 字节的 SHA-256 hex
     * @param structure 原值的归属字段、参数与凭据键名（不含任何参数或凭据值）
     */
    public record ProtectCall(String idempotencyKey, String dataType, String tokenMode, String purpose,
        String valueSha256, Map<String, Object> structure) {

        /** 由请求摘要构造，原值只用于计算摘要与读取结构。 */
        static ProtectCall of(String idempotencyKey, ProtectRequest request) {
            return new ProtectCall(idempotencyKey, request.dataType(), request.tokenMode(), request.purpose(),
                sha256(request.value()), structure(request.value()));
        }

        /** 原值 SHA-256 hex。 */
        static String sha256(String value) {
            try {
                return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new AssertionError(e);
            }
        }

        /** 结构字段：schemaVersion、归属三字段与参数 / 凭据键名。 */
        static Map<String, Object> structure(String value) {
            var tree = JsonMapper.builder().build().readTree(value);
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("schemaVersion", tree.path("schemaVersion").asInt());
            result.put("channelCode", tree.path("channelCode").asString());
            result.put("channelAccountId", tree.path("channelAccountId").asString());
            result.put("entityNo", tree.path("entityNo").asString());
            result.put("parameterKeys", new java.util.TreeSet<>(tree.path("parameters").propertyNames()));
            result.put("credentialKeys", new java.util.TreeSet<>(tree.path("credentials").propertyNames()));
            return result;
        }
    }

    /** 用真实 HTTP 状态或畸形报文验证凭据读取失败，不替换生产客户端。 */
    public synchronized void fault(String token, MockHttpFault fault) {
        faults.put(token, fault);
    }

    /** 正式 Problem 的 Header 和 Body 必须使用同一个 Request ID。 */
    public synchronized void problem(String token, ApiProblemResponse problem) {
        fault(token, MockHttpFault.json(403, problem));
        errorRequestIds.put(token, problem.requestId());
    }

    /** 供测试 HTTP 层写出协议必需 Header，不读取明文配置。 */
    public synchronized String errorRequestId(String token) {
        return errorRequestIds.get(token);
    }

    /** 验证只调用冻结账号一次，错误信息不输出 Token。 */
    public synchronized void assertOnly(String token) {
        assertEquals(1, calls.size());
        assertTrue(calls.getFirst().equals(token), "读取了非冻结账号");
    }

    /** 本例 Reveal 次数（含按配置返回故障的读取）。 */
    public synchronized int revealCount() {
        return calls.size();
    }

    /** 候选查询和前置失败不得触发秘密读取。 */
    public synchronized void assertNoCalls() {
        assertEquals(0, calls.size());
    }

    /** 每例必须从完全空的 Mock 状态开始。 */
    public synchronized void assertEmpty() {
        assertTrue(responses.isEmpty() && faults.isEmpty() && errorRequestIds.isEmpty() && delays.isEmpty() && calls.isEmpty()
            && unexpected.isEmpty() && protectTokens.isEmpty() && protectByKey.isEmpty() && protects.isEmpty()
            && protectFault == null);
    }

    /** 即使业务捕获 HTTP 故障，也不能掩盖意外调用。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertTrue(unexpected.isEmpty(), "存在意外 Vault 操作");
    }

    /** 由 Foundation 的 AfterEach 统一回收响应与调用记录。 */
    @Override
    public synchronized void clear() {
        responses.clear();
        faults.clear();
        errorRequestIds.clear();
        delays.clear();
        calls.clear();
        unexpected.clear();
        protectTokens.clear();
        protectByKey.clear();
        protects.clear();
        protectFault = null;
        protectErrorRequestId = null;
    }
}
