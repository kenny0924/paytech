package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.risk.control.domain.list.RiskSubjectType;
import java.time.Instant;

import lombok.Data;

/** 名单命中 Feature 的不可变参数（t_list_feature_definition）。 */
@Data
public class ListFeatureDefinition {
    /** 与所属 Feature Version 共享的 ID。 */
    private long featureVersionId;

    /** 名单 ID，属于 risk-control。 */
    private long listId;

    /** 判断的主体类型。 */
    private RiskSubjectType subjectType;

    /** 创建 UTC 时间。 */
    private Instant createTime;
}
