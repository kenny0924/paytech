package com.paytech.platform.support.workflow.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;
import com.paytech.platform.support.workflow.core.enums.DataQueryCheckResult;
import com.paytech.platform.support.workflow.core.enums.DataQueryCheckTrigger;
import com.paytech.platform.support.workflow.core.enums.WorkflowInstanceStatus;
import com.paytech.platform.support.workflow.domain.WorkflowInstanceContext;
import com.paytech.platform.support.workflow.domain.runtime.DataQueryCheckOutcome;
import com.paytech.platform.support.workflow.domainservice.process.WorkflowRuntimeEngine;
import com.paytech.platform.support.workflow.domainservice.service.WorkflowInstanceQueryService;
import com.paytech.platform.support.workflow.domainservice.service.WorkflowRuntimeService;
import com.paytech.platform.support.workflow.service.processor.request.ApplyDataQueryCheckRequest;
import com.paytech.platform.support.workflow.service.processor.response.DataQueryCheckView;

import lombok.RequiredArgsConstructor;

/**
 * 落库一次已执行的 DATA_QUERY 检查（technique 第 7 节步骤 5），只由 {@link CheckWorkflowDataQueryProcessor} 在查询之后调用
 * <li>锁 workflow-instance:{id}（TRY_LOCK，与其他实例写入口同一把锁）；本地短事务内重新加载上下文，动作版本已变、
 * 已不待处理、节点已结束或实例已结束时丢弃结果（期间被终止、退回或转交）。</li>
 * <li>已满足：系统完成动作并按现有规则推进，新激活的 DATA_QUERY 同事务登记首查，实例版本 CAS；
 * 未满足或失败：只按动作版本条件更新检查列。</li>
 * <li>MANUAL 在事务内重检 WORKFLOW_TASK_HANDLE 并记 Operations 审计（WORKFLOW_ACTION_RUN / CHECKED）；首查与定时重查
 * 没有操作人，不写 Operations 审计。</li>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ApplyWorkflowDataQueryCheckProcessor
    extends AbstractLockProcessor<ApplyDataQueryCheckRequest, DataQueryCheckView> {

    /** 审计资源类型。 */
    static final String ACTION_RUN = "WORKFLOW_ACTION_RUN";

    private final WorkflowProcessorSupport support;
    private final WorkflowRuntimeService runtime;
    private final WorkflowInstanceQueryService queries;
    private final WorkflowRuntimeEngine engine = new WorkflowRuntimeEngine();

    /** 按所属实例加锁。 */
    @Override
    protected LockCommand lockCommand(ApplyDataQueryCheckRequest request) {
        return LockCommand.tryLock(WorkflowInstanceCommands.lock(request.getInstanceId()), support.lockWaitMillis(),
            support.lockLeaseMillis());
    }

    @Override
    protected DataQueryCheckView processWithinLock(ApplyDataQueryCheckRequest request) {
        return support.write(() -> {
            SessionContext session = request.getTrigger() == DataQueryCheckTrigger.MANUAL
                ? support.authorize(request.getActor(), request.getSession(), WorkflowPermissions.TASK_HANDLE) : null;
            WorkflowInstanceContext context = queries.load(request.getInstanceId(), support.now());
            if (context == null) {
                throw new ErrorException("动作运行所属实例不存在：" + request.getInstanceId());
            }
            WorkflowActionRun row = context.getActionRuns().stream()
                .filter(run -> run.getActionRunId() == request.getActionRunId()).findFirst()
                .orElseThrow(() -> new ErrorException("实例中不存在动作运行：" + request.getActionRunId()));
            DataQueryCheckOutcome outcome = request.getOutcome();
            if (!unchanged(context, row, request.getExpectedVersion())) {
                return new DataQueryCheckView(row.getActionRunId(), true, false, false, outcome.result(),
                    outcome.failure());
            }
            save(context, row, outcome);
            if (session != null) {
                support.succeeded(session, ACTION_RUN, row.getActionRunId(), "CHECKED");
            }
            return new DataQueryCheckView(row.getActionRunId(), true, true, false, outcome.result(),
                outcome.failure());
        });
    }

    /**
     * 查询之后动作是否未被改动：版本相同、仍待处理、节点未结束、实例运行中。
     *
     * @param context 本次加载的上下文
     * @param row 动作运行
     * @param expectedVersion 查询前的动作版本
     * @return 未改动返回 true
     */
    boolean unchanged(WorkflowInstanceContext context, WorkflowActionRun row, int expectedVersion) {
        return context.getInstance().getStatus() == WorkflowInstanceStatus.RUNNING
            && row.getVersion() == expectedVersion
            && engine.dataQueryCheckable(row, context.nodeRun(row.getNodeRunId()).orElseThrow(
                () -> new ErrorException("实例中不存在节点运行：" + row.getNodeRunId())));
    }

    /**
     * 保存检查结果：满足时完成并推进，否则只更新检查列。
     *
     * @param context 实例上下文
     * @param row 动作运行
     * @param outcome 检查结果
     */
    void save(WorkflowInstanceContext context, WorkflowActionRun row, DataQueryCheckOutcome outcome) {
        if (outcome.result() == DataQueryCheckResult.SATISFIED) {
            engine.completeDataQuery(context, row.getActionRunId(), outcome.outputs(), support::nextId);
            support.registerCallbackEvents(context.getChange());
            runtime.apply(context.getChange());
            return;
        }
        engine.recordDataQueryCheck(row, outcome, context.getNow());
        runtime.recordDataQueryCheck(row);
    }
}
