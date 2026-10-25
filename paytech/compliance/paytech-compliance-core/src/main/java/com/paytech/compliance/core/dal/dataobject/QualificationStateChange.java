package com.paytech.compliance.core.dal.dataobject;

import java.time.Instant;

import lombok.Data;

/** 角色资格投影的追加状态变更（t_qualification_state_change）；聚合版本等于变更后的资格版本。 */
@Data
public class QualificationStateChange {
    /** 状态变更 ID。 */
    private long changeId;

    /** 角色资格 ID。 */
    private long qualificationId;

    /**
     * 原状态
     * <li>首次建立资格时为空。</li>
     */
    private String fromStatus;

    /** 新状态。 */
    private String toStatus;

    /** 引起本次变更的资格决定 ID。 */
    private long decisionId;

    /** 变更原因代码；可空。 */
    private String reasonCode;

    /** 触发类型：OPERATIONS_REVIEW、OPERATIONS_MANUAL、BACKFILL。 */
    private String triggerType;

    /** 触发标识：资格决定 ID 的十进制文本。 */
    private String triggerId;

    /** 操作人 Operations 账号 ID；回填为空。 */
    private Long actorId;

    /** 变更后的资格聚合版本。 */
    private long aggregateVersion;

    /** 发生 UTC 时间。 */
    private Instant occurredTime;

    /** 创建 UTC 时间。 */
    private Instant createTime;
}
