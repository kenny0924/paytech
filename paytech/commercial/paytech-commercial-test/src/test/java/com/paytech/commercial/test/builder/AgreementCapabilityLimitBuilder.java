package com.paytech.commercial.test.builder;

import java.time.Instant;

import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;

/**
 * t_agreement_capability_limit 测试行的 Builder：template() 给出合法默认值，用例只覆盖与场景相关的字段。
 * <li>不访问数据库；可显式设置 null 或非法组合构造负例，build() 不修复输入。</li>
 */
public final class AgreementCapabilityLimitBuilder {
    /** 默认约束行 ID */
    public static final long AGREEMENT_CAPABILITY_LIMIT_ID = 920000000000501L;

    private final AgreementCapabilityLimit agreementCapabilityLimit = new AgreementCapabilityLimit();

    private AgreementCapabilityLimitBuilder() {
    }

    /**
     * 默认开通项上的 CURRENCY=USD 约束。
     *
     * @return 新 Builder
     */
    public static AgreementCapabilityLimitBuilder template() {
        return new AgreementCapabilityLimitBuilder()
            .agreementCapabilityLimitId(AGREEMENT_CAPABILITY_LIMIT_ID)
            .agreementCapabilityId(AgreementCapabilityBuilder.AGREEMENT_CAPABILITY_ID)
            .limitCode(PricingDimension.CURRENCY)
            .limitValue("USD")
            .createTime(CheckoutFixture.NOW)
            .updateTime(CheckoutFixture.NOW);
    }

    /** 设置约束行的内部唯一标识。 */
    public AgreementCapabilityLimitBuilder agreementCapabilityLimitId(Long value) {
        agreementCapabilityLimit.setAgreementCapabilityLimitId(value);
        return this;
    }

    /** 设置所属开通项。 */
    public AgreementCapabilityLimitBuilder agreementCapabilityId(Long value) {
        agreementCapabilityLimit.setAgreementCapabilityId(value);
        return this;
    }

    /** 设置条件维度。 */
    public AgreementCapabilityLimitBuilder limitCode(PricingDimension value) {
        agreementCapabilityLimit.setLimitCode(value);
        return this;
    }

    /** 设置约束的规范化值。 */
    public AgreementCapabilityLimitBuilder limitValue(String value) {
        agreementCapabilityLimit.setLimitValue(value);
        return this;
    }

    /** 设置记录创建的 UTC 微秒时间。 */
    public AgreementCapabilityLimitBuilder createTime(Instant value) {
        agreementCapabilityLimit.setCreateTime(value);
        return this;
    }

    /** 设置记录最近一次持久化更新的 UTC 微秒时间。 */
    public AgreementCapabilityLimitBuilder updateTime(Instant value) {
        agreementCapabilityLimit.setUpdateTime(value);
        return this;
    }

    /** 返回本 Builder 持有的对象；多次调用返回同一实例。 */
    public AgreementCapabilityLimit build() {
        return agreementCapabilityLimit;
    }
}
