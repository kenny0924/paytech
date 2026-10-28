package com.paytech.billing.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.billing.test.mocker.external.ChannelCostMocker;
import com.paytech.channel.connector.api.cost.ChannelCostResolutionApi;
import com.paytech.channel.connector.api.cost.request.ResolveChannelCostRequestBody;
import com.paytech.channel.connector.api.cost.response.ChannelCostResolutionResponseBody;

import lombok.AllArgsConstructor;

/** 在独立 Mock 服务继承 channel-connector 正式渠道成本命中合同 {@code /channel-connector/v1/channel-costs/resolutions}。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockChannelCostResolutionApi implements ChannelCostResolutionApi {
    private final ChannelCostMocker mocker;

    @Override
    public ChannelCostResolutionResponseBody resolveChannelCost(ResolveChannelCostRequestBody body) {
        MockRequestId.mark();
        return mocker.resolve(body.getMidId());
    }
}
