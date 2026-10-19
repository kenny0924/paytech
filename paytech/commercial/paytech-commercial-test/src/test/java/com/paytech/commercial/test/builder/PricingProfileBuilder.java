package com.paytech.commercial.test.builder;

import java.time.Instant;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.PricingProfileKind;
import com.paytech.commercial.core.dal.dataobject.PricingProfile;
import com.paytech.commercial.domain.enums.PricingProfileStatus;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;

/**
 * t_pricing_profile 测试行的 Builder：template() 给出合法默认值，用例只覆盖与场景相关的字段。
 * <li>不访问数据库；可显式设置 null 或非法组合构造负例，build() 不修复输入。</li>
 */
public final class PricingProfileBuilder {
    /** 默认画像 ID */
    public static final long PRICING_PROFILE_ID = 930000000000041L;

    /** 默认画像代码 */
    public static final String PRICING_PROFILE_CODE = "WRITE_CARD";

    private final PricingProfile pricingProfile = new PricingProfile();

    private PricingProfileBuilder() {
    }

    /**
     * DRAFT 的银行卡定价画像。
     *
     * @return 新 Builder
     */
    public static PricingProfileBuilder template() {
        return new PricingProfileBuilder()
            .pricingProfileId(PRICING_PROFILE_ID)
            .pricingProfileCode(PRICING_PROFILE_CODE)
            .profileName("Synthetic Card Profile")
            .profileKind(PricingProfileKind.PAYMENT_METHOD)
            .paymentMethodCode(CommercialCodes.PaymentMethod.CARD)
            .status(PricingProfileStatus.DRAFT)
            .createTime(CheckoutFixture.NOW)
            .updateTime(CheckoutFixture.NOW);
    }

    /** 设置画像内部标识。 */
    public PricingProfileBuilder pricingProfileId(Long value) {
        pricingProfile.setPricingProfileId(value);
        return this;
    }

    /** 设置全局唯一稳定代码，创建后不可修改、不可复用。 */
    public PricingProfileBuilder pricingProfileCode(String value) {
        pricingProfile.setPricingProfileCode(value);
        return this;
    }

    /** 设置展示名称。 */
    public PricingProfileBuilder profileName(String value) {
        pricingProfile.setProfileName(value);
        return this;
    }

    /** 设置定价对象种类。 */
    public PricingProfileBuilder profileKind(PricingProfileKind value) {
        pricingProfile.setProfileKind(value);
        return this;
    }

    /** 设置profile_kind=PAYMENT_METHOD 时必填的 Payment Method 代码，其他种类为空。 */
    public PricingProfileBuilder paymentMethodCode(String value) {
        pricingProfile.setPaymentMethodCode(value);
        return this;
    }

    /** 设置生命周期状态。 */
    public PricingProfileBuilder status(PricingProfileStatus value) {
        pricingProfile.setStatus(value);
        return this;
    }

    /** 设置记录创建的 UTC 微秒时间。 */
    public PricingProfileBuilder createTime(Instant value) {
        pricingProfile.setCreateTime(value);
        return this;
    }

    /** 设置记录最近一次持久化更新的 UTC 微秒时间。 */
    public PricingProfileBuilder updateTime(Instant value) {
        pricingProfile.setUpdateTime(value);
        return this;
    }

    /** 返回本 Builder 持有的对象；多次调用返回同一实例。 */
    public PricingProfile build() {
        return pricingProfile;
    }
}
