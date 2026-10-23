package com.paytech.billing.test.mocker.external;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.channel.connector.api.cost.response.ChannelCostResolutionResponseBody;

/** channel-connector 渠道成本命中 Mock：按 MID 主键配置响应或故障。 */
@TestComponent
public class ChannelCostMocker extends DtoResponseMocker<String, ChannelCostResolutionResponseBody> {

    /** 上游名 channel-connector。 */
    public ChannelCostMocker() {
        super("channel-connector");
    }

    /** 由 Mock Controller 调用。 */
    public ChannelCostResolutionResponseBody resolve(String midId) {
        return answer("resolveChannelCost", midId);
    }
}
