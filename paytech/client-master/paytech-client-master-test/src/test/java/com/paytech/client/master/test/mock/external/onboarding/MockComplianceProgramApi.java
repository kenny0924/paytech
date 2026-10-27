package com.paytech.client.master.test.mock.external.onboarding;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.master.test.mocker.onboarding.OnboardingOwnerMocker;
import com.paytech.compliance.api.ProgramResolutionApi;
import com.paytech.compliance.api.response.ProgramResolutionResponseBody;
import com.paytech.foundation.testsupport.api.MockHttpFault;

import lombok.AllArgsConstructor;

/** compliance CP-1 程序解析的测试 HTTP 实现：按 主体类型:角色 返回本例配置的程序。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockComplianceProgramApi implements ProgramResolutionApi {
    private final OnboardingOwnerMocker mocker;

    /** 返回本例配置的程序或故障；未配置为意外调用。 */
    @Override
    public ProgramResolutionResponseBody resolve(String caller, String jurisdiction, String partyType, String roleType, String tier,
        String responsiblePartnerId) {
        MockPlatformAuthenticationApi.requestId();
        Object reply = mocker.program(jurisdiction, partyType, roleType, responsiblePartnerId);
        if (reply instanceof OnboardingOwnerMocker.Fault fault) {
            throw fault.disconnect() ? MockHttpFault.disconnect() : MockHttpFault.json(fault.status(), fault.problem());
        }
        return (ProgramResolutionResponseBody)MockPlatformAuthenticationApi.required(reply);
    }
}
