package com.paytech.commercial.test.builder;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.BandBasis;
import com.paytech.commercial.api.enums.CalculationBase;
import com.paytech.commercial.api.enums.RefundBehavior;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.domain.enums.CommercialTermComponentBandMode;
import com.paytech.commercial.domain.enums.CommercialTermComponentComponentType;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;

/**
 * t_commercial_term_component 测试行的 Builder：template() 给出合法默认值，用例只覆盖与场景相关的字段。
 * <li>不访问数据库；可显式设置 null 或非法组合构造负例，build() 不修复输入。</li>
 */
public final class CommercialTermComponentBuilder {
    /** 默认组件 ID */
    public static final long TERM_COMPONENT_ID = 930000000000091L;

    private final CommercialTermComponent commercialTermComponent = new CommercialTermComponent();

    private CommercialTermComponentBuilder() {
    }

    /**
     * 默认价格下 SALE 触发、按交易总额 2.5% 的百分比组件。
     *
     * @return 新 Builder
     */
    public static CommercialTermComponentBuilder template() {
        return new CommercialTermComponentBuilder()
            .termComponentId(TERM_COMPONENT_ID)
            .termPriceId(CommercialTermPriceBuilder.TERM_PRICE_ID)
            .componentNo(1)
            .componentCode("CLIENT_FEE")
            .componentType(CommercialTermComponentComponentType.PERCENTAGE)
            .triggerCode(CommercialCodes.FeeEvent.SALE)
            .calculationBase(CalculationBase.GROSS_AMOUNT)
            .refundBehavior(RefundBehavior.RETURN_PRORATA)
            .rate(new BigDecimal("0.025"))
            .fixedAmount(null)
            .currency(null)
            .minAmount(null)
            .maxAmount(null)
            .bandBasis(null)
            .bandMode(null)
            .createTime(CheckoutFixture.NOW)
            .updateTime(CheckoutFixture.NOW);
    }

    /** 设置组件标识。 */
    public CommercialTermComponentBuilder termComponentId(Long value) {
        commercialTermComponent.setTermComponentId(value);
        return this;
    }

    /** 设置所属价格。 */
    public CommercialTermComponentBuilder termPriceId(Long value) {
        commercialTermComponent.setTermPriceId(value);
        return this;
    }

    /** 设置价格内从 1 连续的计算顺序。 */
    public CommercialTermComponentBuilder componentNo(Integer value) {
        commercialTermComponent.setComponentNo(value);
        return this;
    }

    /** 设置价格内唯一的业务组件代码。 */
    public CommercialTermComponentBuilder componentCode(String value) {
        commercialTermComponent.setComponentCode(value);
        return this;
    }

    /** 设置组件类型。 */
    public CommercialTermComponentBuilder componentType(CommercialTermComponentComponentType value) {
        commercialTermComponent.setComponentType(value);
        return this;
    }

    /** 设置计费事件代码；开放目录，值域为 t_product_fee_event.fee_event_code。 */
    public CommercialTermComponentBuilder triggerCode(String value) {
        commercialTermComponent.setTriggerCode(value);
        return this;
    }

    /** 设置计算基数。 */
    public CommercialTermComponentBuilder calculationBase(CalculationBase value) {
        commercialTermComponent.setCalculationBase(value);
        return this;
    }

    /** 设置退款时组件费用处理方式。 */
    public CommercialTermComponentBuilder refundBehavior(RefundBehavior value) {
        commercialTermComponent.setRefundBehavior(value);
        return this;
    }

    /** 设置小数费率 0–1。 */
    public CommercialTermComponentBuilder rate(BigDecimal value) {
        commercialTermComponent.setRate(value);
        return this;
    }

    /** 设置固定金额参数。 */
    public CommercialTermComponentBuilder fixedAmount(BigDecimal value) {
        commercialTermComponent.setFixedAmount(value);
        return this;
    }

    /** 设置金额参数的 ISO 4217 币种；纯百分比无上下限时为空。 */
    public CommercialTermComponentBuilder currency(String value) {
        commercialTermComponent.setCurrency(value);
        return this;
    }

    /** 设置百分比结果下限。 */
    public CommercialTermComponentBuilder minAmount(BigDecimal value) {
        commercialTermComponent.setMinAmount(value);
        return this;
    }

    /** 设置百分比结果上限。 */
    public CommercialTermComponentBuilder maxAmount(BigDecimal value) {
        commercialTermComponent.setMaxAmount(value);
        return this;
    }

    /** 设置仅 TIERED 非空的档位匹配基数。 */
    public CommercialTermComponentBuilder bandBasis(BandBasis value) {
        commercialTermComponent.setBandBasis(value);
        return this;
    }

    /** 设置仅 TIERED 非空的档位计算方式。 */
    public CommercialTermComponentBuilder bandMode(CommercialTermComponentBandMode value) {
        commercialTermComponent.setBandMode(value);
        return this;
    }

    /** 设置记录创建的 UTC 微秒时间。 */
    public CommercialTermComponentBuilder createTime(Instant value) {
        commercialTermComponent.setCreateTime(value);
        return this;
    }

    /** 设置记录最近一次持久化更新的 UTC 微秒时间。 */
    public CommercialTermComponentBuilder updateTime(Instant value) {
        commercialTermComponent.setUpdateTime(value);
        return this;
    }

    /** 返回本 Builder 持有的对象；多次调用返回同一实例。 */
    public CommercialTermComponent build() {
        return commercialTermComponent;
    }
}
