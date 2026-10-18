package com.paytech.client.master.test.mock.external.onboarding;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.master.test.mocker.onboarding.OnboardingOwnerMocker;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.compliance.api.MaterialRequirementsApi;
import com.paytech.compliance.api.MaterialRequirementsModels.RequirementsResponseBody;

/** Risk 通用材料要求的测试 HTTP 实现。 */
@RestController
@TestComponent
public class MockRiskOnboardingApi implements MaterialRequirementsApi {
    private final OnboardingOwnerMocker mocker;

    /** 与用例共享配置与记录。 */
    public MockRiskOnboardingApi(OnboardingOwnerMocker mocker) {
        this.mocker = mocker;
    }

    /** 返回本例配置的材料要求或故障。 */
    @Override
    public RequirementsResponseBody read(String caller, String qualificationType) {
        MockPlatformAuthenticationApi.requestId();
        Object reply = mocker.requirements(qualificationType);
        if (reply instanceof OnboardingOwnerMocker.Fault fault) {
            throw fault.disconnect() ? MockHttpFault.disconnect() : MockHttpFault.json(fault.status(), fault.problem());
        }
        return (RequirementsResponseBody)MockPlatformAuthenticationApi.required(reply);
    }
}
