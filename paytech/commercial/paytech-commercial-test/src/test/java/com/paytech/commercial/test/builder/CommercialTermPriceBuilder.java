package com.paytech.commercial.test.builder;

import java.time.Instant;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;

/**
 * t_commercial_term_price 测试行的 Builder：template() 给出合法默认值，用例只覆盖与场景相关的字段。
 * <li>不访问数据库；可显式设置 null 或非法组合构造负例，build() 不修复输入。</li>
 */
public final class CommercialTermPriceBuilder {
    /** 默认价格 ID */
    public static final long TERM_PRICE_ID = 930000000000081L;

    private final CommercialTermPrice commercialTermPrice = new CommercialTermPrice();

    private CommercialTermPriceBuilder() {
    }

    /**
     * 默认版本在 AE993、USD 下引用默认画像的 CLIENT 价格，T+2、AE 工作日日历、UTC、86399。
     *
     * @return 新 Builder
     */
    public static CommercialTermPriceBuilder template() {
        return new CommercialTermPriceBuilder()
            .termPriceId(TERM_PRICE_ID)
            .termVersionId(CommercialTermVersionBuilder.TERM_VERSION_ID)
            .entityNo(AgreementBuilder.ENTITY_NO)
            .currency("USD")
            .pricingProfileCode(PricingProfileBuilder.PRICING_PROFILE_CODE)
            .priorityNo(1)
            .settlementBusinessDays(2)
            .businessCalendarCode(CommercialCodes.AE_BUSINESS_DAYS)
            .settlementTimezone("UTC")
            .cutoffSecondOfDay(86399)
            .createTime(CheckoutFixture.NOW)
            .updateTime(CheckoutFixture.NOW);
    }

    /** 设置价格标识。 */
    public CommercialTermPriceBuilder termPriceId(Long value) {
        commercialTermPrice.setTermPriceId(value);
        return this;
    }

    /** 设置所属版本。 */
    public CommercialTermPriceBuilder termVersionId(Long value) {
        commercialTermPrice.setTermVersionId(value);
        return this;
    }

    /** 设置准确 OE。 */
    public CommercialTermPriceBuilder entityNo(String value) {
        commercialTermPrice.setEntityNo(value);
        return this;
    }

    /** 设置ISO 4217 交易币种，即费用币种。 */
    public CommercialTermPriceBuilder currency(String value) {
        commercialTermPrice.setCurrency(value);
        return this;
    }

    /** 设置引用的 ACTIVE 定价画像代码。 */
    public CommercialTermPriceBuilder pricingProfileCode(String value) {
        commercialTermPrice.setPricingProfileCode(value);
        return this;
    }

    /** 设置同版本、OE 与币种下相同具体度候选的顺序，越小越优先。 */
    public CommercialTermPriceBuilder priorityNo(Integer value) {
        commercialTermPrice.setPriorityNo(value);
        return this;
    }

    /** 设置T+N 工作日结算天数。 */
    public CommercialTermPriceBuilder settlementBusinessDays(Integer value) {
        commercialTermPrice.setSettlementBusinessDays(value);
        return this;
    }

    /** 设置结算业务日历代码。 */
    public CommercialTermPriceBuilder businessCalendarCode(String value) {
        commercialTermPrice.setBusinessCalendarCode(value);
        return this;
    }

    /** 设置IANA 时区，本期仅 UTC。 */
    public CommercialTermPriceBuilder settlementTimezone(String value) {
        commercialTermPrice.setSettlementTimezone(value);
        return this;
    }

    /** 设置日内截止秒数 0–86399。 */
    public CommercialTermPriceBuilder cutoffSecondOfDay(Integer value) {
        commercialTermPrice.setCutoffSecondOfDay(value);
        return this;
    }

    /** 设置记录创建的 UTC 微秒时间。 */
    public CommercialTermPriceBuilder createTime(Instant value) {
        commercialTermPrice.setCreateTime(value);
        return this;
    }

    /** 设置记录最近一次持久化更新的 UTC 微秒时间。 */
    public CommercialTermPriceBuilder updateTime(Instant value) {
        commercialTermPrice.setUpdateTime(value);
        return this;
    }

    /** 返回本 Builder 持有的对象；多次调用返回同一实例。 */
    public CommercialTermPrice build() {
        return commercialTermPrice;
    }
}
