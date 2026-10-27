package com.paytech.compliance.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Data;

/** 一条筛查命中（t_screening_hit）；不保存命中实体姓名。 */
@Data
public class ScreeningHit {
    /** 命中 ID。 */
    private long hitId;
    /** 筛查执行 ID。 */
    private long screeningRunId;
    /** 提供方命中引用。 */
    private String externalHitRef;
    /** 实体类型。 */
    private String entityType;
    /** 匹配方式。 */
    private String matchType;
    /** 匹配分数。 */
    private BigDecimal matchScore;
    /** 风险等级。 */
    private String riskLevel;
    /** OPEN、RESOLVED、SUPERSEDED。 */
    private String status;
    /** 并发版本。 */
    private long version;
    /** 创建 UTC 时间。 */
    private Instant createTime;
    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
