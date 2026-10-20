package com.paytech.channel.connector.ext.rest.converter;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.channel.request.ChangeChannelStatusRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateChannelRequestBody;
import com.paytech.channel.connector.api.channel.request.UpdateChannelRequestBody;
import com.paytech.channel.connector.api.channel.response.ChannelResponseBody;
import com.paytech.channel.connector.api.channel.response.ChannelSummaryResponseBody;
import com.paytech.channel.connector.core.dal.dataobject.PaymentChannel;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.domain.ChannelSummaryView;
import com.paytech.channel.connector.service.processor.request.ChangeChannelStatusRequest;
import com.paytech.channel.connector.service.processor.request.CreateChannelRequest;
import com.paytech.channel.connector.service.processor.request.UpdateChannelRequest;

/** 支付渠道接口的 Wire 模型与内部请求 / 结果转换；ID 输出为十进制字符串。 */
@Component
public class PaymentChannelConverter {

    /** 转换创建请求。 */
    public CreateChannelRequest toRequest(String actorId, CreateChannelRequestBody body) {
        CreateChannelRequest request = new CreateChannelRequest();
        request.setActorId(ChannelConfigurationWire.actor(actorId));
        request.setChannelCode(body.getChannelCode());
        request.setChannelName(body.getChannelName());
        return request;
    }

    /** 转换改名请求。 */
    public UpdateChannelRequest toRequest(String actorId, String channelId, UpdateChannelRequestBody body) {
        UpdateChannelRequest request = new UpdateChannelRequest();
        request.setActorId(ChannelConfigurationWire.actor(actorId));
        request.setChannelId(ChannelConfigurationWire.id("channelId", channelId));
        request.setChannelName(body.getChannelName());
        return request;
    }

    /** 转换渠道状态变更请求。 */
    public ChangeChannelStatusRequest toStatusRequest(String actorId, String channelId,
        ChangeChannelStatusRequestBody body) {
        return ChannelConfigurationWire.statusRequest(actorId, "channelId", channelId, body);
    }

    /** 解析渠道路径 ID。 */
    public long channelId(String channelId) {
        return ChannelConfigurationWire.id("channelId", channelId);
    }

    /** 解析可空状态查询参数。 */
    public ChannelStatus status(String status) {
        return ChannelConfigurationWire.channelStatus(status);
    }

    /** 转换渠道详情。 */
    public ChannelResponseBody toResponseBody(PaymentChannel channel) {
        ChannelResponseBody body = new ChannelResponseBody();
        body.setChannelId(channel.getChannelId().toString());
        body.setChannelCode(channel.getChannelCode());
        body.setChannelName(channel.getChannelName());
        body.setStatus(channel.getChannelStatus().getCode());
        body.setCreateTime(channel.getCreateTime());
        body.setUpdateTime(channel.getUpdateTime());
        return body;
    }

    /** 转换渠道列表项。 */
    public ChannelSummaryResponseBody toSummaryResponseBody(ChannelSummaryView view) {
        ChannelSummaryResponseBody body = new ChannelSummaryResponseBody();
        body.setChannelId(view.getChannelId().toString());
        body.setChannelCode(view.getChannelCode());
        body.setChannelName(view.getChannelName());
        body.setStatus(view.getChannelStatus().getCode());
        body.setAccountCount(view.getAccountCount());
        body.setMidCount(view.getMidCount());
        body.setUpdateTime(view.getUpdateTime());
        return body;
    }
}
