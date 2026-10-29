package com.paytech.platform.support.workflow.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.workflow.response.WorkflowDataQueryCheckResponseBody;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;
import com.paytech.platform.support.workflow.core.enums.DataQueryCheckTrigger;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.domain.WorkflowInstanceContext;
import com.paytech.platform.support.workflow.service.processor.request.CheckDataQueryRequest;
import com.paytech.platform.support.workflow.service.processor.response.DataQueryCheckView;

import lombok.RequiredArgsConstructor;

/**
 * 立即检查（REQ-05 R3，technique 8.2）：任务查看者手动触发一次 DATA_QUERY 检查，无锁（检查锁与实例锁由
 * {@link CheckWorkflowDataQueryProcessor} 负责）
 * <li>先按“实例参与人或 WORKFLOW_INSTANCE_READ”判定可见（不可见与不存在一样 404），动作不是 DATA_QUERY 返回 400。</li>
 * <li>已有检查在进行（检查锁被占用）时不执行查询，返回当前已记录的状态并标注 inProgress；落库时实例正被其他操作处理
 * 返回 409（查询结果丢弃，下一轮重查）。动作已完成、已取消或实例已结束时不执行查询，返回现状。</li>
 * <li>响应在检查之后重新读取实例，反映系统完成与推进后的状态。</li>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CheckWorkflowDataQueryNowProcessor
    extends AbstractProcessor<CheckDataQueryRequest, WorkflowDataQueryCheckResponseBody> {

    private final WorkflowProcessorSupport support;
    private final WorkflowQueryProcessor queries;
    private final WorkflowInstanceCommands commands;
    private final CheckWorkflowDataQueryProcessor checker;
    private final WorkflowResponseAssembler assembler = new WorkflowResponseAssembler();

    @Override
    protected WorkflowDataQueryCheckResponseBody processRequest(CheckDataQueryRequest request) {
        // 可见性与动作类型
        long instanceId = commands.instanceOfActionRun(request.getActionRunId());
        WorkflowActionRun row = CheckWorkflowDataQueryProcessor.actionRun(
            queries.visible(request.getActor(), request.getSession(), instanceId), request.getActionRunId());
        if (row.getActionType() != WorkflowActionType.DATA_QUERY) {
            throw new BizCheckFailException("该动作不是系统检查：" + row.getActionKey());
        }

        // 检查（检查锁被占用时为 null）
        DataQueryCheckView view = checkUnlessInProgress(request);
        if (view != null && view.isBusy()) {
            throw new ConcurrentRequestException("流程实例正在被其他操作处理，请稍后重试");
        }

        // 响应
        WorkflowInstanceContext after = queries.visible(request.getActor(), request.getSession(), instanceId);
        return assembler.dataQueryCheckResponse(after,
            CheckWorkflowDataQueryProcessor.actionRun(after, request.getActionRunId()), view == null);
    }

    /**
     * 执行检查；检查锁被占用（已有检查在进行）时返回 null。
     *
     * @param request MANUAL 请求
     * @return 检查结果；已有检查在进行时为 null
     */
    DataQueryCheckView checkUnlessInProgress(CheckDataQueryRequest request) {
        try {
            return checker.process(new CheckDataQueryRequest(request.getActionRunId(), DataQueryCheckTrigger.MANUAL,
                request.getActor(), request.getSession()));
        } catch (ConcurrentRequestException inProgress) {
            return null;
        }
    }
}
