package com.paytech.payment.test.mocker;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.payment.api.card.request.RevealChannelCardMaterialRequestBody;
import com.paytech.payment.api.card.response.RevealChannelCardMaterialResponseBody;
import com.paytech.platform.support.api.standardapi.request.ExecuteStandardApiRequestBody;
import com.paytech.platform.support.api.standardapi.response.ExecuteStandardApiResponseBody;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Platform 标准 API 执行的响应 DTO：按用例配置返回 executeStatus/outputs 或 Problem 故障。
 *
 * <p>
 * 与真实 Platform 一样，收到含 card.token 的请求时先经真实 HTTP 回调 Payment 读卡接口（Caller platform-support-service），
 * 只记录读卡结果的卡号末四位与 CVV 是否存在，不保存卡值；读卡失败时按真实 Platform 返回 503 INTEGRATION_INITIALIZATION_FAILED。
 */
@TestComponent
@RequiredArgsConstructor
public class PlatformMocker implements Clear {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Environment environment;
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

    /** HTTP Mock 实际调用此处：先读卡，再按配置响应。 */
    public ExecuteStandardApiResponseBody execute(String standardApiCode, ExecuteStandardApiRequestBody body) {
        String revealed = revealCard(body.getInput());
        synchronized (this) {
            calls.add(new Call(standardApiCode, body.getIntegrationCode(), body.getInput(), revealed));
            if (revealed != null && revealed.startsWith("REJECTED")) {
                throw MockHttpFault.json(503, Map.of("type", "about:blank", "title", "Service Unavailable",
                    "detail", "外部 API 初始化失败", "code", "INTEGRATION_INITIALIZATION_FAILED"));
            }
            if (fault != null) {
                throw fault;
            }
            if (response == null) {
                unexpected++;
                throw new AssertionError("未配置的 Platform 执行");
            }
            return response;
        }
    }

    /** 按真实 Platform 的 PAYMENT_CARD 初始化读卡；返回“末四位/CVV 是否存在”或拒绝状态码。 */
    String revealCard(JsonNode input) {
        if (!input.path("card").path("token").isString()) {
            return null;
        }
        var request = new RevealChannelCardMaterialRequestBody();
        request.setPaymentInstrumentToken(input.path("card").path("token").asString());
        request.setPaymentAttemptId(input.path("payment").path("attemptId").asString());
        request.setPurpose("CHANNEL_EXECUTION");
        try {
            var card = RestClient.create().post()
                .uri("http://127.0.0.1:" + environment.getProperty("local.server.port")
                    + "/payment/payment-card/v1/channel-card-materials/reveal")
                .header("X-Caller-Service", "platform-support-service").contentType(MediaType.APPLICATION_JSON)
                .body(request).retrieve().body(RevealChannelCardMaterialResponseBody.class);
            return card.getPan().substring(card.getPan().length() - 4) + "/" + (card.getCvv() != null);
        } catch (RestClientResponseException rejected) {
            return "REJECTED " + rejected.getStatusCode().value();
        }
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

    /** 一次执行请求：标准 API、接入方、输入与读卡结果摘要。 */
    public record Call(String standardApiCode, String integrationCode, JsonNode input, String revealed) {
    }
}
