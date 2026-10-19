package com.paytech.commercial.test.builder;

import java.time.Instant;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.core.dal.dataobject.PricingProfileCond;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;

/**
 * t_pricing_profile_cond 测试行的 Builder：template() 给出合法默认值，用例只覆盖与场景相关的字段。
 * <li>不访问数据库；可显式设置 null 或非法组合构造负例，build() 不修复输入。</li>
 */
public final class PricingProfileCondBuilder {
    /** 默认条件行 ID */
    public static final long PROFILE_CONDITION_ID = 930000000000051L;

    private final PricingProfileCond pricingProfileCond = new PricingProfileCond();

    private PricingProfileCondBuilder() {
    }

    /**
     * 默认画像的 CARD_BRAND=VISA 条件。
     *
     * @return 新 Builder
     */
    public static PricingProfileCondBuilder template() {
        return new PricingProfileCondBuilder()
            .profileConditionId(PROFILE_CONDITION_ID)
            .pricingProfileId(PricingProfileBuilder.PRICING_PROFILE_ID)
            .dimensionCode(PricingDimension.CARD_BRAND)
            .conditionValue(CommercialCodes.CardBrand.VISA)
            .createTime(CheckoutFixture.NOW)
            .updateTime(CheckoutFixture.NOW);
    }

    /** 设置条件行标识。 */
    public PricingProfileCondBuilder profileConditionId(Long value) {
        pricingProfileCond.setProfileConditionId(value);
        return this;
    }

    /** 设置所属画像。 */
    public PricingProfileCondBuilder pricingProfileId(Long value) {
        pricingProfileCond.setPricingProfileId(value);
        return this;
    }

    /** 设置定价维度。 */
    public PricingProfileCondBuilder dimensionCode(PricingDimension value) {
        pricingProfileCond.setDimensionCode(value);
        return this;
    }

    /** 设置该维度的一个规范化值。 */
    public PricingProfileCondBuilder conditionValue(String value) {
        pricingProfileCond.setConditionValue(value);
        return this;
    }

    /** 设置记录创建的 UTC 微秒时间。 */
    public PricingProfileCondBuilder createTime(Instant value) {
        pricingProfileCond.setCreateTime(value);
        return this;
    }

    /** 设置记录最近一次持久化更新的 UTC 微秒时间。 */
    public PricingProfileCondBuilder updateTime(Instant value) {
        pricingProfileCond.setUpdateTime(value);
        return this;
    }

    /** 返回本 Builder 持有的对象；多次调用返回同一实例。 */
    public PricingProfileCond build() {
        return pricingProfileCond;
    }
}
