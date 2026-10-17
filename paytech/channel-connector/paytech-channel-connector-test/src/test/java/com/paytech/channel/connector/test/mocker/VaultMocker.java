package com.paytech.channel.connector.test.mocker;

import static org.junit.jupiter.api.Assertions.*;

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
 * 按账号 Token 配置合成 Vault Reveal DTO：只接受 channel-connector 以 CHANNEL_EXECUTION 用途读取已配置的账号，其他请求都使测试失败。
 */
@TestComponent
public class VaultMocker implements Clear {
    private static final String CALLER = "channel-connector-service";

    private final Map<String, RevealResponse> responses = new HashMap<>();
    private final Map<String, MockHttpFault> faults = new HashMap<>();
    private final Map<String, String> errorRequestIds = new HashMap<>();
    private final List<String> calls = new ArrayList<>();
    private final List<String> unexpected = new ArrayList<>();

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

    /** HTTP Mock 实际调用此处，测试只记录合成 Token，不记录 Payload。 */
    public synchronized RevealResponse reveal(String caller, String token, String purpose) {
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

    /** channel-connector 从不 Protect；收到即记为意外调用。 */
    public synchronized ProtectResponse protect(String caller, ProtectRequest request) {
        unexpected.add("protect");
        throw new AssertionError("channel-connector 不应调用 Vault Protect");
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

    /** 候选查询和前置失败不得触发秘密读取。 */
    public synchronized void assertNoCalls() {
        assertEquals(0, calls.size());
    }

    /** 每例必须从完全空的 Mock 状态开始。 */
    public synchronized void assertEmpty() {
        assertTrue(responses.isEmpty() && faults.isEmpty() && errorRequestIds.isEmpty() && calls.isEmpty()
            && unexpected.isEmpty());
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
        calls.clear();
        unexpected.clear();
    }
}
