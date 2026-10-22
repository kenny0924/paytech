package com.paytech.platform.support.workflow.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.workflow.core.enums.ApprovalStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** t_workflow_approval 映射对象：审批；只在 workflow 模块内使用。 */
@Getter
@Setter
@NoArgsConstructor
public class WorkflowApproval {

    /** 审批行的 Snowflake 主键。 */
    private Long approvalId;

    /** 审批所属的流程实例 ID。 */
    private Long instanceId;

    /** 审批所属的节点运行 ID，每轮节点运行各有一组。 */
    private Long nodeRunId;

    /** 当前审批人 Operations 账号 ID；转交后更新，同一节点运行内不重复。 */
    private Long approverId;

    /** PENDING 待决定；APPROVED 同意；RETURNED 退回修改；REJECTED 驳回；WITHDRAWN 因他人决定或实例终止而撤回。允许值：PENDING、APPROVED、RETURNED、REJECTED、WITHDRAWN。 */
    private ApprovalStatus status;

    /** 审批人填写的意见；退回修改与驳回必填，同意可空。 */
    private String decisionComment;

    /** 汇合后第一个节点驳回时指定的目标分支末 TASK 节点 key；其他情形为空。 */
    private String rejectTargetNodeKey;

    /** 审批决定或撤回的 UTC 微秒时间；待决定时为空。 */
    private Instant decidedTime;

    /** 并发更新版本，初始为 1，每次条件更新成功加一。 */
    private Integer version;

    /** 该记录在 Platform Support 数据库中的 UTC 微秒创建时间。 */
    private Instant createTime;

    /** 该可变记录最后一次持久化更新的 UTC 微秒时间。 */
    private Instant updateTime;
}
