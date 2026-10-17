package com.paytech.payment.test.mock;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.channel.connector.api.candidate.ChannelCandidateQueryApi;
import com.paytech.channel.connector.api.candidate.request.QueryMidCandidatesRequestBody;
import com.paytech.channel.connector.api.candidate.response.QueryMidCandidatesResponseBody;
import com.paytech.channel.connector.api.operation.ChannelOperationApi;
import com.paytech.channel.connector.api.operation.request.ExecuteChannelOperationRequestBody;
import com.paytech.channel.connector.api.operation.response.ChannelOperationResponseBody;
import com.paytech.payment.test.mocker.ChannelConnectorMocker;

import lombok.AllArgsConstructor;

/** 仅存在于独立测试 HTTP 服务的 channel-connector 合同实现（服务发现名 channel-connector-service）。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockChannelConnectorController implements ChannelCandidateQueryApi, ChannelOperationApi {
    private final ChannelConnectorMocker channel;

    /** 委托 ChannelConnectorMocker 返回预设候选。 */
    @Override
    public QueryMidCandidatesResponseBody queryMidCandidates(QueryMidCandidatesRequestBody requestBody) {
        return channel.queryMidCandidates(requestBody);
    }

    /** 委托 ChannelConnectorMocker 返回预设结论。 */
    @Override
    public ChannelOperationResponseBody execute(ExecuteChannelOperationRequestBody requestBody) {
        return channel.execute(requestBody);
    }
}
