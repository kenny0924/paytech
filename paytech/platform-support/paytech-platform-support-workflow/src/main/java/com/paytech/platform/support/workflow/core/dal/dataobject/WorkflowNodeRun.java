package com.paytech.platform.support.workflow.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.workflow.core.enums.NodeRunStatus;
import com.paytech.platform.support.workflow.core.enums.WorkflowNodeType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** t_workflow_node_run 映射对象：节点运行；只在 workflow 模块内使用。 */
@Getter
@Setter
@NoArgsConstructor
public class WorkflowNodeRun {

    /** 节点运行的 Snowflake 主键。 */
    private Long nodeRunId;

    /** 节点运行所属的流程实例 ID。 */
    private Long instanceId;

    /** 绑定流程版本中的节点 key。 */
    private String nodeKey;

    /** 节点在流程图中的类型。允许值：START、END、TASK、EXCLUSIVE、PARALLEL_SPLIT、PARALLEL_JOIN。 */
    private WorkflowNodeType nodeType;

    /** 同一实例同一节点的轮次，从 1 开始；新一轮为该节点已有最大轮次加一。 */
    private Integer roundNo;

    /** 所在并行分支，取并行分叉出边的 key；不在并行分支内时为空。 */
    private String branchKey;

    /** 实际来源的节点运行 ID；START 与并行汇合节点为空。 */
    private Long previousNodeRunId;

    /** TASK 依次为 ACTIVE（执行动作）、APPROVING（待审批），结束于 PASSED、RETURNED（退回修改）、REJECTED（被驳回）或 CANCELLED；并行汇合为 WAITING 直到全部分支完成；START、END、排他网关与并行分叉写入即 PASSED。允许值：ACTIVE、APPROVING、WAITING、PASSED、RETURNED、REJECTED、CANCELLED。 */
    private NodeRunStatus status;

    /** 排他网关按条件选中的出边 key；其他节点为空。 */
    private String selectedEdgeKey;

    /** 节点运行创建即激活的 UTC 微秒时间。 */
    private Instant activatedTime;

    /** 节点运行进入 PASSED、RETURNED、REJECTED 或 CANCELLED 的 UTC 微秒时间；进行中为空。 */
    private Instant completedTime;

    /** 并发更新版本，初始为 1，每次条件更新成功加一。 */
    private Integer version;

    /** 该记录在 Platform Support 数据库中的 UTC 微秒创建时间。 */
    private Instant createTime;

    /** 该可变记录最后一次持久化更新的 UTC 微秒时间。 */
    private Instant updateTime;
}
