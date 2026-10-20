package com.paytech.channel.connector.api.channel;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.channel.connector.api.ChannelConnectorHeaders;
import com.paytech.channel.connector.api.channel.request.ChangeMidCapabilityStatusRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateMidCapabilityRequestBody;
import com.paytech.channel.connector.api.channel.request.ReplaceMidCapabilityDetailsRequestBody;
import com.paytech.channel.connector.api.channel.response.CapabilityDefinitionsResponseBody;
import com.paytech.channel.connector.api.channel.response.MidCapabilityResponseBody;

import jakarta.validation.Valid;

/**
 * MID 能力条件组运营管理（仅私网，调用方 operations-console-bff）
 * <li>写命令必带 {@value ChannelConnectorHeaders#ACTOR_HEADER}；错误语义同 {@link PaymentChannelApi}。</li>
 * <li>明细类型与值域以本服务能力目录为准，与候选查询的解析规则一致；未注册代码、类型不一致或 CODE 值越界 400。</li>
 */
@HttpExchange(url = "/channel-connector/v1/mid-capabilities", accept = "application/json")
public interface MidCapabilityApi {

    /**
     * 已注册的能力目录
     *
     * @return 能力代码、值类型与 CODE 允许值
     */
    @GetExchange("/definitions")
    CapabilityDefinitionsResponseBody listCapabilityDefinitions();

    /**
     * 创建 ENABLED 能力条件组及其明细
     *
     * @param actorId 操作者 Account ID
     * @param body 六维条件与明细
     * @return 201 与能力组；MID 不存在或已关闭、明细不合法 400；六维组合重复 409
     */
    @PostExchange
    ResponseEntity<MidCapabilityResponseBody> createMidCapability(
        @RequestHeader(ChannelConnectorHeaders.ACTOR_HEADER) String actorId,
        @Valid @RequestBody CreateMidCapabilityRequestBody body);

    /**
     * 整组替换能力明细：同一事务内删除原明细并写入本次全部明细
     *
     * @param actorId 操作者 Account ID
     * @param midCapabilityId 能力组 ID
     * @param body 替换后的全部明细
     * @return 替换后的能力组；不存在、MID 已关闭或明细不合法 400
     */
    @PutExchange("/{midCapabilityId}/details")
    MidCapabilityResponseBody replaceMidCapabilityDetails(
        @RequestHeader(ChannelConnectorHeaders.ACTOR_HEADER) String actorId,
        @PathVariable("midCapabilityId") String midCapabilityId,
        @Valid @RequestBody ReplaceMidCapabilityDetailsRequestBody body);

    /**
     * 启用或停用能力条件组；停用的组不参与候选查询
     *
     * @param actorId 操作者 Account ID
     * @param midCapabilityId 能力组 ID
     * @param body 目标状态
     * @return 变更后的能力组；已是目标状态或 MID 已关闭 400；并发 409
     */
    @PostExchange("/{midCapabilityId}/status")
    MidCapabilityResponseBody changeMidCapabilityStatus(
        @RequestHeader(ChannelConnectorHeaders.ACTOR_HEADER) String actorId,
        @PathVariable("midCapabilityId") String midCapabilityId,
        @Valid @RequestBody ChangeMidCapabilityStatusRequestBody body);
}
