package com.paytech.client.master.test.mock.external.commercial;

import java.util.List;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.client.master.test.mocker.commercial.CommercialAgreementPartyMocker;
import com.paytech.commercial.api.agreement.AgreementPartyQueryApi;
import com.paytech.commercial.api.agreement.response.AgreementPartyUsageResponseBody;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/** commercial 合同参与方引用查询的测试 HTTP 实现；client-master 必须以 client-master-service 调用。 */
@RestController
@TestComponent
public class MockCommercialAgreementPartyApi implements AgreementPartyQueryApi {
    private final CommercialAgreementPartyMocker mocker;

    /** 与用例共享配置与记录。 */
    public MockCommercialAgreementPartyApi(CommercialAgreementPartyMocker mocker) {
        this.mocker = mocker;
    }

    /** 返回本例配置的引用结果或断连。 */
    @Override
    public AgreementPartyUsageResponseBody exists(String callerService, List<String> partyIds, List<String> partyProfileIds) {
        if (!"client-master-service".equals(callerService)) {
            throw new IllegalStateException("意外的调用方: " + callerService);
        }
        var attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        attributes.getResponse().setHeader("X-Request-Id", "client-master-commercial-test");
        if (Boolean.FALSE.equals(mocker.available())) {
            mocker.usage(partyIds, partyProfileIds);
            throw MockHttpFault.disconnect();
        }
        var reply = mocker.usage(partyIds, partyProfileIds);
        if (reply == null) {
            throw new IllegalStateException("未配置的 commercial 调用");
        }
        return reply;
    }
}
