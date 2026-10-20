package com.paytech.commercial.domain;

import java.time.Instant;

import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.FundMode;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.AgreementStatus;

import lombok.Getter;

/**
 * 合同列表一行：t_agreement 字段与 AGREEMENT BCR Root 当前版本的两表组合
 * <li>不携带 DataObject，供 app 入站适配器转换为 ResponseBody。</li>
 */
@Getter
public class AgreementSummaryView {
    /** 合同 ID */
    private final long agreementId;

    /** 合同编号 */
    private final String agreementNo;

    /** 合同名称 */
    private final String agreementName;

    /** 合同类型 */
    private final AgreementAgreementType agreementType;

    /** 合同状态 */
    private final AgreementStatus status;

    /** 业务模式，PARTNER 合同为空 */
    private final BusinessMode businessMode;

    /** 资金模式，PARTNER 合同为空 */
    private final FundMode fundMode;

    /** Client */
    private final Long clientId;

    /** Local Partner */
    private final Long localPartnerId;

    /** CLIENT 合同 OE */
    private final String entityNo;

    /** 生效起点 */
    private final Instant effectiveFrom;

    /** 生效终点 */
    private final Instant effectiveTo;

    /** 签署完成时刻 */
    private final Instant signedTime;

    /** 激活时刻 */
    private final Instant activatedTime;

    /** 合同 BCR Root 当前版本 */
    private final long changeVersion;

    /** 合同行最近更新时间 */
    private final Instant updateTime;

    /**
     * 由合同行与其 Root 当前版本构造
     *
     * @param agreement 合同
     * @param changeVersion Root 当前版本
     */
    public AgreementSummaryView(Agreement agreement, long changeVersion) {
        this.agreementId = agreement.getAgreementId();
        this.agreementNo = agreement.getAgreementNo();
        this.agreementName = agreement.getAgreementName();
        this.agreementType = agreement.getAgreementType();
        this.status = agreement.getStatus();
        this.businessMode = agreement.getBusinessMode();
        this.fundMode = agreement.getFundMode();
        this.clientId = agreement.getClientId();
        this.localPartnerId = agreement.getLocalPartnerId();
        this.entityNo = agreement.getEntityNo();
        this.effectiveFrom = agreement.getEffectiveFrom();
        this.effectiveTo = agreement.getEffectiveTo();
        this.signedTime = agreement.getSignedTime();
        this.activatedTime = agreement.getActivatedTime();
        this.changeVersion = changeVersion;
        this.updateTime = agreement.getUpdateTime();
    }
}
