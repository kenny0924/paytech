package com.paytech.commercial.test.builder;

import java.time.Instant;

import com.paytech.commercial.core.dal.dataobject.AgreementParty;
import com.paytech.commercial.domain.enums.AgreementPartyAcceptanceMethod;
import com.paytech.commercial.domain.enums.AgreementPartyPartyRole;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;

/**
 * t_agreement_party 测试行的 Builder：template() 给出合法默认值，用例只覆盖与场景相关的字段。
 * <li>不访问数据库；可显式设置 null 或非法组合构造负例，build() 不修复输入。</li>
 */
public final class AgreementPartyBuilder {
    /** 默认角色行 ID */
    public static final long AGREEMENT_PARTY_ID = 930000000000011L;

    /** 默认法律 Party */
    public static final long PARTY_ID = 930000000000903L;

    /** 默认 Party 资料版本 */
    public static final long PARTY_PROFILE_ID = 930000000000904L;

    private final AgreementParty agreementParty = new AgreementParty();

    private AgreementPartyBuilder() {
    }

    /**
     * 默认合同下 CLIENT 角色的待签行（签署字段全空）。
     *
     * @return 新 Builder
     */
    public static AgreementPartyBuilder template() {
        return new AgreementPartyBuilder()
            .agreementPartyId(AGREEMENT_PARTY_ID)
            .agreementId(AgreementBuilder.AGREEMENT_ID)
            .partyId(PARTY_ID)
            .partyProfileId(PARTY_PROFILE_ID)
            .partyRole(AgreementPartyPartyRole.CLIENT)
            .acceptanceMethod(null)
            .acceptedByActorId(null)
            .acceptedTime(null)
            .evidenceDocumentId(null)
            .acceptanceReference(null)
            .acceptanceRefSlot(null)
            .acceptanceRecordedBy(null)
            .acceptanceRecordedAt(null)
            .createBy(AgreementBuilder.ACTOR_ID)
            .createTime(CheckoutFixture.NOW)
            .updateTime(CheckoutFixture.NOW);
    }

    /** 设置Party 角色记录标识。 */
    public AgreementPartyBuilder agreementPartyId(Long value) {
        agreementParty.setAgreementPartyId(value);
        return this;
    }

    /** 设置所属合同。 */
    public AgreementPartyBuilder agreementId(Long value) {
        agreementParty.setAgreementId(value);
        return this;
    }

    /** 设置法律 Party 标识。 */
    public AgreementPartyBuilder partyId(Long value) {
        agreementParty.setPartyId(value);
        return this;
    }

    /** 设置合同冻结引用的 Party 资料版本。 */
    public AgreementPartyBuilder partyProfileId(Long value) {
        agreementParty.setPartyProfileId(value);
        return this;
    }

    /** 设置Party 在合同中的角色。 */
    public AgreementPartyBuilder partyRole(AgreementPartyPartyRole value) {
        agreementParty.setPartyRole(value);
        return this;
    }

    /** 设置签署方式；未签为空。 */
    public AgreementPartyBuilder acceptanceMethod(AgreementPartyAcceptanceMethod value) {
        agreementParty.setAcceptanceMethod(value);
        return this;
    }

    /** 设置代表签约方完成接受的外部 Actor。 */
    public AgreementPartyBuilder acceptedByActorId(Long value) {
        agreementParty.setAcceptedByActorId(value);
        return this;
    }

    /** 设置接受事实成立的 UTC 时刻；未签为空。 */
    public AgreementPartyBuilder acceptedTime(Instant value) {
        agreementParty.setAcceptedTime(value);
        return this;
    }

    /** 设置签署证据文件引用。 */
    public AgreementPartyBuilder evidenceDocumentId(Long value) {
        agreementParty.setEvidenceDocumentId(value);
        return this;
    }

    /** 设置平台生成的全局唯一合同接受事件编号。 */
    public AgreementPartyBuilder acceptanceReference(String value) {
        agreementParty.setAcceptanceReference(value);
        return this;
    }

    /** 设置1 或 NULL：同合同同 Party 最小角色主键行签后为 1，其他为空。 */
    public AgreementPartyBuilder acceptanceRefSlot(Integer value) {
        agreementParty.setAcceptanceRefSlot(value);
        return this;
    }

    /** 设置登记最终签署证据的可信 Actor。 */
    public AgreementPartyBuilder acceptanceRecordedBy(Long value) {
        agreementParty.setAcceptanceRecordedBy(value);
        return this;
    }

    /** 设置首次登记最终签署结果的 UTC 时间。 */
    public AgreementPartyBuilder acceptanceRecordedAt(Instant value) {
        agreementParty.setAcceptanceRecordedAt(value);
        return this;
    }

    /** 设置创建该行的 Actor。 */
    public AgreementPartyBuilder createBy(Long value) {
        agreementParty.setCreateBy(value);
        return this;
    }

    /** 设置记录创建的 UTC 微秒时间。 */
    public AgreementPartyBuilder createTime(Instant value) {
        agreementParty.setCreateTime(value);
        return this;
    }

    /** 设置记录最近一次持久化更新的 UTC 微秒时间。 */
    public AgreementPartyBuilder updateTime(Instant value) {
        agreementParty.setUpdateTime(value);
        return this;
    }

    /** 返回本 Builder 持有的对象；多次调用返回同一实例。 */
    public AgreementParty build() {
        return agreementParty;
    }
}
