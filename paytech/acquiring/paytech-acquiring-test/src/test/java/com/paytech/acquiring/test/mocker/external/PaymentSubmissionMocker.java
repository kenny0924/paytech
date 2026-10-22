package com.paytech.acquiring.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.payment.api.execution.response.GetSubmissionResponseBody;

/** Payment EX-I06 按来源提交查询的 HTTP Mock：只配置响应 DTO、未找到或故障，并记录查询坐标。 */
@TestComponent
public class PaymentSubmissionMocker implements Clear {
    private GetSubmissionResponseBody response;
    private MockHttpFault failure;
    private final List<Call> calls = new ArrayList<>();

    /** EX-I06 只读查询。 */
    public static final String GET = "GET";

    /** 3DS 全链路的刷新（处理中时 Payment 可能查单确认）。 */
    public static final String REFRESH = "REFRESH";

    /**
     * 一次查询的坐标
     *
     * @param intentId 路径中的 Intent
     * @param sourceSubmissionId 路径中的来源提交 ID
     * @param clientId 查询参数或请求体中的 Client ID
     * @param operation 调用的 Payment 接口：{@value #GET} 或 {@value #REFRESH}
     */
    public record Call(String intentId, String sourceSubmissionId, String clientId, String operation) {}

    /** 配置查询响应 DTO。 */
    public synchronized void mockResponse(GetSubmissionResponseBody value) {
        response = value;
        failure = null;
    }

    /** 按 Payment 合同返回 404 Problem。 */
    public synchronized void mockNotFound() {
        failure = MockHttpFault.json(HttpStatus.NOT_FOUND.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
            "Not Found", "支付提交不存在", "NOT_FOUND", "payment-test", "2026-09-08T00:00:00.000000Z", null));
    }

    /** 配置网络或下游故障。 */
    public synchronized void mockFailure(MockHttpFault value) {
        failure = value;
    }

    /** 记录调用后返回 DTO 或故障；查询与刷新共用同一响应配置。 */
    public synchronized GetSubmissionResponseBody answer(String intentId, String sourceSubmissionId, String clientId,
        String operation) {
        calls.add(new Call(intentId, sourceSubmissionId, clientId, operation));
        if (failure != null) {
            throw failure;
        }
        assertNotNull(response, "未配置支付查询响应 DTO");
        return response;
    }

    /** 查询调用记录。 */
    public synchronized List<Call> calls() {
        return List.copyOf(calls);
    }

    /** 本例查询响应：给定状态，金额与时间取固定合成值，其余字段由调用方补充。 */
    public static GetSubmissionResponseBody submission(long intentId, long sourceSubmissionId, String attemptId,
        String status) {
        var body = new GetSubmissionResponseBody();
        body.setIntentId(Long.toString(intentId));
        body.setSourceSubmissionId(Long.toString(sourceSubmissionId));
        body.setAttemptId(attemptId);
        body.setPaymentMethodCode("CARD_TEST");
        body.setStatus(status);
        body.setCapturedAmount("SUCCEEDED".equals(status) ? "1.00" : "0.00");
        body.setCurrency("USD");
        body.setObservedAt("2026-09-08T00:00:01Z");
        return body;
    }

    /** 每例清除响应、故障和调用记录。 */
    @Override
    public synchronized void clear() {
        response = null;
        failure = null;
        calls.clear();
    }
}
