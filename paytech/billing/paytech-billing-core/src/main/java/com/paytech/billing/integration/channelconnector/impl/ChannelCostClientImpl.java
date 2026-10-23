package com.paytech.billing.integration.channelconnector.impl;

import static com.paytech.billing.integration.UpstreamResponseFields.optionalDecimal;
import static com.paytech.billing.integration.UpstreamResponseFields.required;
import static com.paytech.billing.integration.UpstreamResponseFields.requiredId;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.billing.core.enums.AmountRoundingMode;
import com.paytech.billing.core.enums.BillingCodes;
import com.paytech.billing.domain.ChannelCostComponentRule;
import com.paytech.billing.domain.ChannelCostResolution;
import com.paytech.billing.domain.OrderPaymentSucceededFact;
import com.paytech.billing.integration.channelconnector.ChannelCostClient;
import com.paytech.channel.connector.api.cost.ChannelCostResolutionApi;
import com.paytech.channel.connector.api.cost.request.ResolveChannelCostRequestBody;
import com.paytech.channel.connector.api.cost.response.ChannelCostComponentResponseBody;
import com.paytech.channel.connector.api.cost.response.ChannelCostResolutionResponseBody;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;

import lombok.AllArgsConstructor;

/**
 * 只适配 channel-connector-api 渠道成本命中 DTO；HTTP 与错误解码交给 Foundation 公共客户端
 * <li>动作类型取 Payment 动作类型目录的 PAY（S1：PAY 成功即直接收款）；命中时间为支付成功时间。</li>
 * <li>400 未命中 / 命中不唯一对自动登记表示成本配置尚未就绪：转为 ErrorException 使事实停在重试（技术方案 4.8）。</li>
 */
@Component
@AllArgsConstructor
public class ChannelCostClientImpl implements ChannelCostClient {
    /** Payment 动作类型目录中的直接收款动作。 */
    static final String PAY_ACTION = "PAY";

    private static final String UPSTREAM = "channel-connector";

    private final ChannelCostResolutionApi channelCostResolutionApi;

    /** 按六个范围维度、Channel 与成功时间命中；上游明确拒绝转为可重试错误。 */
    @Override
    public ChannelCostResolution resolve(OrderPaymentSucceededFact payment) {
        ResolveChannelCostRequestBody body = new ResolveChannelCostRequestBody();
        body.setChannelId(Long.toString(payment.getChannelId()));
        body.setMidId(Long.toString(payment.getMidId()));
        body.setEntityNo(payment.getOrder().getEntityNo());
        body.setCountryCode(payment.getOrder().getConsumerCountryCode());
        body.setCurrency(payment.getOrder().getCurrency());
        body.setPaymentMethodCode(payment.getPaymentMethodCode());
        body.setActionType(PAY_ACTION);
        body.setAsOfTime(payment.getPaymentSucceededTime());
        ChannelCostResolutionResponseBody response;
        try {
            response = channelCostResolutionApi.resolveChannelCost(body);
        } catch (FailException rejected) {
            throw new ErrorException("渠道成本未命中或配置未就绪: Channel " + payment.getChannelId() + ", MID "
                + payment.getMidId() + ", 支付尝试 " + payment.getPaymentAttemptId(), rejected);
        }
        return toResolution(required(UPSTREAM, "resolution", response));
    }

    /** 命中结果转换；组件必须非空。 */
    ChannelCostResolution toResolution(ChannelCostResolutionResponseBody response) {
        List<ChannelCostComponentResponseBody> components = required(UPSTREAM, "components", response.getComponents());
        if (components.isEmpty()) {
            throw new ErrorException(UPSTREAM + " 响应无效: components 为空");
        }
        return ChannelCostResolution.builder().planCode(response.getPlanCode())
            .costVerId(requiredId(UPSTREAM, "costVerId", response.getCostVerId())).costVer(response.getCostVer())
            .contentHash(response.getContentHash()).scopeId(response.getScopeId())
            .currency(required(UPSTREAM, "currency", response.getCurrency()))
            .components(components.stream().map(ChannelCostClientImpl::toRule).toList()).build();
    }

    /** 组件转换；舍入方式不可识别视为上游响应无效。 */
    static ChannelCostComponentRule toRule(ChannelCostComponentResponseBody component) {
        AmountRoundingMode roundingMode;
        try {
            roundingMode = BillingCodes.parse(AmountRoundingMode.class, "舍入方式", component.getRoundingMode());
        } catch (InvalidParameterException unknown) {
            throw new ErrorException(UPSTREAM + " 响应无效: roundingMode", unknown);
        }
        return new ChannelCostComponentRule(requiredId(UPSTREAM, "componentId", component.getComponentId()),
            component.getComponentCode(), component.getComponentType(), component.getCalculationBase(),
            optionalDecimal(UPSTREAM, "rateValue", component.getRateValue()),
            optionalDecimal(UPSTREAM, "fixedAmount", component.getFixedAmount()),
            optionalDecimal(UPSTREAM, "minAmount", component.getMinAmount()),
            optionalDecimal(UPSTREAM, "maxAmount", component.getMaxAmount()), roundingMode);
    }
}
