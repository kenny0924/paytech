package com.paytech.client.master.test.mock.external.onboarding;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.master.test.mocker.onboarding.OnboardingOwnerMocker;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.risk.api.compliance.ClientReviewRegistrationApi;
import com.paytech.risk.api.compliance.request.ClientReviewClassificationsRequestBody;
import com.paytech.risk.api.compliance.request.ClientReviewRegistrationRequestBody;
import com.paytech.risk.api.compliance.response.ClientReviewCommandResponseBody;
import com.paytech.risk.api.compliance.response.ClientReviewRegistrationResponseBody;

/** Risk I01 审核登记与 I02 分类接收的测试 HTTP 实现；请求体经正式 @Valid 合同校验后记录。 */
@RestController
@TestComponent
public class MockRiskReviewRegistrationApi implements ClientReviewRegistrationApi {
    private final OnboardingOwnerMocker mocker;

    /** 与用例共享配置与记录。 */
    public MockRiskReviewRegistrationApi(OnboardingOwnerMocker mocker) {
        this.mocker = mocker;
    }

    /** 返回本例配置的登记结果或故障。 */
    @Override
    public ClientReviewRegistrationResponseBody register(String caller, ClientReviewRegistrationRequestBody request) {
        MockPlatformAuthenticationApi.requestId();
        Object reply = mocker.review(request);
        if (reply instanceof OnboardingOwnerMocker.Fault fault) {
            throw fault.disconnect() ? MockHttpFault.disconnect() : MockHttpFault.json(fault.status(), fault.problem());
        }
        return (ClientReviewRegistrationResponseBody)MockPlatformAuthenticationApi.required(reply);
    }

    /** 返回本例配置的分类接收结果或故障。 */
    @Override
    public ClientReviewCommandResponseBody receiveClassifications(String caller, String caseId,
        ClientReviewClassificationsRequestBody request) {
        MockPlatformAuthenticationApi.requestId();
        Object reply = mocker.classifications(caseId, request);
        if (reply instanceof OnboardingOwnerMocker.Fault fault) {
            throw fault.disconnect() ? MockHttpFault.disconnect() : MockHttpFault.json(fault.status(), fault.problem());
        }
        return (ClientReviewCommandResponseBody)MockPlatformAuthenticationApi.required(reply);
    }
}
