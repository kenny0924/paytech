package com.paytech.channel.connector.test.builder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.paytech.channel.connector.api.cost.request.ChannelCostComponentRequestBody;
import com.paytech.channel.connector.api.cost.request.ChannelCostScopeRequestBody;
import com.paytech.channel.connector.api.cost.request.CreateChannelCostVersionRequestBody;
import com.paytech.channel.connector.core.enums.AmountRoundingMode;
import com.paytech.channel.connector.core.enums.ChannelCostCalculationBase;
import com.paytech.channel.connector.core.enums.ChannelCostComponentType;

/**
 * 创建渠道成本版本请求的模板：默认 1 个范围（标准六维，MID 由用例的本库 Fixture 提供）与 1 个处理费组成（2.9% + 0.30 USD）
 * <li>用例通过 clearScopes/addScope/scope 与 scope/component 静态工厂覆盖场景差异；build 不修复刻意设置的非法值。</li>
 */
public final class ChannelCostVersionRequestBuilder {
    /** 标准范围 Operating Entity，也是标准 Fixture MID 的 OE。 */
    public static final String ENTITY_NO = "AE001";

    /** 标准范围 Channel 协议引用。 */
    public static final String AGREEMENT_REF = "NI-AGR-2026-01";

    /** 标准范围国家。 */
    public static final String COUNTRY_CODE = "AE";

    /** 标准范围币种。 */
    public static final String CURRENCY = "USD";

    /** 标准范围支付方式。 */
    public static final String PAYMENT_METHOD = "CARD";

    /** 标准范围动作类别。 */
    public static final String ACTION_TYPE = "SALE";

    /** 模板处理费组成代码。 */
    public static final String PROCESSING = "PROCESSING";

    /** 模板处理费费率。 */
    public static final String PROCESSING_RATE = "0.029";

    /** 模板处理费固定费。 */
    public static final String PROCESSING_FIXED = "0.30";

    private final CreateChannelCostVersionRequestBody body = new CreateChannelCostVersionRequestBody();

    private ChannelCostVersionRequestBuilder() {
    }

    /** 标准单范围、单组成模板。 */
    public static ChannelCostVersionRequestBuilder template(String makerId, Instant effectiveFrom, long midId) {
        ChannelCostVersionRequestBuilder builder = new ChannelCostVersionRequestBuilder();
        builder.body.setMakerId(makerId);
        builder.body.setEffectiveFrom(effectiveFrom);
        builder.body.setScopes(new ArrayList<>(List.of(scope(1, midId, standardComponent()))));
        return builder;
    }

    /** 模板处理费组成：2.9% + 0.30 USD，无上下限。 */
    public static ChannelCostComponentRequestBody standardComponent() {
        return component(PROCESSING, ChannelCostComponentType.PROCESSING_FEE, PROCESSING_RATE, PROCESSING_FIXED);
    }

    /** 标准六维范围；用例按需覆盖单个维度。 */
    public static ChannelCostScopeRequestBody scope(int scopeNo, long midId,
        ChannelCostComponentRequestBody... components) {
        ChannelCostScopeRequestBody scope = new ChannelCostScopeRequestBody();
        scope.setScopeNo(scopeNo);
        scope.setMidId(String.valueOf(midId));
        scope.setEntityNo(ENTITY_NO);
        scope.setChannelAgreementRef(AGREEMENT_REF);
        scope.setCountryCode(COUNTRY_CODE);
        scope.setCurrency(CURRENCY);
        scope.setPaymentMethodCode(PAYMENT_METHOD);
        scope.setActionType(ACTION_TYPE);
        scope.setComponents(new ArrayList<>(List.of(components)));
        return scope;
    }

    /** 交易金额基数、HALF_UP 的组成；有固定费时固定费币种为标准币种。rate/fixed 为空表示不设置。 */
    public static ChannelCostComponentRequestBody component(String componentCode, ChannelCostComponentType type,
        String rateValue, String fixedAmount) {
        ChannelCostComponentRequestBody component = new ChannelCostComponentRequestBody();
        component.setComponentCode(componentCode);
        component.setComponentType(type.getCode());
        component.setCalculationBase(ChannelCostCalculationBase.TRANSACTION_AMOUNT.getCode());
        component.setRateValue(rateValue);
        component.setFixedAmount(fixedAmount);
        component.setCurrency(fixedAmount == null ? null : CURRENCY);
        component.setRoundingMode(AmountRoundingMode.HALF_UP.getCode());
        return component;
    }

    /** 带上下限的组成；min/max 为空表示不设置。 */
    public static ChannelCostComponentRequestBody component(String componentCode, ChannelCostComponentType type,
        String rateValue, String fixedAmount, String minAmount, String maxAmount) {
        ChannelCostComponentRequestBody component = component(componentCode, type, rateValue, fixedAmount);
        component.setMinAmount(minAmount);
        component.setMaxAmount(maxAmount);
        return component;
    }

    /** 清空范围集。 */
    public ChannelCostVersionRequestBuilder clearScopes() {
        body.getScopes().clear();
        return this;
    }

    /** 追加范围。 */
    public ChannelCostVersionRequestBuilder addScope(ChannelCostScopeRequestBody... scopes) {
        body.getScopes().addAll(List.of(scopes));
        return this;
    }

    /** 以给定组成替换全部范围为一个使用原第一个范围 MID 的标准范围。 */
    public ChannelCostVersionRequestBuilder components(ChannelCostComponentRequestBody... components) {
        long midId = Long.parseLong(scope(0).getMidId());
        return clearScopes().addScope(scope(1, midId, components));
    }

    /** 取得指定下标的范围以便覆盖字段。 */
    public ChannelCostScopeRequestBody scope(int index) {
        return body.getScopes().get(index);
    }

    /** 取得第一个范围的指定下标组成以便覆盖字段。 */
    public ChannelCostComponentRequestBody component(int index) {
        return scope(0).getComponents().get(index);
    }

    /** 覆盖编制人。 */
    public ChannelCostVersionRequestBuilder makerId(String makerId) {
        body.setMakerId(makerId);
        return this;
    }

    /** 返回请求体。 */
    public CreateChannelCostVersionRequestBody build() {
        return body;
    }
}
