package com.paytech.platform.support.workflow.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.workflow.core.enums.ActionRunStatus;
import com.paytech.platform.support.workflow.core.enums.DataQueryCheckResult;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** t_workflow_action_run 映射对象：动作运行；只在 workflow 模块内使用。 */
@Getter
@Setter
@NoArgsConstructor
public class WorkflowActionRun {

    /** 动作运行的 Snowflake 主键。 */
    private Long actionRunId;

    /** 动作运行所属的流程实例 ID。 */
    private Long instanceId;

    /** 动作运行所属的节点运行 ID，每轮节点运行各有一份。 */
    private Long nodeRunId;

    /** 绑定流程版本中的动作 key，同时是输出变量前缀。 */
    private String actionKey;

    /** 引用的动作模板 ID；执行配置以流程版本的模板快照为准。 */
    private Long actionTemplateId;

    /** 动作的提交与验证方式，取自版本快照。允许值：FORM、FILE_UPLOAD、PAGE_TASK、MANUAL_CONFIRM、DATA_QUERY。 */
    private WorkflowActionType actionType;

    /** 1 表示必选，节点进入审批或放行前必须 COMPLETED；0 表示可选，不阻塞放行。 */
    private Boolean required;

    /** 当前指派的执行人 Operations 账号 ID；转交后更新。 */
    private Long assigneeId;

    /** PENDING 待处理；COMPLETED 已完成；CANCELLED 节点结束或实例终止时仍未完成。允许值：PENDING、COMPLETED、CANCELLED。 */
    private ActionRunStatus status;

    /** 执行人最近一次提交的输出，按动作类型与配置校验；未提交时为空。 */
    private String outputJson;

    /** 最近一次提交的 Operations 账号 ID；未提交时为空。 */
    private Long submittedBy;

    /** 最近一次提交的 UTC 微秒时间；未提交时为空。 */
    private Instant submittedTime;

    /** DATA_QUERY 最近一次检查结果落库的 UTC 微秒时间；其他类型与尚未检查时为空。 */
    private Instant lastCheckTime;

    /** DATA_QUERY 最近一次检查结果；从未检查为空。允许值：SATISFIED、UNSATISFIED、FAILED。 */
    private DataQueryCheckResult checkResult;

    /** UNSATISFIED 时为查询返回的原因码（逗号分隔，可空）；FAILED 时为失败码；SATISFIED 时为空。 */
    private String checkDetail;

    /** DATA_QUERY 连续检查失败次数；查询成功执行（满足或未满足）时清零。 */
    private Integer checkFailureCount;

    /** DATA_QUERY 最近一次查询成功执行的 UTC 微秒时间（满足或未满足）；从未成功为空。 */
    private Instant lastCheckSuccessTime;

    /** 并发更新版本，初始为 1，每次条件更新成功加一。 */
    private Integer version;

    /** 该记录在 Platform Support 数据库中的 UTC 微秒创建时间。 */
    private Instant createTime;

    /** 该可变记录最后一次持久化更新的 UTC 微秒时间。 */
    private Instant updateTime;
}
