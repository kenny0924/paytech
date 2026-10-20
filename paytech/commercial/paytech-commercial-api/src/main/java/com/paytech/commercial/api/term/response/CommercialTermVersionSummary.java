package com.paytech.commercial.api.term.response;

import lombok.Getter;
import lombok.Setter;

/** 版本列表中的一行：版本字段、所属方案摘要、方案 BCR 根版本、审批人与价格条数。 */
@Getter
@Setter
public class CommercialTermVersionSummary {
    /** 版本 ID */
    private String versionId;

    /** 方案 ID */
    private String planId;

    /** 方案编号 */
    private String planCode;

    /** 条款类型（开放目录代码） */
    private String termType;

    /** 合同 ID，Partner 级方案为空 */
    private String agreementId;

    /** 方案 OE 编号，可空 */
    private String entityNo;

    /** 方案 Client ID，Client 条款非空 */
    private String clientId;

    /** 方案 Local Partner ID，Partner 条款非空 */
    private String localPartnerId;

    /** 方案内版本号 */
    private String versionNo;

    /**
     * 版本状态
     * <li>DRAFT、PENDING_APPROVAL、APPROVED、SCHEDULED、ACTIVE、SUPERSEDED、EXPIRED。</li>
     */
    private String status;

    /** 生效起点，UTC ISO 8601；排期前为版本创建时刻占位，排期时改写 */
    private String effectiveFrom;

    /** 生效终点（不含），UTC ISO 8601；为空表示无终点 */
    private String effectiveTo;

    /** 舍入方式 */
    private String roundingMode;

    /** 定价模型 */
    private String pricingModel;

    /** 费用税务处理 */
    private String feeTaxTreatment;

    /**
     * 本轮审批的 Maker（提交人）Account ID
     * <li>取自版本审计快照：最近一次提交之后未被驳回、也未回到草稿编辑时有值；DRAFT 与未经提交的版本为空。</li>
     */
    private String makerAccountId;

    /**
     * 本轮审批的 Checker（批准人）Account ID
     * <li>批准后有值；PENDING_APPROVAL 及更早为空。</li>
     */
    private String checkerAccountId;

    /** 本轮提交时刻，UTC ISO 8601；与 makerAccountId 同时有值 */
    private String submittedTime;

    /** 版本内价格条数 */
    private Integer priceCount;

    /** 方案 BCR 根当前版本，批准 / 驳回 / 排期等写入的 expectedChangeVersion */
    private String changeVersion;

    /** 版本行最近更新时间，UTC ISO 8601 */
    private String updateTime;
}
