package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;
import java.time.Instant;

import lombok.Data;

/** 风险事件与受控主体的关联（t_velocity_event_subject）。 */
@Data
public class VelocityEventSubject {
    /** 关联 ID。 */
    private long eventSubjectId;

    /** 风险事件 ID。 */
    private long velocityEventId;

    /** 主体类型。 */
    private RiskSubjectType subjectType;

    /** 规范化主体键；受限值不进入日志。 */
    private String subjectKey;

    /** 冻结的事件编码，供窗口查询。 */
    private VelocityEventCode eventCode;

    /** 冻结的事件时间，供窗口查询。 */
    private Instant occurredTime;

    /** 创建 UTC 时间。 */
    private Instant createTime;
}
