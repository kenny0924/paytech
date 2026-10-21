package com.paytech.commercial.test.builder;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.AgreementCapabilityStatus;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;

/**
 * t_agreement_capability 测试行的 Builder：template() 给出合法默认值，用例只覆盖与场景相关的字段。
 * <li>不访问数据库；可显式设置 null 或非法组合构造负例，build() 不修复输入。</li>
 */
public final class AgreementCapabilityBuilder {
    /** 默认开通项 ID */
    public static final long AGREEMENT_CAPABILITY_ID = 920000000000401L;

    /** 合成 CLIENT 合同的 OE */
    public static final String ENTITY_NO = "AE991";

    /** 默认开通生效起点 */
    public static final Instant EFFECTIVE_FROM = Instant.parse("2026-09-01T00:00:00Z");

    private final AgreementCapability agreementCapability = new AgreementCapability();

    private AgreementCapabilityBuilder() {
    }

    /**
     * 合成 CLIENT 合同在 AE991 下 ENABLED 的 CARD，自 2026-09-01 起无结束，无保证金、不要求 3DS。
     *
     * @return 新 Builder
     */
    public static AgreementCapabilityBuilder template() {
        return new AgreementCapabilityBuilder()
            .agreementCapabilityId(AGREEMENT_CAPABILITY_ID)
            .agreementId(AgreementProductBuilder.AGREEMENT_ID)
            .entityNo(ENTITY_NO)
            .capabilityCode(CommercialCodes.Capability.CARD)
            .status(AgreementCapabilityStatus.ENABLED)
            .effectiveFrom(EFFECTIVE_FROM)
            .effectiveTo(null)
            .rollingReserveRate(null)
            .rollingReserveDays(null)
            .require3ds(false)
            .createTime(CheckoutFixture.NOW)
            .updateTime(CheckoutFixture.NOW);
    }

    /** 设置是否要求 3DS。 */
    public AgreementCapabilityBuilder require3ds(Boolean value) {
        agreementCapability.setRequire3ds(value);
        return this;
    }

    /** 设置开通项行的内部唯一标识。 */
    public AgreementCapabilityBuilder agreementCapabilityId(Long value) {
        agreementCapability.setAgreementCapabilityId(value);
        return this;
    }

    /** 设置所属合同。 */
    public AgreementCapabilityBuilder agreementId(Long value) {
        agreementCapability.setAgreementId(value);
        return this;
    }

    /** 设置开通项适用的准确 OE。 */
    public AgreementCapabilityBuilder entityNo(String value) {
        agreementCapability.setEntityNo(value);
        return this;
    }

    /** 设置开通的可开通项代码。 */
    public AgreementCapabilityBuilder capabilityCode(String value) {
        agreementCapability.setCapabilityCode(value);
        return this;
    }

    /** 设置开通项状态。 */
    public AgreementCapabilityBuilder status(AgreementCapabilityStatus value) {
        agreementCapability.setStatus(value);
        return this;
    }

    /** 设置开通生效区间的 UTC 微秒起点，包含该时刻。 */
    public AgreementCapabilityBuilder effectiveFrom(Instant value) {
        agreementCapability.setEffectiveFrom(value);
        return this;
    }

    /** 设置开通生效区间的 UTC 微秒终点，不包含该时刻。 */
    public AgreementCapabilityBuilder effectiveTo(Instant value) {
        agreementCapability.setEffectiveTo(value);
        return this;
    }

    /** 设置滚动保证金按交易金额计提的小数比例。 */
    public AgreementCapabilityBuilder rollingReserveRate(BigDecimal value) {
        agreementCapability.setRollingReserveRate(value);
        return this;
    }

    /** 设置滚动保证金的持有天数。 */
    public AgreementCapabilityBuilder rollingReserveDays(Integer value) {
        agreementCapability.setRollingReserveDays(value);
        return this;
    }

    /** 设置记录创建的 UTC 微秒时间。 */
    public AgreementCapabilityBuilder createTime(Instant value) {
        agreementCapability.setCreateTime(value);
        return this;
    }

    /** 设置记录最近一次持久化更新的 UTC 微秒时间。 */
    public AgreementCapabilityBuilder updateTime(Instant value) {
        agreementCapability.setUpdateTime(value);
        return this;
    }

    /** 返回本 Builder 持有的对象；多次调用返回同一实例。 */
    public AgreementCapability build() {
        return agreementCapability;
    }
}
