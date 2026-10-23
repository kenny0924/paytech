package com.paytech.platform.support.api.workflow.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import tools.jackson.databind.JsonNode;

/** 实例详情：绑定版本的流程图与配置、启动信息、全部节点运行、动作运行、验证、审批与转交记录。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowInstanceDetailResponseBody {

    /** 实例摘要。 */
    private WorkflowInstanceSummaryBody instance;

    /** 流程编码。 */
    private String definitionCode;

    /** 流程名称。 */
    private String definitionName;

    /** 绑定的版本号。 */
    private Integer versionNo;

    /** 实例并发版本。 */
    private Integer version;

    /** 终止人；未终止为空。 */
    private String terminatedBy;

    /** 终止原因；未终止为空。 */
    private String terminationReason;

    /** 绑定版本的流程图。 */
    private JsonNode graph;

    /** 绑定版本的启动表单。 */
    private JsonNode startForm;

    /** 启动值。 */
    private JsonNode startValues;

    /** 绑定版本冻结的动作模板快照。 */
    private JsonNode actionSnapshot;

    /** 节点运行，按激活时间。 */
    private List<NodeRunBody> nodeRuns;

    /** 动作运行。 */
    private List<ActionRunBody> actionRuns;

    /** 历史验证记录，按时间；2026-10-22 起不再产生新记录。 */
    private List<VerificationBody> verifications;

    /** 审批行。 */
    private List<ApprovalBody> approvals;

    /** 转交记录。 */
    private List<TransferBody> transfers;

    /** 有审批的任务节点当前生效的审批人：节点 key 到账号 ID（发起时的分配，并保留转交效果）。 */
    private Map<String, List<String>> nodeApprovers;

    /** 出现的人员账号到显示名。 */
    private Map<String, String> people;

    /**
     * 启动值与动作输出引用的附件
     * <li>按引用的文件地址查询的名称、类型、大小与扫描状态；查不到的文件不出现。只有 AVAILABLE 的文件可以下载。</li>
     */
    private List<WorkflowAttachmentBody> files;

    /** 节点运行。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeRunBody {

        /** 节点运行 ID。 */
        private String nodeRunId;

        /** 节点 key。 */
        private String nodeKey;

        /** 节点类型。 */
        private String nodeType;

        /** 轮次。 */
        private Integer roundNo;

        /** 并行分支；可空。 */
        private String branchKey;

        /** 实际来源节点运行；可空。 */
        private String previousNodeRunId;

        /** 状态。 */
        private String status;

        /** 排他网关选中的出边；可空。 */
        private String selectedEdgeKey;

        /** 激活时间。 */
        private Instant activatedTime;

        /** 结束时间；可空。 */
        private Instant completedTime;

        /**
         * 驳回方式
         * <li>仅待审批的任务节点给出：PREVIOUS_TASK、CHOOSE_BRANCH、FIRST_TASK、BRANCH_FIRST_NODE；其余为空。</li>
         */
        private String rejectKind;

        /** 驳回目标候选；CHOOSE_BRANCH 时须选其一作为 rejectTargetNodeKey。 */
        private List<String> rejectTargets;
    }

    /** 动作运行。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionRunBody {

        /** 动作运行 ID。 */
        private String actionRunId;

        /** 节点运行 ID。 */
        private String nodeRunId;

        /** 动作 key。 */
        private String actionKey;

        /** 动作类型。 */
        private String actionType;

        /** 是否必选。 */
        private Boolean required;

        /** 当前执行人。 */
        private String assigneeId;

        /** 状态：PENDING 待处理、COMPLETED 已完成、CANCELLED 已取消。 */
        private String status;

        /** 最近一次提交的输出；未提交为空。 */
        private JsonNode output;

        /** 最近一次提交人；可空。 */
        private String submittedBy;

        /** 最近一次提交时间；可空。 */
        private Instant submittedTime;

        /** 动作模板名称（发布时冻结；早期版本快照没有名称时为空）。 */
        private String templateName;
    }

    /** 验证记录。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerificationBody {

        /** 验证记录 ID。 */
        private String verificationId;

        /** 动作运行 ID。 */
        private String actionRunId;

        /** PASSED 或 FAILED。 */
        private String result;

        /** 未通过原因；可空。 */
        private String failureReason;

        /** 证据；可空。 */
        private JsonNode evidence;

        /** 触发人。 */
        private String verifiedBy;

        /** 验证时间。 */
        private Instant verifiedTime;
    }

    /** 审批行。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalBody {

        /** 审批 ID。 */
        private String approvalId;

        /** 节点运行 ID。 */
        private String nodeRunId;

        /** 审批人。 */
        private String approverId;

        /** 状态。 */
        private String status;

        /** 意见；可空。 */
        private String comment;

        /** 驳回目标；可空。 */
        private String rejectTargetNodeKey;

        /** 决定时间；可空。 */
        private Instant decidedTime;
    }

    /** 转交记录。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferBody {

        /** 转交记录 ID。 */
        private String transferId;

        /** ACTION_RUN 或 APPROVAL。 */
        private String targetType;

        /** 动作运行或审批 ID。 */
        private String targetId;

        /** 原处理人。 */
        private String fromUserId;

        /** 新处理人。 */
        private String toUserId;

        /** 说明；可空。 */
        private String reason;

        /** 操作人。 */
        private String transferredBy;

        /** 转交时间。 */
        private Instant createTime;
    }
}
