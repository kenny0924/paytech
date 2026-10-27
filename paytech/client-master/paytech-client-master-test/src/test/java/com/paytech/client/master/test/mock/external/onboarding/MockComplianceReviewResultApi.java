package com.paytech.client.master.test.mock.external.onboarding;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.master.test.mocker.onboarding.OnboardingOwnerMocker;
import com.paytech.compliance.api.ClientReviewResultApi;
import com.paytech.compliance.api.response.ClientReviewResultResponseBody;
import com.paytech.foundation.testsupport.api.MockHttpFault;

import lombok.AllArgsConstructor;

/** compliance CP-4 审核结果的测试 HTTP 实现：返回本例按申请配置的结果；未配置为意外调用。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockComplianceReviewResultApi implements ClientReviewResultApi {
    private final OnboardingOwnerMocker mocker;

    /** 返回本例配置的结果或故障。 */
    @Override
    public ClientReviewResultResponseBody result(String caller, String applicationId) {
        MockPlatformAuthenticationApi.requestId();
        Object reply = mocker.reviewResult(applicationId);
        if (reply instanceof OnboardingOwnerMocker.Fault fault) {
            throw fault.disconnect() ? MockHttpFault.disconnect() : MockHttpFault.json(fault.status(), fault.problem());
        }
        return (ClientReviewResultResponseBody)MockPlatformAuthenticationApi.required(reply);
    }
}
