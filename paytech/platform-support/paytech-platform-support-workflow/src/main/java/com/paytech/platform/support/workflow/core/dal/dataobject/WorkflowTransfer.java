package com.paytech.platform.support.workflow.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.workflow.core.enums.TransferTargetType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** t_workflow_transfer 映射对象：转交记录；只在 workflow 模块内使用。 */
@Getter
@Setter
@NoArgsConstructor
public class WorkflowTransfer {

    /** 转交记录的 Snowflake 主键。 */
    private Long transferId;

    /** 转交所属的流程实例 ID。 */
    private Long instanceId;

    /** 被转交的是动作运行还是审批行。允许值：ACTION_RUN、APPROVAL。 */
    private TransferTargetType targetType;

    /** 被转交的动作运行 ID 或审批 ID，由 target_type 区分。 */
    private Long targetId;

    /** 转交前的执行人或审批人 Operations 账号 ID。 */
    private Long fromUserId;

    /** 转交后的执行人或审批人 Operations 账号 ID。 */
    private Long toUserId;

    /** 转交时填写的说明；未填写时为空。 */
    private String reason;

    /** 执行转交的 Operations 账号 ID：原处理人本人或有实例管理权限的人。 */
    private Long transferredBy;

    /** 该记录在 Platform Support 数据库中的 UTC 微秒创建时间。 */
    private Instant createTime;
}
