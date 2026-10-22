package com.paytech.channel.connector.ext.rest.converter;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.channel.request.ChangeChannelStatusRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateChannelMidRequestBody;
import com.paytech.channel.connector.api.channel.request.RebindChannelMidAccountRequestBody;
import com.paytech.channel.connector.api.channel.response.ChannelMidResponseBody;
import com.paytech.channel.connector.api.channel.response.ChannelMidSummaryResponseBody;
import com.paytech.channel.connector.core.dal.dataobject.ChannelMid;
import com.paytech.channel.connector.domain.ChannelMidDetailView;
import com.paytech.channel.connector.service.processor.request.ChangeChannelStatusRequest;
import com.paytech.channel.connector.service.processor.request.CreateChannelMidRequest;
import com.paytech.channel.connector.service.processor.request.RebindChannelMidAccountRequest;
import com.paytech.channel.connector.core.enums.ChannelCostCodes;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.domain.ChannelMidListCriteria;
import com.paytech.channel.connector.domain.ChannelMidSummaryView;
import com.paytech.foundation.web.paging.PagingRequestBody;

/** MID 接口的查询参数解析与 Wire 转换；ID 输出为十进制字符串。 */
@Component
public class ChannelMidConverter {

    /** 由可空查询参数构造列表条件；格式已由入口校验，分页参数非法时以参数错误拒绝。 */
    public ChannelMidListCriteria toCriteria(String channelId, String channelAccountId, String entityNo, String status,
        String externalMidCode, Integer page, Integer size) {
        ChannelStatus midStatus = status == null ? null : ChannelCostCodes.parse(ChannelStatus.class, "status", status);
        return new ChannelMidListCriteria(optionalId(channelId), optionalId(channelAccountId), entityNo, midStatus,
            externalMidCode, PagingRequestBody.of(page, size));
    }

    /** 转换 MID 列表项。 */
    public ChannelMidSummaryResponseBody toSummaryResponseBody(ChannelMidSummaryView view) {
        ChannelMidSummaryResponseBody body = new ChannelMidSummaryResponseBody();
        body.setMidId(view.getMidId().toString());
        body.setChannelId(view.getChannelId().toString());
        body.setChannelCode(view.getChannelCode());
        body.setChannelAccountId(view.getChannelAccountId().toString());
        body.setAccountName(view.getAccountName());
        body.setExternalMidCode(view.getExternalMidCode());
        body.setEntityNo(view.getEntityNo());
        body.setStatus(view.getMidStatus().getCode());
        body.setCapabilityGroupCount(view.getCapabilityGroupCount());
        body.setEnabledCapabilityGroupCount(view.getEnabledCapabilityGroupCount());
        body.setActivatedAt(view.getActivatedAt());
        body.setUpdateTime(view.getUpdateTime());
        return body;
    }

    // ---- CC-B 追加：MID 写入口转换 ----

    /** 转换创建请求。 */
    public CreateChannelMidRequest toRequest(String actorId, CreateChannelMidRequestBody body) {
        CreateChannelMidRequest request = new CreateChannelMidRequest();
        request.setActorId(ChannelConfigurationWire.actor(actorId));
        request.setChannelId(ChannelConfigurationWire.id("channelId", body.getChannelId()));
        request.setChannelAccountId(ChannelConfigurationWire.id("channelAccountId", body.getChannelAccountId()));
        request.setExternalMidCode(body.getExternalMidCode());
        request.setEntityNo(body.getEntityNo());
        return request;
    }

    /** 转换改绑请求。 */
    public RebindChannelMidAccountRequest toRequest(String actorId, String midId, RebindChannelMidAccountRequestBody body) {
        RebindChannelMidAccountRequest request = new RebindChannelMidAccountRequest();
        request.setActorId(ChannelConfigurationWire.actor(actorId));
        request.setMidId(ChannelConfigurationWire.id("midId", midId));
        request.setChannelAccountId(ChannelConfigurationWire.id("channelAccountId", body.getChannelAccountId()));
        return request;
    }

    /** 转换 MID 状态变更请求。 */
    public ChangeChannelStatusRequest toStatusRequest(String actorId, String midId, ChangeChannelStatusRequestBody body) {
        return ChannelConfigurationWire.statusRequest(actorId, "midId", midId, body);
    }

    /** 解析 MID 路径 ID。 */
    public long midId(String midId) {
        return ChannelConfigurationWire.id("midId", midId);
    }

    /** 转换 MID 详情，含能力组与明细。 */
    public ChannelMidResponseBody toResponseBody(ChannelMidDetailView view) {
        ChannelMid mid = view.getMid();
        ChannelMidResponseBody body = new ChannelMidResponseBody();
        body.setMidId(mid.getMidId().toString());
        body.setChannelId(mid.getChannelId().toString());
        body.setChannelAccountId(mid.getChannelAccountId().toString());
        body.setExternalMidCode(mid.getExternalMidCode());
        body.setEntityNo(mid.getEntityNo());
        body.setStatus(mid.getMidStatus().getCode());
        body.setActivatedAt(mid.getActivatedAt());
        body.setClosedAt(mid.getClosedAt());
        body.setCreateTime(mid.getCreateTime());
        body.setUpdateTime(mid.getUpdateTime());
        body.setCapabilities(view.getCapabilities().stream().map(ChannelConfigurationWire::capability).toList());
        return body;
    }

    /** 可空十进制主键转 Long。 */
    Long optionalId(String value) {
        return value == null ? null : Long.valueOf(value);
    }
}
