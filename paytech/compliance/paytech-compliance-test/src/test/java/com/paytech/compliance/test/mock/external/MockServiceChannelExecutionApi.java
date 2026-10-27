package com.paytech.compliance.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.channel.connector.api.serviceexecution.ServiceChannelExecutionApi;
import com.paytech.channel.connector.api.serviceexecution.request.ExecuteServiceChannelRequestBody;
import com.paytech.channel.connector.api.serviceexecution.response.ServiceChannelExecutionResponseBody;
import com.paytech.compliance.test.mocker.external.ScreeningMocker;

import lombok.AllArgsConstructor;

/** 在独立 Mock 服务实现 channel-connector 服务渠道执行合同 /channel-connector/v1/service-executions。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockServiceChannelExecutionApi implements ServiceChannelExecutionApi {
    private final ScreeningMocker mocker;

    /** 委托 Mocker 应答。 */
    @Override
    public ServiceChannelExecutionResponseBody execute(String callerService, ExecuteServiceChannelRequestBody requestBody) {
        markRequestId();
        return mocker.answer(callerService, requestBody);
    }

    /** 公共客户端校验 Problem 与响应头 Request ID 一致，Mock 响应统一写入固定值。 */
    static void markRequestId() {
        var attributes = (ServletRequestAttributes)RequestContextHolder.currentRequestAttributes();
        attributes.getResponse().setHeader("X-Request-Id", ScreeningMocker.REQUEST_ID);
    }
}
