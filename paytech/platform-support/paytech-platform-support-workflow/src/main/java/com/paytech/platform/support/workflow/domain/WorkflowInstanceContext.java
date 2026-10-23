package com.paytech.platform.support.workflow.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowApproval;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowCallback;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinition;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinitionVersion;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowInstance;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowNodeRun;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowTransfer;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowVerification;
import com.paytech.platform.support.workflow.core.enums.ActionRunStatus;
import com.paytech.platform.support.workflow.domain.action.ActionTemplateReference;
import com.paytech.platform.support.workflow.domain.form.FormDefinition;
import com.paytech.platform.support.workflow.domain.graph.NodeAction;
import com.paytech.platform.support.workflow.domain.graph.WorkflowGraph;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;
import com.paytech.platform.support.workflow.domain.runtime.WorkflowAssignment;

import lombok.Getter;

/**
 * 一个流程实例的完整运行上下文：绑定版本的流程图、启动表单、模板快照、人员分配与全部运行记录，以及本次操作的变化。
 *
 * <p>由查询服务一次加载（或发起时构造），运行引擎在其上做纯内存推进并累积 {@link WorkflowRuntimeChange}，
 * 再由运行服务在同一本地事务保存。业务方法只读已加载事实，不访问数据库。</p>
 */
@Getter
public class WorkflowInstanceContext {

    /** 所属流程定义。 */
    private final WorkflowDefinition definition;

    /** 绑定的已发布版本。 */
    private final WorkflowDefinitionVersion version;

    /** 实例。 */
    private final WorkflowInstance instance;

    /** 解析后的流程图。 */
    private final WorkflowGraph graph;

    /** 解析后的启动表单。 */
    private final FormDefinition startForm;

    /** 发布时冻结的动作模板，按模板 ID。 */
    private final Map<Long, ActionTemplateReference> templates;

    /** 发起时冻结的人员分配。 */
    private final WorkflowAssignment assignment;

    /** 全部节点运行。 */
    private final List<WorkflowNodeRun> nodeRuns;

    /** 全部动作运行。 */
    private final List<WorkflowActionRun> actionRuns;

    /** 全部审批行。 */
    private final List<WorkflowApproval> approvals;

    /** 全部验证记录。 */
    private final List<WorkflowVerification> verifications;

    /** 全部转交记录。 */
    private final List<WorkflowTransfer> transfers;

    /** 全部通知与回调。 */
    private final List<WorkflowCallback> callbacks;

    /** 本次操作的 UTC 微秒时间。 */
    private final Instant now;

    /** 本次操作的变化。 */
    private final WorkflowRuntimeChange change;

    /**
     * 构造运行上下文；列表须为可变列表，引擎会追加本次新建的记录。
     *
     * @param snapshot 定义、版本、实例与解析结果
     * @param runs 已有运行记录
     * @param now 本次操作时间
     * @param newInstance 是否为发起
     */
    public WorkflowInstanceContext(Snapshot snapshot, Runs runs, Instant now, boolean newInstance) {
        this.definition = snapshot.definition();
        this.version = snapshot.version();
        this.instance = snapshot.instance();
        this.graph = snapshot.graph();
        this.startForm = snapshot.startForm();
        this.templates = snapshot.templates();
        this.assignment = snapshot.assignment();
        this.nodeRuns = runs.nodeRuns();
        this.actionRuns = runs.actionRuns();
        this.approvals = runs.approvals();
        this.verifications = runs.verifications();
        this.transfers = runs.transfers();
        this.callbacks = runs.callbacks();
        this.now = now;
        this.change = new WorkflowRuntimeChange(snapshot.instance(), newInstance);
    }

    /**
     * 按 ID 取节点运行。
     *
     * @param nodeRunId 节点运行 ID
     * @return 节点运行；不属于本实例时为空
     */
    public Optional<WorkflowNodeRun> nodeRun(Long nodeRunId) {
        return nodeRuns.stream().filter(run -> run.getNodeRunId().equals(nodeRunId)).findFirst();
    }

    /**
     * 节点运行的动作运行。
     *
     * @param nodeRunId 节点运行 ID
     * @return 动作运行
     */
    public List<WorkflowActionRun> actionRunsOf(Long nodeRunId) {
        return actionRuns.stream().filter(run -> run.getNodeRunId().equals(nodeRunId)).toList();
    }

    /**
     * 节点运行的审批行。
     *
     * @param nodeRunId 节点运行 ID
     * @return 审批行
     */
    public List<WorkflowApproval> approvalsOf(Long nodeRunId) {
        return approvals.stream().filter(row -> row.getNodeRunId().equals(nodeRunId)).toList();
    }

    /**
     * 流程图中的节点；已发布版本必然存在。
     *
     * @param nodeKey 节点 key
     * @return 节点
     */
    public WorkflowNode node(String nodeKey) {
        return graph.node(nodeKey).orElseThrow(() -> new ErrorException("版本中不存在节点：" + nodeKey));
    }

    /**
     * 节点上的动作定义。
     *
     * @param nodeKey 节点 key
     * @param actionKey 动作 key
     * @return 动作定义
     */
    public NodeAction action(String nodeKey, String actionKey) {
        return node(nodeKey).actions().stream().filter(action -> action.key().equals(actionKey)).findFirst()
            .orElseThrow(() -> new ErrorException("节点中不存在动作：" + actionKey));
    }

    /**
     * 动作最新一轮的运行。
     *
     * @param actionKey 动作 key
     * @return 最新一轮；从未运行时为空
     */
    public Optional<WorkflowActionRun> latestActionRun(String actionKey) {
        return actionRuns.stream().filter(run -> run.getActionKey().equals(actionKey))
            .max(Comparator.comparing(run -> nodeRun(run.getNodeRunId()).map(WorkflowNodeRun::getRoundNo).orElse(0)));
    }

    /**
     * 新一轮动作的执行人：沿用上一轮的实际执行人（保留转交效果），首次取发起时的分配。
     *
     * @param actionKey 动作 key
     * @return 执行人
     */
    public Long executorFor(String actionKey) {
        return latestActionRun(actionKey).map(WorkflowActionRun::getAssigneeId)
            .orElseGet(() -> assignment.executorOf(actionKey));
    }

    /**
     * 新一轮审批的审批人：沿用该节点最近一轮审批行的审批人（保留转交效果），首次取发起时的分配。
     *
     * @param nodeKey 节点 key
     * @return 审批人
     */
    public List<Long> approversFor(String nodeKey) {
        return nodeRuns.stream().filter(run -> run.getNodeKey().equals(nodeKey))
            .sorted(Comparator.comparing(WorkflowNodeRun::getRoundNo).reversed())
            .map(run -> approvalsOf(run.getNodeRunId())).filter(rows -> !rows.isEmpty()).findFirst()
            .map(rows -> rows.stream().map(WorkflowApproval::getApproverId).toList())
            .orElseGet(() -> assignment.approversOf(nodeKey));
    }

    /**
     * 是否已有可用作变量的输出：该动作最新一轮为 COMPLETED。
     *
     * @param actionKey 动作 key
     * @return 最新一轮 COMPLETED 的运行
     */
    public Optional<WorkflowActionRun> completedOutput(String actionKey) {
        return latestActionRun(actionKey).filter(run -> run.getStatus() == ActionRunStatus.COMPLETED);
    }

    /**
     * 账号是否参与本实例：发起、执行、提交、审批或转交。
     *
     * @param accountId 账号
     * @return 参与返回 true
     */
    public boolean isParticipant(long accountId) {
        Long id = accountId;
        return id.equals(instance.getStartedBy())
            || actionRuns.stream().anyMatch(run -> id.equals(run.getAssigneeId()) || id.equals(run.getSubmittedBy()))
            || approvals.stream().anyMatch(row -> id.equals(row.getApproverId()))
            || transfers.stream().anyMatch(row -> id.equals(row.getFromUserId()) || id.equals(row.getToUserId()));
    }

    /**
     * 节点运行中提交过动作的账号，用于“同一人不能既执行又审批”。
     *
     * @param nodeRunId 节点运行 ID
     * @return 提交人
     */
    public List<Long> submittersOf(Long nodeRunId) {
        return actionRunsOf(nodeRunId).stream().map(WorkflowActionRun::getSubmittedBy).filter(Objects::nonNull).toList();
    }

    /**
     * 定义、版本、实例与解析后的配置。
     *
     * @param definition 定义
     * @param version 版本
     * @param instance 实例
     * @param graph 流程图
     * @param startForm 启动表单
     * @param templates 模板快照
     * @param assignment 人员分配
     */
    public record Snapshot(WorkflowDefinition definition, WorkflowDefinitionVersion version, WorkflowInstance instance,
        WorkflowGraph graph, FormDefinition startForm, Map<Long, ActionTemplateReference> templates,
        WorkflowAssignment assignment) {
    }

    /**
     * 已有运行记录，均为可变列表。
     *
     * @param nodeRuns 节点运行
     * @param actionRuns 动作运行
     * @param approvals 审批行
     * @param verifications 验证记录
     * @param transfers 转交记录
     * @param callbacks 通知与回调
     */
    public record Runs(List<WorkflowNodeRun> nodeRuns, List<WorkflowActionRun> actionRuns,
        List<WorkflowApproval> approvals, List<WorkflowVerification> verifications, List<WorkflowTransfer> transfers,
        List<WorkflowCallback> callbacks) {
    }
}
