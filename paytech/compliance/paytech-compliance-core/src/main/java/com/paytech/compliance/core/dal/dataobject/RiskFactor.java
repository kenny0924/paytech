package com.paytech.compliance.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Data;

/** 主案件客户风险评级的一项依据因子（t_dd_risk_factor）。 */
@Data
public class RiskFactor {
    /** 因子 ID。 */
    private long factorId;
    /** 主案件 ID。 */
    private long caseId;
    /** 因子代码。 */
    private String factorCode;
    /** 因子取值。 */
    private String factorValue;
    /** 自动评分得分；人工为空。 */
    private BigDecimal score;
    /** 来源：AUTO 或 REVIEWER。 */
    private String source;
    /** 记录人。 */
    private Long recordedBy;
    /** 记录时间。 */
    private Instant recordedTime;
    /** 创建 UTC 时间。 */
    private Instant createTime;
}
