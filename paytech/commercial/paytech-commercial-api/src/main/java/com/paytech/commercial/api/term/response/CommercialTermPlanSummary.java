package com.paytech.commercial.api.term.response;

import lombok.Getter;
import lombok.Setter;

/** 方案列表中的一行：方案字段、方案 BCR 根版本与当前 ACTIVE / 已排期 SCHEDULED 版本。 */
@Getter
@Setter
public class CommercialTermPlanSummary {
    /** 方案 ID */
    private String planId;

    /** 方案编号 */
    private String planCode;

    /** 条款类型（开放目录代码） */
    private String termType;

    /**
     * 合同 ID
     * <li>Partner 级方案为空。</li>
     */
    private String agreementId;

    /** OE 编号，可空 */
    private String entityNo;

    /** Client ID，Client 条款非空 */
    private String clientId;

    /** Local Partner ID，Partner 条款非空 */
    private String localPartnerId;

    /**
     * 方案状态
     * <li>DRAFT、ACTIVE、RETIRED。</li>
     */
    private String status;

    /** 方案 BCR 根当前版本，版本写入的 expectedChangeVersion */
    private String changeVersion;

    /** 当前 ACTIVE 版本 ID，没有为空 */
    private String activeVersionId;

    /** 当前 ACTIVE 版本号，没有为空 */
    private String activeVersionNo;

    /** 当前 ACTIVE 版本生效起点，UTC ISO 8601，没有为空 */
    private String activeEffectiveFrom;

    /** 已排期 SCHEDULED 版本 ID，没有为空 */
    private String scheduledVersionId;

    /** 已排期 SCHEDULED 版本号，没有为空 */
    private String scheduledVersionNo;

    /** 已排期 SCHEDULED 版本生效起点，UTC ISO 8601，没有为空 */
    private String scheduledEffectiveFrom;

    /** 方案创建时间，UTC ISO 8601 */
    private String createTime;

    /** 方案行最近更新时间，UTC ISO 8601 */
    private String updateTime;
}
