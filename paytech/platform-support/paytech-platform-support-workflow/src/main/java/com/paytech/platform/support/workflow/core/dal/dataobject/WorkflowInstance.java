package com.paytech.platform.support.workflow.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.workflow.core.enums.WorkflowInstanceStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** t_workflow_instance 映射对象：流程实例；只在 workflow 模块内使用。 */
@Getter
@Setter
@NoArgsConstructor
public class WorkflowInstance {

    /** 流程实例的 Snowflake 主键。 */
    private Long instanceId;

    /** 实例所属的流程定义 ID。 */
    private Long definitionId;

    /** 发起时绑定的已发布流程版本 ID；实例全程按该版本执行。 */
    private Long definitionVersionId;

    /** 发起人填写的实例标题。 */
    private String title;

    /** 可选的业务关联键（如 CHANNEL:GEIDEA-SA），只用于检索和展示，工作流不解释其含义；未填写时为空。 */
    private String businessKey;

    /** 实例运行状态；COMPLETED、TERMINATED 为终态，终态后只读。允许值：RUNNING、COMPLETED、TERMINATED。 */
    private WorkflowInstanceStatus status;

    /** 业务关联键防重槽位：实例 RUNNING 且 business_key 非空时为 1，完成或终止时置空；与 definition_id、business_key 组成唯一索引。 */
    private Integer activeSlot;

    /** 启动表单提交值，作为 start.* 实例变量；发起后不可修改。 */
    private String startValuesJson;

    /** 发起时冻结的各节点审批人与各动作执行人（版本默认值加发起时的修改）。 */
    private String assignmentJson;

    /** 发起实例的 Operations 账号 ID。 */
    private Long startedBy;

    /** 实例发起的 UTC 微秒时间。 */
    private Instant startTime;

    /** 实例完成或终止的 UTC 微秒时间；运行中为空。 */
    private Instant endTime;

    /** 终止实例的 Operations 账号 ID；未终止时为空。 */
    private Long terminatedBy;

    /** 终止实例时填写的原因；未终止时为空。 */
    private String terminationReason;

    /** 并发更新版本，初始为 1，每次条件更新成功加一。 */
    private Integer version;

    /** 该记录在 Platform Support 数据库中的 UTC 微秒创建时间。 */
    private Instant createTime;

    /** 该可变记录最后一次持久化更新的 UTC 微秒时间。 */
    private Instant updateTime;
}
