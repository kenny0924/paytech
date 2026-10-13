package com.paytech.risk.compliance.core.dal.dataobject;

import java.time.Instant;

import lombok.Data;

/** 案件状态变更（t_dd_case_state_change）。 */
@Data
public class CaseStateChange {
    /** 变更 ID。 */
    private long changeId;

    /** 案件 ID。 */
    private long caseId;

    /** 原状态；创建时为空。 */
    private String fromStatus;

    /** 新状态。 */
    private String toStatus;

    /** 迁移动作。 */
    private String actionType;

    /** 迁移原因代码；可空。 */
    private String reasonCode;

    /** 触发类型。 */
    private String triggerType;

    /** 触发标识：来源提交编号。 */
    private String triggerId;

    /** 操作人 Operations 账号 ID；系统触发为空。 */
    private Long actorId;

    /** 聚合版本。 */
    private long aggregateVersion;

    /** 发生 UTC 时间。 */
    private Instant occurredTime;

    /** 创建 UTC 时间。 */
    private Instant createTime;
}
