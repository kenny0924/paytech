package com.paytech.commercial.test.builder;

import java.time.Instant;

import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.FundMode;
import com.paytech.commercial.api.enums.PricingAuthority;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.domain.enums.AgreementAgreementFormat;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;

/**
 * t_agreement 测试行的 Builder：template() 给出合法默认值，用例只覆盖与场景相关的字段。
 * <li>不访问数据库；可显式设置 null 或非法组合构造负例，build() 不修复输入。</li>
 */
public final class AgreementBuilder {
    /** 默认合同 ID */
    public static final long AGREEMENT_ID = 930000000000001L;

    /** 默认合同编号 */
    public static final String AGREEMENT_NO = "WRITE-AGREEMENT-1";

    /** 默认 Client */
    public static final long CLIENT_ID = 930000000000901L;

    /** 默认 OE */
    public static final String ENTITY_NO = "AE993";

    /** 默认创建 Actor */
    public static final long ACTOR_ID = 930000000000902L;

    private final Agreement agreement = new Agreement();

    private AgreementBuilder() {
    }

    /**
     * 合成 CLIENT 合同：MOR、经平台结算、平台定价，绑定 AE993，DRAFT 且未签署。
     *
     * @return 新 Builder
     */
    public static AgreementBuilder template() {
        return new AgreementBuilder()
            .agreementId(AGREEMENT_ID)
            .agreementNo(AGREEMENT_NO)
            .agreementName("Synthetic Write Contract")
            .agreementType(AgreementAgreementType.CLIENT)
            .businessMode(BusinessMode.MOR)
            .fundMode(FundMode.SETTLE_THROUGH_PLATFORM)
            .pricingAuthority(PricingAuthority.PLATFORM)
            .referralPartnerId(null)
            .clientId(CLIENT_ID)
            .localPartnerId(null)
            .entityNo(ENTITY_NO)
            .agreementFormat(AgreementAgreementFormat.MERGED_PARTY)
            .status(AgreementStatus.DRAFT)
            .effectiveFrom(Instant.parse("2026-09-01T00:00:00Z"))
            .effectiveTo(null)
            .signedTime(null)
            .activatedTime(null)
            .createBy(ACTOR_ID)
            .createTime(CheckoutFixture.NOW)
            .updateTime(CheckoutFixture.NOW);
    }

    /** 设置合同内部唯一标识。 */
    public AgreementBuilder agreementId(Long value) {
        agreement.setAgreementId(value);
        return this;
    }

    /** 设置可在业务操作中识别合同的唯一编号，uk_agreement_no。 */
    public AgreementBuilder agreementNo(String value) {
        agreement.setAgreementNo(value);
        return this;
    }

    /** 设置合同展示名称。 */
    public AgreementBuilder agreementName(String value) {
        agreement.setAgreementName(value);
        return this;
    }

    /** 设置合同类型：CLIENT 或 PARTNER。 */
    public AgreementBuilder agreementType(AgreementAgreementType value) {
        agreement.setAgreementType(value);
        return this;
    }

    /** 设置CLIENT 合同必填的业务模式，PARTNER 为空；创建后不可修改。 */
    public AgreementBuilder businessMode(BusinessMode value) {
        agreement.setBusinessMode(value);
        return this;
    }

    /** 设置CLIENT 合同必填的资金模式，PARTNER 为空；本期只接受 SETTLE_THROUGH_PLATFORM。 */
    public AgreementBuilder fundMode(FundMode value) {
        agreement.setFundMode(value);
        return this;
    }

    /** 设置CLIENT 合同必填的定价权归属，PARTNER 为空；本期只接受 PLATFORM。 */
    public AgreementBuilder pricingAuthority(PricingAuthority value) {
        agreement.setPricingAuthority(value);
        return this;
    }

    /** 设置推荐伙伴（client-master Local Partner 值引用）；pricing_authority=PARTNER 时必填。 */
    public AgreementBuilder referralPartnerId(Long value) {
        agreement.setReferralPartnerId(value);
        return this;
    }

    /** 设置CLIENT 合同的 Client 标识；PARTNER 为空。 */
    public AgreementBuilder clientId(Long value) {
        agreement.setClientId(value);
        return this;
    }

    /** 设置PARTNER 合同的 Local Partner 标识；CLIENT 为空。 */
    public AgreementBuilder localPartnerId(Long value) {
        agreement.setLocalPartnerId(value);
        return this;
    }

    /** 设置CLIENT 合同准确绑定的 OE；PARTNER 为空，覆盖 OE 见 t_agreement_operating_entity。 */
    public AgreementBuilder entityNo(String value) {
        agreement.setEntityNo(value);
        return this;
    }

    /** 设置新同 Party 共签或旧逐角色签署；创建后不可修改。 */
    public AgreementBuilder agreementFormat(AgreementAgreementFormat value) {
        agreement.setAgreementFormat(value);
        return this;
    }

    /** 设置法律生命周期状态。 */
    public AgreementBuilder status(AgreementStatus value) {
        agreement.setStatus(value);
        return this;
    }

    /** 设置生效区间 UTC 起点（含）。 */
    public AgreementBuilder effectiveFrom(Instant value) {
        agreement.setEffectiveFrom(value);
        return this;
    }

    /** 设置生效区间 UTC 终点（不含）；空为未预定结束。 */
    public AgreementBuilder effectiveTo(Instant value) {
        agreement.setEffectiveTo(value);
        return this;
    }

    /** 设置完成必要签署的 UTC 时刻。 */
    public AgreementBuilder signedTime(Instant value) {
        agreement.setSignedTime(value);
        return this;
    }

    /** 设置进入 ACTIVE 的 UTC 时刻。 */
    public AgreementBuilder activatedTime(Instant value) {
        agreement.setActivatedTime(value);
        return this;
    }

    /** 设置创建该行的 Human 或 System Actor。 */
    public AgreementBuilder createBy(Long value) {
        agreement.setCreateBy(value);
        return this;
    }

    /** 设置记录创建的 UTC 微秒时间。 */
    public AgreementBuilder createTime(Instant value) {
        agreement.setCreateTime(value);
        return this;
    }

    /** 设置记录最近一次持久化更新的 UTC 微秒时间。 */
    public AgreementBuilder updateTime(Instant value) {
        agreement.setUpdateTime(value);
        return this;
    }

    /** 返回本 Builder 持有的对象；多次调用返回同一实例。 */
    public Agreement build() {
        return agreement;
    }
}
