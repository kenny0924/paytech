package com.paytech.channel.connector.ext.rest.converter;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.channel.request.ChangeChannelStatusRequestBody;
import com.paytech.channel.connector.api.channel.request.ChannelEndpointBody;
import com.paytech.channel.connector.api.channel.request.CreateChannelRequestBody;
import com.paytech.channel.connector.api.channel.request.UpdateChannelEndpointsRequestBody;
import com.paytech.channel.connector.api.channel.request.UpdateChannelRequestBody;
import com.paytech.channel.connector.api.channel.response.ChannelEndpointDetail;
import com.paytech.channel.connector.api.channel.response.ChannelResponseBody;
import com.paytech.channel.connector.api.channel.response.ChannelSummaryResponseBody;
import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.core.enums.ChannelCostCodes;
import com.paytech.channel.connector.domain.ChannelSummaryView;
import com.paytech.channel.connector.protocol.domain.ChannelIdentity;
import com.paytech.channel.connector.protocol.domain.ChannelSummary;
import com.paytech.channel.connector.protocol.domain.EndpointDefinition;
import com.paytech.channel.connector.protocol.service.processor.request.CreateChannelRequest;
import com.paytech.channel.connector.protocol.service.processor.request.UpdateChannelEndpointsRequest;
import com.paytech.channel.connector.protocol.service.processor.request.UpdateChannelRequest;
import com.paytech.channel.connector.service.processor.request.ChangeChannelStatusRequest;
import com.paytech.channel.connector.service.processor.request.ListChannelsRequest;
import com.paytech.foundation.web.paging.PagingRequestBody;

/** 渠道接口的 Wire 模型与内部请求 / 结果转换；ID 输出为十进制字符串。 */
@Component
public class ChannelConverter {

    /** 转换创建请求。 */
    public CreateChannelRequest toRequest(String actorId, CreateChannelRequestBody body) {
        CreateChannelRequest request = new CreateChannelRequest();
        request.setActorId(ChannelConfigurationWire.actor(actorId));
        request.setChannelCode(body.getChannelCode());
        request.setChannelName(body.getChannelName());
        request.setChannelType(channelType(body.getChannelType()));
        request.setDescription(blankToNull(body.getDescription()));
        return request;
    }

    /** 转换名称与说明修改请求。 */
    public UpdateChannelRequest toRequest(String actorId, String channelId, UpdateChannelRequestBody body) {
        UpdateChannelRequest request = new UpdateChannelRequest();
        request.setActorId(ChannelConfigurationWire.actor(actorId));
        request.setChannelId(ChannelConfigurationWire.id("channelId", channelId));
        request.setChannelName(body.getChannelName());
        request.setDescription(blankToNull(body.getDescription()));
        return request;
    }

    /** 转换出站地址替换请求；sandbox 缺省为 false。 */
    public UpdateChannelEndpointsRequest toRequest(String actorId, String channelId,
        UpdateChannelEndpointsRequestBody body) {
        UpdateChannelEndpointsRequest request = new UpdateChannelEndpointsRequest();
        request.setActorId(ChannelConfigurationWire.actor(actorId));
        request.setChannelId(ChannelConfigurationWire.id("channelId", channelId));
        request.setExpectedVersion(body.getExpectedVersion());
        request.setEndpoints(endpoints(body.getEndpoints()));
        return request;
    }

    /** 转换渠道状态变更请求。 */
    public ChangeChannelStatusRequest toStatusRequest(String actorId, String channelId,
        ChangeChannelStatusRequestBody body) {
        return ChannelConfigurationWire.statusRequest(actorId, "channelId", channelId, body);
    }

    /** 转换列表查询条件；分页参数由公共分页规则校验。 */
    public ListChannelsRequest toListRequest(String channelType, String status, String keyword, Integer page,
        Integer size) {
        return new ListChannelsRequest(channelType == null ? null : channelType(channelType),
            ChannelConfigurationWire.channelStatus(status), keyword, PagingRequestBody.of(page, size));
    }

    /** 解析渠道路径 ID。 */
    public long channelId(String channelId) {
        return ChannelConfigurationWire.id("channelId", channelId);
    }

    /** 转换渠道详情；未配置出站地址时为空数组。 */
    public ChannelResponseBody toResponseBody(ChannelIdentity channel) {
        ChannelResponseBody body = new ChannelResponseBody();
        body.setChannelId(channel.getChannelId().toString());
        body.setChannelCode(channel.getChannelCode());
        body.setChannelName(channel.getChannelName());
        body.setChannelType(channel.getChannelType().getCode());
        body.setDescription(channel.getDescription());
        body.setEndpoints(channel.getEndpoints().stream().map(ChannelConverter::endpointDetail).toList());
        body.setVersion(channel.getVersion());
        body.setStatus(channel.getChannelStatus().getCode());
        body.setCreateTime(channel.getCreateTime());
        body.setUpdateTime(channel.getUpdateTime());
        return body;
    }

    /** 转换渠道列表项；ACTIVE 授权数只对服务渠道有值。 */
    public ChannelSummaryResponseBody toSummaryResponseBody(ChannelSummaryView view) {
        ChannelSummary channel = view.getChannel();
        ChannelSummaryResponseBody body = new ChannelSummaryResponseBody();
        body.setChannelId(channel.getChannelId().toString());
        body.setChannelCode(channel.getChannelCode());
        body.setChannelName(channel.getChannelName());
        body.setChannelType(channel.getChannelType().getCode());
        body.setStatus(channel.getChannelStatus().getCode());
        body.setAccountCount(view.getAccountCount());
        body.setMidCount(view.getMidCount());
        body.setEndpointCount(channel.getEndpointCount());
        body.setActiveGrantCount(channel.getChannelType() == ChannelType.SERVICE ? channel.getActiveGrantCount() : null);
        body.setUpdateTime(channel.getUpdateTime());
        return body;
    }

    /** 解析渠道类型代码。 */
    static ChannelType channelType(String code) {
        return ChannelCostCodes.parse(ChannelType.class, "channelType", code);
    }

    /** 出站地址转为领域定义，保持提交顺序。 */
    static List<EndpointDefinition> endpoints(List<ChannelEndpointBody> bodies) {
        return bodies.stream().map(body -> new EndpointDefinition(body.getEndpointCode(), body.getBaseUrl(),
            Boolean.TRUE.equals(body.getSandbox()))).toList();
    }

    /** 出站地址投影。 */
    static ChannelEndpointDetail endpointDetail(EndpointDefinition endpoint) {
        ChannelEndpointDetail detail = new ChannelEndpointDetail();
        detail.setEndpointCode(endpoint.getEndpointCode());
        detail.setBaseUrl(endpoint.getBaseUrl());
        detail.setSandbox(endpoint.isSandbox());
        return detail;
    }

    /** 空白说明按未填写处理。 */
    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
