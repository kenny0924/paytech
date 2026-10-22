package com.paytech.platform.support.workflow.service.processor;

import java.util.function.BiConsumer;
import java.util.function.Function;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowApproval;
import com.paytech.platform.support.workflow.domain.WorkflowInstanceContext;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowActionRunRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowApprovalRepository;
import com.paytech.platform.support.workflow.domainservice.service.WorkflowInstanceQueryService;
import com.paytech.platform.support.workflow.domainservice.service.WorkflowRuntimeService;

import lombok.AllArgsConstructor;

/**
 * 实例写入口共用的阶段：在本库写事务内重检 WORKFLOW_TASK_HANDLE、加载实例上下文、执行引擎操作、为新通知回调构造补偿任务、
 * 一次保存全部变化，再从已保存的上下文构造响应。调用方须已持有 workflow-instance:{id} 资源锁。
 *
 * <p>事务内只读写本库；邮件与事件投递由补偿任务在提交后执行。</p>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class WorkflowInstanceCommands {

    private final WorkflowProcessorSupport support;
    private final WorkflowRuntimeService runtime;
    private final WorkflowApprovalRepository approvals;
    private final WorkflowActionRunRepository actionRuns;
    private final WorkflowInstanceQueryService queries;

    /**
     * 实例资源锁 Key：提交、重新验证、审批、转交、终止与回调重试共用。
     *
     * @param instanceId 实例 ID
     * @return 锁 Key
     */
    static String lock(long instanceId) {
        return "workflow-instance:" + instanceId;
    }

    /**
     * 在写事务中执行一次实例操作。
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param instanceId 实例 ID
     * @param operation 引擎操作
     * @param audit 成功审计的资源
     * @param response 保存后的响应构造
     * @param <T> 响应类型
     * @return 响应
     */
    <T> T run(String actor, String session, long instanceId, BiConsumer<SessionContext, WorkflowInstanceContext> operation,
        AuditResource audit, Function<WorkflowInstanceContext, T> response) {
        return support.write(() -> {
            SessionContext current = support.authorize(actor, session, WorkflowPermissions.TASK_HANDLE);
            WorkflowInstanceContext context = queries.load(instanceId, support.now());
            if (context == null) {
                throw support.notFound();
            }
            operation.accept(current, context);
            support.registerCallbackEvents(context.getChange());
            runtime.apply(context.getChange());
            support.succeeded(current, audit.type(), audit.id(), audit.reason());
            return response.apply(context);
        });
    }

    /**
     * 动作运行所属实例；动作运行与实例的关系不可变，可在加锁前读取。
     *
     * @param actionRunId 动作运行 ID
     * @return 实例 ID
     */
    long instanceOfActionRun(long actionRunId) {
        WorkflowActionRun row = actionRuns.find(actionRunId);
        if (row == null) {
            throw support.notFound();
        }
        return row.getInstanceId();
    }

    /**
     * 审批行所属实例；关系不可变，可在加锁前读取。
     *
     * @param approvalId 审批 ID
     * @return 实例 ID
     */
    long instanceOfApproval(long approvalId) {
        WorkflowApproval row = approvals.find(approvalId);
        if (row == null) {
            throw support.notFound();
        }
        return row.getInstanceId();
    }

    /**
     * 成功审计的资源：须与入口 {@code @Audit} 声明的资源类型一致。
     *
     * @param type 资源类型
     * @param id 资源 ID
     * @param reason 有界结果编码
     */
    record AuditResource(String type, long id, String reason) {
    }
}
