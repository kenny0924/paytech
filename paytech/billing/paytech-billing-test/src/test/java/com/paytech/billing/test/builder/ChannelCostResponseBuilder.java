package com.paytech.billing.test.builder;

import static com.paytech.billing.test.builder.PaymentFactConstants.COST_COMPONENT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.COST_VER_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.USD;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.paytech.billing.core.enums.AmountRoundingMode;
import com.paytech.channel.connector.api.cost.response.ChannelCostComponentResponseBody;
import com.paytech.channel.connector.api.cost.response.ChannelCostResolutionResponseBody;

/** channel-connector 渠道成本命中响应：template() 为一个 0.5% 的 PROCESSING_FEE 组件（USD、HALF_UP）。 */
public final class ChannelCostResponseBuilder {
    /** 版本内容摘要。 */
    public static final String CONTENT_HASH = "a".repeat(64);

    /** 方案代码。 */
    public static final String PLAN_CODE = "NI_CARD";

    /** 默认费率。 */
    public static final String DEFAULT_RATE = "0.005";

    private final ChannelCostResolutionResponseBody body = new ChannelCostResolutionResponseBody();

    /** 只能从模板开始。 */
    private ChannelCostResponseBuilder() {
    }

    /** 默认命中结果。 */
    public static ChannelCostResponseBuilder template() {
        ChannelCostResponseBuilder builder = new ChannelCostResponseBuilder();
        ChannelCostResolutionResponseBody body = builder.body;
        body.setPlanCode(PLAN_CODE);
        body.setCostVerId(Long.toString(COST_VER_ID));
        body.setCostVer("1");
        body.setContentHash(CONTENT_HASH);
        body.setEffectiveFrom(Instant.parse("2026-10-01T00:00:00Z"));
        body.setScopeId("58101");
        body.setScopeNo(1);
        body.setChannelAgreementRef("NI-2026");
        body.setCurrency(USD);
        body.setComponents(new ArrayList<>(List.of(component(COST_COMPONENT_ID, DEFAULT_RATE))));
        return builder;
    }

    /** 一个按交易金额百分比计算的 PROCESSING_FEE 组件。 */
    public static ChannelCostComponentResponseBody component(long componentId, String rateValue) {
        ChannelCostComponentResponseBody component = new ChannelCostComponentResponseBody();
        component.setComponentId(Long.toString(componentId));
        component.setComponentCode("PROCESSING_FEE_" + componentId);
        component.setComponentType("PROCESSING_FEE");
        component.setCalculationBase("TRANSACTION_AMOUNT");
        component.setRateValue(rateValue);
        component.setRoundingMode(AmountRoundingMode.HALF_UP.getCode());
        return component;
    }

    /** 替换全部组件。 */
    public ChannelCostResponseBuilder components(ChannelCostComponentResponseBody... components) {
        body.setComponents(new ArrayList<>(List.of(components)));
        return this;
    }

    /** 范围币种。 */
    public ChannelCostResponseBuilder currency(String currency) {
        body.setCurrency(currency);
        return this;
    }

    /** 构建。 */
    public ChannelCostResolutionResponseBody build() {
        return body;
    }
}
