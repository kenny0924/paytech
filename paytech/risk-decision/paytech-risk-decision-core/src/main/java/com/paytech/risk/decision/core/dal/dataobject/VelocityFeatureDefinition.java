package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;
import com.paytech.risk.decision.domain.enums.VelocityMetric;
import java.time.Instant;

import lombok.Data;

/** 实时累计 Feature 的不可变参数（t_velocity_feature_definition）。 */
@Data
public class VelocityFeatureDefinition {
    /** 与所属 Feature Version 共享的 ID。 */
    private long featureVersionId;

    /** 计入的事件。 */
    private VelocityEventCode eventCode;

    /** 锚定主体类型。 */
    private RiskSubjectType anchorSubjectType;

    /** 累计算法。 */
    private VelocityMetric metric;

    /** 被去重的主体类型；仅去重计数非空。 */
    private RiskSubjectType distinctSubjectType;

    /** 窗口秒数。 */
    private int windowSeconds;

    /** 是否计入本次；仅计数与金额合计可为 true。 */
    private boolean includeCurrent;

    /** 创建 UTC 时间。 */
    private Instant createTime;
}
