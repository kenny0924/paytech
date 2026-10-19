package com.paytech.commercial.test.builder;

import java.time.Instant;

import com.paytech.commercial.core.dal.dataobject.AgreementOperatingEntity;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;

/**
 * t_agreement_operating_entity 测试行的 Builder：template() 给出合法默认值，用例只覆盖与场景相关的字段。
 * <li>不访问数据库；可显式设置 null 或非法组合构造负例，build() 不修复输入。</li>
 */
public final class AgreementOperatingEntityBuilder {
    /** 默认覆盖关系 ID */
    public static final long AGREEMENT_OPERATING_ENTITY_ID = 930000000000031L;

    private final AgreementOperatingEntity agreementOperatingEntity = new AgreementOperatingEntity();

    private AgreementOperatingEntityBuilder() {
    }

    /**
     * 默认合同覆盖 AE993。
     *
     * @return 新 Builder
     */
    public static AgreementOperatingEntityBuilder template() {
        return new AgreementOperatingEntityBuilder()
            .agreementOperatingEntityId(AGREEMENT_OPERATING_ENTITY_ID)
            .agreementId(AgreementBuilder.AGREEMENT_ID)
            .entityNo(AgreementBuilder.ENTITY_NO)
            .createTime(CheckoutFixture.NOW)
            .updateTime(CheckoutFixture.NOW);
    }

    /** 设置覆盖关系标识。 */
    public AgreementOperatingEntityBuilder agreementOperatingEntityId(Long value) {
        agreementOperatingEntity.setAgreementOperatingEntityId(value);
        return this;
    }

    /** 设置所属合同。 */
    public AgreementOperatingEntityBuilder agreementId(Long value) {
        agreementOperatingEntity.setAgreementId(value);
        return this;
    }

    /** 设置覆盖的准确 OE。 */
    public AgreementOperatingEntityBuilder entityNo(String value) {
        agreementOperatingEntity.setEntityNo(value);
        return this;
    }

    /** 设置记录创建的 UTC 微秒时间。 */
    public AgreementOperatingEntityBuilder createTime(Instant value) {
        agreementOperatingEntity.setCreateTime(value);
        return this;
    }

    /** 设置记录最近一次持久化更新的 UTC 微秒时间。 */
    public AgreementOperatingEntityBuilder updateTime(Instant value) {
        agreementOperatingEntity.setUpdateTime(value);
        return this;
    }

    /** 返回本 Builder 持有的对象；多次调用返回同一实例。 */
    public AgreementOperatingEntity build() {
        return agreementOperatingEntity;
    }
}
