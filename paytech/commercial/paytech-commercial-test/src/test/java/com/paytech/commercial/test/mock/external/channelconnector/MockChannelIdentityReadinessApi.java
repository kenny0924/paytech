package com.paytech.commercial.test.mock.external.channelconnector;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.channel.connector.api.readiness.ChannelIdentityReadinessApi;
import com.paytech.channel.connector.api.readiness.request.ChannelIdentityReadinessRequestBody;
import com.paytech.channel.connector.api.readiness.response.ChannelIdentityReadinessResponseBody;
import com.paytech.commercial.test.mocker.channelconnector.ChannelIdentityReadinessMocker;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/** channel-connector 渠道身份就绪接口的测试 HTTP 实现。 */
@RestController
@TestComponent
public class MockChannelIdentityReadinessApi implements ChannelIdentityReadinessApi {
    private final ChannelIdentityReadinessMocker mocker;

    /** 与用例共享配置与记录。 */
    public MockChannelIdentityReadinessApi(ChannelIdentityReadinessMocker mocker) {
        this.mocker = mocker;
    }

    /** 返回本例配置的结论或故障；存在未配置组合时记为意外调用。 */
    @Override
    public ChannelIdentityReadinessResponseBody checkReadiness(ChannelIdentityReadinessRequestBody requestBody) {
        var attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        attributes.getResponse().setHeader("X-Request-Id", ChannelIdentityReadinessMocker.REQUEST_ID);
        Object configured = mocker.check(requestBody);
        if (configured instanceof ChannelIdentityReadinessMocker.Fault fault) {
            throw fault.disconnect() ? MockHttpFault.disconnect() : MockHttpFault.json(fault.status(), fault.problem());
        }
        if (configured == null) {
            throw new IllegalStateException("未配置的渠道身份就绪组合");
        }
        return (ChannelIdentityReadinessResponseBody) configured;
    }
}
