package com.paytech.channel.connector.api.cost;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.channel.connector.api.cost.request.ResolveChannelCostRequestBody;
import com.paytech.channel.connector.api.cost.response.ChannelCostResolutionResponseBody;

import jakarta.validation.Valid;

/**
 * 渠道成本命中（服务发现名 {@code channel-connector-service}，无 context path）
 * <li>只读、幂等：按 asOfTime 选出 Channel 的 ACTIVE 方案中覆盖该时间的唯一版本，再按六个维度精确命中唯一范围，返回该范围与全部成本组成。</li>
 * <li>不计算金额：组成 → 金额由 billing 按自身币种精度计算。</li>
 * <li>命中失败返回业务错误而不是空组成，错误码见 {@code ChannelCostErrorCode}。</li>
 */
@HttpExchange("/channel-connector/v1/channel-costs")
public interface ChannelCostResolutionApi {

    /**
     * 命中渠道成本范围与组成
     *
     * @param body Channel、MID、OE、国家、币种、支付方式、动作类型与命中时间
     * @return 200 与命中的方案版本、范围及按 componentCode 升序的组成；字段格式不合法时 400 INVALID_REQUEST；无 ACTIVE 方案、
     *         无覆盖 asOfTime 的版本或无命中范围时 400 CHANNEL_COST_NOT_RESOLVED；命中多于一个版本或范围时 400
     *         CHANNEL_COST_AMBIGUOUS；两类错误的 detail 均含请求维度上下文
     */
    @PostExchange("/resolutions")
    ChannelCostResolutionResponseBody resolveChannelCost(@Valid @RequestBody ResolveChannelCostRequestBody body);
}
