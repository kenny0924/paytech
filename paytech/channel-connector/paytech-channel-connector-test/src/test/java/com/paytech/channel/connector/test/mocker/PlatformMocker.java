package com.paytech.channel.connector.test.mocker;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.platform.support.api.standardapi.request.ExecuteStandardApiRequestBody;
import com.paytech.platform.support.api.standardapi.response.ExecuteStandardApiResponseBody;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Platform 标准 API 执行的响应 DTO：按用例配置返回 executeStatus/outputs 或 Problem 故障，并记录每次请求。
 * <li>只记录合成输入；channel-connector 不读卡，读卡回调属于 Platform 与 Payment 之间（见 Payment 测试）。</li>
 */
@TestComponent
public class PlatformMocker implements Clear {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final List<Call> calls = new ArrayList<>();
    private ExecuteStandardApiResponseBody response;
    private MockHttpFault fault;
    private int unexpected;

    /** 配置 200 响应。 */
    public synchronized void respond(String executeStatus, String outputsJson) {
        response = new ExecuteStandardApiResponseBody(executeStatus, JSON.readTree(outputsJson));
        fault = null;
    }

    /** 配置 Problem 或网络故障。 */
    public synchronized void fail(MockHttpFault failure) {
        fault = failure;
        response = null;
    }

    /** HTTP Mock 实际调用此处：记录请求并按配置响应。 */
    public synchronized ExecuteStandardApiResponseBody execute(String standardApiCode, ExecuteStandardApiRequestBody body) {
        calls.add(new Call(standardApiCode, body.getIntegrationCode(), body.getInput()));
        if (fault != null) {
            throw fault;
        }
        if (response == null) {
            unexpected++;
            throw new AssertionError("未配置的 Platform 执行");
        }
        return response;
    }

    /** 本例执行请求。 */
    public synchronized List<Call> calls() {
        return List.copyOf(calls);
    }

    /** 唯一的一次执行请求。 */
    public synchronized Call only() {
        assertEquals(1, calls.size(), "Platform 执行次数");
        return calls.getFirst();
    }

    /** 每例必须从完全空的 Mock 状态开始。 */
    public synchronized void assertEmpty() {
        assertTrue(calls.isEmpty() && response == null && fault == null && unexpected == 0);
    }

    /** 每例结束检查没有未配置的执行。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的 Platform 执行");
    }

    @Override
    public synchronized void clear() {
        calls.clear();
        response = null;
        fault = null;
        unexpected = 0;
    }

    /** 一次执行请求：标准 API、接入方与输入。 */
    public record Call(String standardApiCode, String integrationCode, JsonNode input) {
    }
}
