package com.paytech.commercial.core.dal.dataobject;

import java.time.Instant;

import com.paytech.commercial.api.enums.FeeTaxTreatment;
import com.paytech.commercial.api.enums.PricingModel;
import com.paytech.commercial.domain.enums.CommercialTermRoundingMode;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;

import lombok.Getter;
import lombok.Setter;

/**
 * t_commercial_term_version：商业条款不可变业务版本。
 * <li>APPROVED 后 Owner、合同、版本号、生效起点、舍入方式与 Price/Component/Band 冻结；仅 ACTIVE 占 active_slot=1。</li>
 */
@Getter
@Setter
public class CommercialTermVersion {
    /** 版本标识 */
    private Long termVersionId;

    /** 所属方案 */
    private Long termPlanId;

    /** 版本冻结引用的合同；模板版本为空 */
    private Long agreementId;

    /** 方案内版本序号 */
    private Long versionNo;

    /** 版本状态 */
    private CommercialTermVersionStatus status;

    /** 1 或 NULL：ACTIVE 为 1，其他状态为空 */
    private Integer activeSlot;

    /** 生效区间 UTC 起点（含） */
    private Instant effectiveFrom;

    /** 生效区间 UTC 终点（不含）；空为未预定结束 */
    private Instant effectiveTo;

    /** 金额舍入规则 */
    private CommercialTermRoundingMode roundingMode;

    /** 定价模型；本期只接受 BLENDED */
    private PricingModel pricingModel;

    /** 费用金额是否含税 */
    private FeeTaxTreatment feeTaxTreatment;

    /** 基于标准价目模板生成时的来源模板版本；手工或复制创建为空 */
    private Long sourceTemplateVersionId;

    /** 迁移前已排期或生效的存量版本免客户签署 */
    private Boolean signatureExempt;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
