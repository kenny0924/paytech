package com.paytech.acquiring.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.acquiring.test.mocker.external.CheckoutRiskMocker;
import com.paytech.risk.decision.api.evaluation.RiskDecisionApi;
import com.paytech.risk.decision.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.decision.api.evaluation.response.EvaluateDecisionResponseBody;

import lombok.AllArgsConstructor;

/** 独立 Mock 服务直接实现 Risk 统一评估合同；Risk 没有 Context Path，不加路径前缀。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockRiskDecisionApi implements RiskDecisionApi {
    private final CheckoutRiskMocker risk;

    /** 委托 Mocker 取出本次响应；按配置延迟，故障时写出与 Problem Body 一致的 Request ID。 */
    @Override
    public EvaluateDecisionResponseBody evaluate(EvaluateDecisionRequestBody requestBody) {
        CheckoutRiskMocker.Reply reply = risk.next(requestBody);
        delay(reply);
        if (reply.isFault()) {
            var attributes = (ServletRequestAttributes)RequestContextHolder.currentRequestAttributes();
            attributes.getResponse().setHeader("X-Request-Id", CheckoutRiskMocker.ERROR_REQUEST_ID);
            throw reply.fault();
        }
        return reply.body();
    }

    /** 模拟 Risk 处理耗时，用于超过 Commerce 的读取超时。 */
    static void delay(CheckoutRiskMocker.Reply reply) {
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
