package com.paytech.client.contract.test.mock.external.onboarding;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.contract.test.mocker.onboarding.OnboardingOwnerMocker;
import com.paytech.platform.support.api.tenantprojection.TenantProjectionApi;
import com.paytech.platform.support.api.tenantprojection.request.ProvisionRequestBody;
import com.paytech.platform.support.api.tenantprojection.request.StatusRequestBody;
import com.paytech.platform.support.api.tenantprojection.response.ProjectionResponseBody;

/** Platform PLT-I17 工作区投影的测试 HTTP 实现；/platform-support 前缀由 MockPlatformAuthenticationApi 统一配置。 */
@RestController
@TestComponent
public class MockPlatformTenantProjectionApi implements TenantProjectionApi {
    private final OnboardingOwnerMocker mocker;

    /** 与用例共享配置与记录。 */
    public MockPlatformTenantProjectionApi(OnboardingOwnerMocker mocker) {
        this.mocker = mocker;
    }

    /** 首版开通不属于审核结果链路。 */
    @Override
    public ProjectionResponseBody provision(ProvisionRequestBody request) {
        throw MockPlatformAuthenticationApi.unexpected();
    }

    /** 状态投影。 */
    @Override
    public ProjectionResponseBody projectStatus(StatusRequestBody request) {
        MockPlatformAuthenticationApi.requestId();
        return MockPlatformAuthenticationApi.required(mocker.projectStatus(request));
    }

    /** 当前位置。 */
    @Override
    public ProjectionResponseBody projection(String tenantType, String ownerRefId) {
        MockPlatformAuthenticationApi.requestId();
        return MockPlatformAuthenticationApi.required(mocker.projection(ownerRefId));
    }
}
