package com.paytech.platform.support.workflow.service.handler;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.platform.support.workflow.core.enums.DataQueryCheckTrigger;
import com.paytech.platform.support.workflow.service.processor.CheckWorkflowDataQueryProcessor;
import com.paytech.platform.support.workflow.service.processor.request.CheckDataQueryRequest;
import com.paytech.platform.support.workflow.service.processor.response.DataQueryCheckView;

/**
 * DATA_QUERY 动作的首查（REQ-05 R3）：动作运行与该任务在同一写事务登记，提交后在事务外执行一次检查。
 * <li>任何写入口（发起、完成、审批、退回、驳回、系统完成）新建 DATA_QUERY 动作运行时登记，级联激活自然覆盖。</li>
 * <li>检查已执行（含查询失败、未满足）或已有检查在进行（检查锁被占用）即结束任务；落库时实例锁冲突可重试；
 * 其他异常可重试，达到次数上限后交由定时重查兜底。</li>
 */
@Component
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class WorkflowDataQueryCheckHandler implements CompensationEventHandler {

    /** 补偿事件类型。 */
    public static final String EVENT_TYPE = "WORKFLOW_DATA_QUERY_CHECK";

    private final CheckWorkflowDataQueryProcessor checker;

    /**
     * 装配检查入口。
     *
     * @param checker DATA_QUERY 检查 Processor
     */
    public WorkflowDataQueryCheckHandler(CheckWorkflowDataQueryProcessor checker) {
        this.checker = checker;
    }

    /**
     * 纯工厂：与新建的动作运行同事务登记。
     *
     * @param actionRunId 动作运行 ID
     * @return 登记请求
     */
    public static CompensationEventRequest buildEvent(long actionRunId) {
        if (actionRunId <= 0) {
            throw new IllegalArgumentException("动作运行 ID 必须为正数");
        }
        return new CompensationEventRequest(Long.toString(actionRunId), EVENT_TYPE,
            WorkflowCallbackHandlerSupport.NO_SUB_EVENT_TYPE);
    }

    /** 持久化任务类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 每次 20 秒（查询上限 8 秒加两次锁等待），最多 5 次，退避 5 秒到 1 分钟。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(20), 5, Duration.ofSeconds(5), Duration.ofMinutes(1),
            2, 0.2);
    }

    /** 事务外执行一次检查。 */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ErrorException("工作流数据查询首查不能处于已有事务中");
        }
        Long actionRunId = actionRunId(event);
        if (actionRunId == null) {
            return CompensationHandlerResult.terminalFailure("WORKFLOW_DATA_QUERY_CHECK_INVALID",
                "工作流数据查询任务身份不合法");
        }
        try {
            DataQueryCheckView view = checker.process(CheckDataQueryRequest.system(actionRunId,
                DataQueryCheckTrigger.ACTIVATION));
            return view.isBusy() ? CompensationHandlerResult.retryableFailure("WORKFLOW_INSTANCE_BUSY",
                "流程实例正在处理，稍后重试首查") : CompensationHandlerResult.success();
        } catch (ConcurrentRequestException inProgress) {
            return CompensationHandlerResult.success();
        } catch (RuntimeException failure) {
            // 任务结果须回写为可重试，摘要只含异常类型
            return CompensationHandlerResult.retryableFailure("WORKFLOW_DATA_QUERY_CHECK_FAILED",
                "工作流数据查询首查异常：" + failure.getClass().getSimpleName());
        }
    }

    /**
     * 解析事件键为动作运行 ID；不合法返回 null。
     *
     * @param event 补偿事件
     * @return 动作运行 ID 或 null
     */
    static Long actionRunId(CompensationEvent event) {
        if (event.payloadJson() != null || !event.eventKey().matches("[1-9][0-9]{0,18}")) {
            return null;
        }
        try {
            return Long.parseLong(event.eventKey());
        } catch (NumberFormatException outOfRange) {
            return null;
        }
    }
}
