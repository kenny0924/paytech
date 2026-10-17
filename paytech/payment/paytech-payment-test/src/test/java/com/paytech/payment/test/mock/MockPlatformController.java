package com.paytech.payment.test.mock;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.payment.test.mocker.PlatformMocker;
import com.paytech.platform.support.api.standardapi.StandardApiCommandApi;
import com.paytech.platform.support.api.standardapi.request.ExecuteStandardApiRequestBody;
import com.paytech.platform.support.api.standardapi.response.ExecuteStandardApiResponseBody;

import lombok.AllArgsConstructor;

/** 仅存在于独立测试 HTTP 服务的 Platform 标准 API 执行契约实现。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockPlatformController implements StandardApiCommandApi {
    private final PlatformMocker platform;

    /** 委托 PlatformMocker 返回预设 DTO。 */
    @Override
    public ExecuteStandardApiResponseBody executeStandardApi(String standardApiCode, ExecuteStandardApiRequestBody body) {
        return platform.execute(standardApiCode, body);
    }
}
