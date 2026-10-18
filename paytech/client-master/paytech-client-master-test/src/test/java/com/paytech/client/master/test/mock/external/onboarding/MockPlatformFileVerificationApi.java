package com.paytech.client.master.test.mock.external.onboarding;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.master.test.mocker.onboarding.OnboardingOwnerMocker;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileVerificationApi;
import com.paytech.platform.support.api.onboardingfiles.response.FileVerificationResponseBody;

/** Platform 文件核验的测试 HTTP 实现；路径前缀由 MockPlatformAuthenticationApi 统一配置。 */
@RestController
@TestComponent
public class MockPlatformFileVerificationApi implements OnboardingFileVerificationApi {
    private final OnboardingOwnerMocker mocker;

    /** 与用例共享配置与记录。 */
    public MockPlatformFileVerificationApi(OnboardingOwnerMocker mocker) {
        this.mocker = mocker;
    }

    /** 返回本例配置的文件证据。 */
    @Override
    public FileVerificationResponseBody verify(String caller, String fileId, String expectedApplicationId,
        String expectedSubjectPartyId, String expectedClientId, String requestingAccountId, String purpose) {
        MockPlatformAuthenticationApi.requestId();
        return MockPlatformAuthenticationApi.required(mocker.file(fileId));
    }
}
