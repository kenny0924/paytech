package com.paytech.platform.support.workflow.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowApproval;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowCallback;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowInstance;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowNodeRun;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowTransfer;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowVerification;
import com.paytech.platform.support.workflow.core.enums.NodeRunStatus;

import lombok.Getter;

/**
 * 一次运行操作要在同一本地事务保存的全部变化：实例版本推进、节点运行状态变化与新建、动作运行与审批行的新建与更新、
 * 验证与转交记录、通知回调及其补偿任务。
 *
 * <p>已持久化对象的更新以其当前 version（或节点运行的旧状态）为条件；新建对象插入前已准备好 ID 与初始值。</p>
 */
@Getter
public class WorkflowRuntimeChange {

    /** 本次是否新建实例（发起）。 */
    private final boolean newInstance;

    /** 实例；新建时插入，否则按原版本推进版本与状态。 */
    private final WorkflowInstance instance;

    /** 已有节点运行的状态变化，按发生顺序。 */
    private final List<NodeRunUpdate> nodeRunUpdates = new ArrayList<>();

    /** 新建的节点运行。 */
    private final List<WorkflowNodeRun> newNodeRuns = new ArrayList<>();

    /** 新建的动作运行。 */
    private final List<WorkflowActionRun> newActionRuns = new ArrayList<>();

    /** 新建的审批行。 */
    private final List<WorkflowApproval> newApprovals = new ArrayList<>();

    /** 追加的验证记录。 */
    private final List<WorkflowVerification> newVerifications = new ArrayList<>();

    /** 追加的转交记录。 */
    private final List<WorkflowTransfer> newTransfers = new ArrayList<>();

    /** 新登记的通知与回调。 */
    private final List<WorkflowCallback> newCallbacks = new ArrayList<>();

    /** 与通知回调同事务登记的补偿任务，由编排方按 Handler 事件工厂构造。 */
    private final List<CompensationEventRequest> compensationEvents = new ArrayList<>();

    private final Set<WorkflowActionRun> updatedActionRuns = Collections.newSetFromMap(new IdentityHashMap<>());

    private final Set<WorkflowApproval> updatedApprovals = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * 以实例构造。
     *
     * @param instance 实例
     * @param newInstance 是否新建
     */
    public WorkflowRuntimeChange(WorkflowInstance instance, boolean newInstance) {
        this.instance = instance;
        this.newInstance = newInstance;
    }

    /**
     * 记录已有动作运行的修改；本次新建的不重复记录。
     *
     * @param row 动作运行
     */
    public void updated(WorkflowActionRun row) {
        if (newActionRuns.stream().noneMatch(created -> created == row)) {
            updatedActionRuns.add(row);
        }
    }

    /**
     * 记录已有审批行的修改；本次新建的不重复记录。
     *
     * @param row 审批行
     */
    public void updated(WorkflowApproval row) {
        if (newApprovals.stream().noneMatch(created -> created == row)) {
            updatedApprovals.add(row);
        }
    }

    /**
     * 已有节点运行的一次状态变化。
     *
     * @param row 节点运行（已改为新状态）
     * @param from 期望旧状态
     * @param to 新状态
     */
    public record NodeRunUpdate(WorkflowNodeRun row, NodeRunStatus from, NodeRunStatus to) {
    }
}
