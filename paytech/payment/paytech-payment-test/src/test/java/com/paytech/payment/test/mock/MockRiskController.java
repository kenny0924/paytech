package com.paytech.payment.test.mock;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.payment.test.mocker.RiskMocker;
import com.paytech.risk.api.evaluation.RiskDecisionApi;
import com.paytech.risk.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.api.evaluation.response.EvaluateDecisionResponseBody;

import lombok.AllArgsConstructor;

/** 仅存在于独立测试 HTTP 服务的 Risk 统一评估实现。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockRiskController implements RiskDecisionApi {
    private final RiskMocker risk;

    /** 委托 RiskMocker 取出本次响应；按配置延迟，故障时写出与 Problem Body 一致的 Request ID。 */
    @Override
    public EvaluateDecisionResponseBody evaluate(EvaluateDecisionRequestBody requestBody) {
        RiskMocker.Reply reply = risk.next(requestBody);
        delay(reply);
        if (reply.isProblem()) {
            var attributes = (ServletRequestAttributes)RequestContextHolder.currentRequestAttributes();
            attributes.getResponse().setHeader("X-Request-Id", RiskMocker.ERROR_REQUEST_ID);
            throw reply.fault();
        }
        return reply.body();
    }

    /** 模拟 Risk 处理耗时，用于超过 Payment 的读取超时。 */
    static void delay(RiskMocker.Reply reply) {
        if (reply.delay().isZero()) {
            return;
        }
        try {
            Thread.sleep(reply.delay());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
