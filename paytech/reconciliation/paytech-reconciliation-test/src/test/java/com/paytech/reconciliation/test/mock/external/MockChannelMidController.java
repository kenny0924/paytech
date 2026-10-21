package com.paytech.reconciliation.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.channel.connector.api.channel.ChannelMidApi;
import com.paytech.channel.connector.api.channel.request.ChangeChannelStatusRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateChannelMidRequestBody;
import com.paytech.channel.connector.api.channel.request.RebindChannelMidAccountRequestBody;
import com.paytech.channel.connector.api.channel.response.ChannelMidResponseBody;
import com.paytech.channel.connector.api.channel.response.ChannelMidSummaryResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.test.mocker.external.ChannelConnectorMocker;

import lombok.AllArgsConstructor;

/** 仅存在于独立测试 HTTP 服务的 channel-connector ChannelMidApi 实现：查询委托 Mocker 返回预设 DTO，写命令记为意外调用。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockChannelMidController implements ChannelMidApi {
    private final ChannelConnectorMocker channelConnector;

    /** reconciliation 不创建 MID。 */
    @Override
    public ResponseEntity<ChannelMidResponseBody> createChannelMid(String actorId, CreateChannelMidRequestBody body) {
        channelConnector.unexpectedCommand("createChannelMid");
        return null;
    }

    /** 按 Mocker 配置过滤分页。 */
    @Override
    public PagingResponseBody<ChannelMidSummaryResponseBody> listChannelMids(String channelId, String channelAccountId,
        String entityNo, String status, String externalMidCode, Integer page, Integer size) {
        return channelConnector.list(channelId, entityNo, status, externalMidCode, page, size);
    }

    /** 返回 Mocker 配置的 MID 详情；配置为正式 Problem 时先写出与 Body 一致的 X-Request-Id。 */
    @Override
    public ChannelMidResponseBody getChannelMid(String midId) {
        String problemRequestId = channelConnector.detailProblemRequestId(midId);
        if (problemRequestId != null) {
            ServletRequestAttributes attributes = (ServletRequestAttributes)RequestContextHolder.currentRequestAttributes();
            attributes.getResponse().setHeader("X-Request-Id", problemRequestId);
        }
        return channelConnector.detail(midId);
    }

    /** reconciliation 不改绑 MID。 */
    @Override
    public ChannelMidResponseBody rebindChannelMidAccount(String actorId, String midId,
        RebindChannelMidAccountRequestBody body) {
        channelConnector.unexpectedCommand("rebindChannelMidAccount");
        return null;
    }

    /** reconciliation 不变更 MID 状态。 */
    @Override
    public ChannelMidResponseBody changeChannelMidStatus(String actorId, String midId,
        ChangeChannelStatusRequestBody body) {
        channelConnector.unexpectedCommand("changeChannelMidStatus");
        return null;
    }
}
