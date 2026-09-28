package com.paytech.commerce.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.client.contract.api.checkout.CheckoutEligibilityApi;
import com.paytech.client.contract.api.checkout.CheckoutEligibilityModels.PrecheckResponseBody;
import com.paytech.commerce.test.mocker.external.CctCheckoutMocker;
import com.paytech.commerce.test.mocker.external.CctCheckoutMocker.Call;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/** 只装配到独立 Mock 服务；路由与参数绑定直接继承 CCT 的正式 API 合同。 */
@RestController
@TestComponent
public final class MockCheckoutEligibilityApi implements CheckoutEligibilityApi {
    private final CctCheckoutMocker mocker;

    /** 与测试用例使用同一份响应配置和调用记录。 */
    public MockCheckoutEligibilityApi(CctCheckoutMocker mocker) {
        this.mocker = mocker;
    }

    /** 返回本例配置的 DTO；协议故障由公共 Mock HTTP 服务在传输边界处理。 */
    @Override
    public PrecheckResponseBody precheck(String callerService, String clientId, String consumerCountryCode,
        String currency) {
        var attributes = (ServletRequestAttributes)RequestContextHolder.currentRequestAttributes();
        attributes.getResponse().setHeader("X-Request-Id", "cct-checkout-test");
        var call = new Call(Long.parseLong(clientId), consumerCountryCode, currency, callerService,
            attributes.getRequest().getHeader("X-Request-Id"));
        var reply = mocker.answer(call);
        if (reply.isDisconnect()) {
            throw MockHttpFault.disconnect();
        }
        if (reply.getRawBody() != null) {
            throw MockHttpFault.raw(reply.getStatus(), reply.getRawBody());
        }
        if (reply.getStatus() >= 400) {
            throw MockHttpFault.json(reply.getStatus(), reply.getBody());
        }
        return (PrecheckResponseBody)reply.getBody();
    }
}
