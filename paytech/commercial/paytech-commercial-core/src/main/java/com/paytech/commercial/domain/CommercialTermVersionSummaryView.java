package com.paytech.commercial.domain;

import java.time.Instant;

import com.paytech.commercial.api.enums.FeeTaxTreatment;
import com.paytech.commercial.api.enums.PricingModel;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.enums.CommercialTermRoundingMode;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;

import lombok.Getter;

/**
 * 版本列表一行：版本、所属方案、方案 BCR 根版本、价格条数与本轮审批事实的多表组合
 * <li>不携带 DataObject，供 app 入站适配器转换为 ResponseBody。</li>
 */
@Getter
public class CommercialTermVersionSummaryView {
    /** 版本 ID */
    private final long versionId;

    /** 方案 ID */
    private final long planId;

    /** 方案编号 */
    private final String planCode;

    /** 条款类型 */
    private final String termType;

    /** 合同 ID，可空 */
    private final Long agreementId;

    /** 方案 OE，可空 */
    private final String entityNo;

    /** 方案 Client，可空 */
    private final Long clientId;

    /** 方案 Local Partner，可空 */
    private final Long localPartnerId;

    /** 版本号 */
    private final long versionNo;

    /** 版本状态 */
    private final CommercialTermVersionStatus status;

    /** 生效起点，可空 */
    private final Instant effectiveFrom;

    /** 生效终点，可空 */
    private final Instant effectiveTo;

    /** 舍入方式 */
    private final CommercialTermRoundingMode roundingMode;

    /** 定价模型 */
    private final PricingModel pricingModel;

    /** 费用税务处理 */
    private final FeeTaxTreatment feeTaxTreatment;

    /** 本轮审批事实 */
    private final CommercialTermReviewFacts review;

    /** 价格条数 */
    private final int priceCount;

    /** 方案 BCR 根当前版本 */
    private final long changeVersion;

    /** 版本行最近更新时间 */
    private final Instant updateTime;

    /**
     * 由同一次列表查询读取的行构造
     *
     * @param plan 所属方案
     * @param version 版本
     * @param changeVersion 方案 BCR 根当前版本
     * @param priceCount 价格条数
     * @param review 本轮审批事实
     */
    public CommercialTermVersionSummaryView(CommercialTermPlan plan, CommercialTermVersion version, long changeVersion,
        int priceCount, CommercialTermReviewFacts review) {
        this.versionId = version.getTermVersionId();
        this.planId = plan.getTermPlanId();
        this.planCode = plan.getPlanCode();
        this.termType = plan.getTermType();
        this.agreementId = version.getAgreementId();
        this.entityNo = plan.getEntityNo();
        this.clientId = plan.getClientId();
        this.localPartnerId = plan.getLocalPartnerId();
        this.versionNo = version.getVersionNo();
        this.status = version.getStatus();
        this.effectiveFrom = version.getEffectiveFrom();
        this.effectiveTo = version.getEffectiveTo();
        this.roundingMode = version.getRoundingMode();
        this.pricingModel = version.getPricingModel();
        this.feeTaxTreatment = version.getFeeTaxTreatment();
        this.review = review;
        this.priceCount = priceCount;
        this.changeVersion = changeVersion;
        this.updateTime = version.getUpdateTime();
    }
}
