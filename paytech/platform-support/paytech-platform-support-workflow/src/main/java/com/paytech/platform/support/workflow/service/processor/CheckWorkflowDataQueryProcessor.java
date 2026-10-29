package com.paytech.platform.support.workflow.service.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.api.enums.DataQueryErrorCode;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.dataquery.domain.DataQueryResult;
import com.paytech.platform.support.dataquery.domain.QueryTemplateSignature;
import com.paytech.platform.support.dataquery.domainservice.service.QueryTemplateService;
import com.paytech.platform.support.dataquery.service.processor.RunDataQueryTemplateProcessor;
import com.paytech.platform.support.dataquery.service.processor.request.RunDataQueryTemplateRequest;
import com.paytech.platform.support.workflow.core.configuration.WorkflowProperties;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowNodeRun;
import com.paytech.platform.support.workflow.core.enums.DataQueryCheckFailure;
import com.paytech.platform.support.workflow.core.enums.DataQueryCheckTrigger;
import com.paytech.platform.support.workflow.core.enums.WorkflowInstanceStatus;
import com.paytech.platform.support.workflow.domain.WorkflowInstanceContext;
import com.paytech.platform.support.workflow.domain.action.DataQueryActionConfig;
import com.paytech.platform.support.workflow.domain.runtime.DataQueryCheckOutcome;
import com.paytech.platform.support.workflow.domainservice.process.DataQueryParameterResolver;
import com.paytech.platform.support.workflow.domainservice.process.DataQueryResultReader;
import com.paytech.platform.support.workflow.domainservice.process.WorkflowRuntimeEngine;
import com.paytech.platform.support.workflow.domainservice.service.WorkflowInstanceQueryService;
import com.paytech.platform.support.workflow.service.processor.request.ApplyDataQueryCheckRequest;
import com.paytech.platform.support.workflow.service.processor.request.CheckDataQueryRequest;
import com.paytech.platform.support.workflow.service.processor.response.DataQueryCheckView;

import io.micrometer.core.instrument.MeterRegistry;

import lombok.RequiredArgsConstructor;

import tools.jackson.databind.node.ObjectNode;

/**
 * 检查一个 DATA_QUERY 动作（REQ-05，technique 第 7 节）：首查补偿任务、定时重查与立即检查共用
 * <li>锁 workflow-action-check:{actionRunId}（TRY_LOCK，等待 0，租约 workflow.data-query.check-lock-lease）：同一动作同一时刻
 * 只执行一次查询；未取得锁抛并发冲突，由各入口按“检查进行中”处理。</li>
 * <li>读实例上下文（不加实例锁）：动作须为 DATA_QUERY 且待处理、节点未结束、实例运行中，否则不查询直接返回。</li>
 * <li>按节点动作 inputs 取参数，在数据库事务外、实例锁外以空操作人执行 data-query（超时上限 workflow.data-query.timeout），
 * 按单行约定解读并按声明类型规整；查询异常一律记为失败码，不向调用方抛出。</li>
 * <li>结果交 {@link ApplyWorkflowDataQueryCheckProcessor} 在实例锁内落库；实例锁或版本冲突时结果丢弃，返回 busy。</li>
 * <li>记指标 workflow.data_query.check{trigger,result}（result 为 SATISFIED、UNSATISFIED 或失败码）与一条日志，
 * 只含动作运行、模板编码、结果与耗时，不记参数与结果行。</li>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CheckWorkflowDataQueryProcessor extends AbstractLockProcessor<CheckDataQueryRequest, DataQueryCheckView> {

    /** data-query 日志与指标中的调用方标签。 */
    static final String CALLER = "workflow";

    /** 检查指标名。 */
    static final String METRIC = "workflow.data_query.check";

    private static final Logger LOG = LoggerFactory.getLogger(CheckWorkflowDataQueryProcessor.class);

    private final MeterRegistry meters;
    private final WorkflowProperties properties;
    private final WorkflowProcessorSupport support;
    private final WorkflowInstanceCommands commands;
    private final QueryTemplateService queryTemplates;
    private final WorkflowInstanceQueryService queries;
    private final RunDataQueryTemplateProcessor dataQuery;
    private final ApplyWorkflowDataQueryCheckProcessor applier;
    private final WorkflowRuntimeEngine engine = new WorkflowRuntimeEngine();
    private final DataQueryResultReader reader = new DataQueryResultReader();
    private final DataQueryParameterResolver resolver = new DataQueryParameterResolver();

    /**
     * 检查锁 Key。
     *
     * @param actionRunId 动作运行 ID
     * @return 锁 Key
     */
    static String lock(long actionRunId) {
        return "workflow-action-check:" + actionRunId;
    }

    /** MANUAL 必须带操作人与会话。 */
    @Override
    protected void validate(CheckDataQueryRequest request) {
        boolean manual = request.getTrigger() == DataQueryCheckTrigger.MANUAL;
        if (manual && (request.getActor() == null || request.getSession() == null)) {
            throw new InvalidParameterException("立即检查必须带操作人与会话");
        }
    }

    /** 立即尝试获取检查锁。 */
    @Override
    protected LockCommand lockCommand(CheckDataQueryRequest request) {
        return LockCommand.tryLock(lock(request.getActionRunId()), 0,
            Math.toIntExact(properties.getDataQuery().getCheckLockLease().toMillis()));
    }

    @Override
    protected DataQueryCheckView processWithinLock(CheckDataQueryRequest request) {
        // 读取实例上下文与动作
        long instanceId = commands.instanceOfActionRun(request.getActionRunId());
        WorkflowInstanceContext context = queries.load(instanceId, support.now());
        if (context == null) {
            throw new ErrorException("动作运行所属实例不存在：" + instanceId);
        }
        WorkflowActionRun row = actionRun(context, request.getActionRunId());
        if (!checkable(context, row)) {
            return DataQueryCheckView.notExecuted(row.getActionRunId());
        }
        DataQueryActionConfig config = (DataQueryActionConfig) context.getTemplates().get(row.getActionTemplateId())
            .config();

        // 事务外、实例锁外执行查询并解读
        long startedAt = System.nanoTime();
        DataQueryCheckOutcome outcome = query(context, row, config);

        // 实例锁内落库
        DataQueryCheckView view = apply(request, row, instanceId, outcome);
        observe(request.getTrigger(), row, config, outcome, view, startedAt);
        return view;
    }

    /**
     * 实例运行中且动作仍可检查。
     *
     * @param context 实例上下文
     * @param row 动作运行
     * @return 可检查返回 true
     */
    boolean checkable(WorkflowInstanceContext context, WorkflowActionRun row) {
        WorkflowNodeRun nodeRun = context.nodeRun(row.getNodeRunId())
            .orElseThrow(() -> new ErrorException("实例中不存在节点运行：" + row.getNodeRunId()));
        return context.getInstance().getStatus() == WorkflowInstanceStatus.RUNNING
            && engine.dataQueryCheckable(row, nodeRun);
    }

    /**
     * 执行查询并按单行约定解读；模板不可用或执行异常都记为失败码。
     *
     * @param context 实例上下文
     * @param row 动作运行
     * @param config 动作配置（版本快照）
     * @return 检查结果
     */
    DataQueryCheckOutcome query(WorkflowInstanceContext context, WorkflowActionRun row, DataQueryActionConfig config) {
        QueryTemplateSignature signature = queryTemplates.findTemplateSignature(config.queryCode());
        if (signature == null || signature.getStatus() != QueryTemplateStatus.ACTIVE
            || !signature.isDataSourceActive()) {
            return DataQueryCheckOutcome.failed(DataQueryCheckFailure.TEMPLATE_UNAVAILABLE);
        }
        String nodeKey = context.nodeRun(row.getNodeRunId()).orElseThrow().getNodeKey();
        ObjectNode params = resolver.resolve(context.action(nodeKey, row.getActionKey()).inputs(),
            engine.variables(context));
        try {
            DataQueryResult result = dataQuery.process(new RunDataQueryTemplateRequest(config.queryCode(), params, null,
                null, false, null, null, properties.getDataQuery().timeoutSeconds(), CALLER));
            return reader.read(result.getColumns().stream().map(DataQueryResult.Column::getName).toList(),
                result.getRows(), result.isHasMore(), config.outputs());
        } catch (RuntimeException failure) {
            // 查询失败只记录为未完成的检查结果（R4），不打断调用方
            DataQueryCheckFailure code = failure(failure);
            if (code == DataQueryCheckFailure.QUERY_FAILED) {
                LOG.warn("工作流数据查询失败 actionRun={} template={} type={}", row.getActionRunId(), config.queryCode(),
                    failure.getClass().getSimpleName());
            }
            return DataQueryCheckOutcome.failed(code);
        }
    }

    /**
     * data-query 异常对应的失败码。
     *
     * @param failure 异常
     * @return 失败码
     */
    static DataQueryCheckFailure failure(RuntimeException failure) {
        if (failure instanceof RequestBodyValidationException || failure instanceof InvalidParameterException) {
            return DataQueryCheckFailure.PARAMETER_INVALID;
        }
        Object code = failure instanceof FailException fail ? fail.getErrorCode()
            : failure instanceof ErrorException error ? error.getErrorCode() : null;
        if (code == DataQueryErrorCode.DATA_QUERY_TIMEOUT) {
            return DataQueryCheckFailure.QUERY_TIMEOUT;
        }
        if (code == DataQueryErrorCode.DATA_QUERY_RESULT_TOO_LARGE) {
            return DataQueryCheckFailure.RESULT_TOO_LARGE;
        }
        if (code == DataQueryErrorCode.DATA_SOURCE_UNAVAILABLE) {
            return DataQueryCheckFailure.DATA_SOURCE_UNAVAILABLE;
        }
        return code == CommonErrorCode.NOT_FOUND ? DataQueryCheckFailure.TEMPLATE_UNAVAILABLE
            : DataQueryCheckFailure.QUERY_FAILED;
    }

    /**
     * 实例锁内落库；实例锁或版本冲突时丢弃结果。
     *
     * @param request 请求
     * @param row 查询前读到的动作运行
     * @param instanceId 实例 ID
     * @param outcome 检查结果
     * @return 检查结果视图
     */
    DataQueryCheckView apply(CheckDataQueryRequest request, WorkflowActionRun row, long instanceId,
        DataQueryCheckOutcome outcome) {
        try {
            return applier.process(new ApplyDataQueryCheckRequest(row.getActionRunId(), instanceId, row.getVersion(),
                outcome, request.getTrigger(), request.getActor(), request.getSession()));
        } catch (ConcurrentRequestException busy) {
            // 实例正被其他操作处理：结果丢弃，下一轮重查
            return new DataQueryCheckView(row.getActionRunId(), true, false, true, outcome.result(), outcome.failure());
        }
    }

    /**
     * 指标与日志：只记动作运行、模板编码、结果与耗时。
     *
     * @param trigger 触发来源
     * @param row 动作运行
     * @param config 动作配置
     * @param outcome 检查结果
     * @param view 落库结果
     * @param startedAt 查询开始的纳秒时间
     */
    void observe(DataQueryCheckTrigger trigger, WorkflowActionRun row, DataQueryActionConfig config,
        DataQueryCheckOutcome outcome, DataQueryCheckView view, long startedAt) {
        String result = outcome.failure() == null ? outcome.result().getCode() : outcome.failure().getCode();
        meters.counter(METRIC, "trigger", trigger.getCode(), "result", result).increment();
        LOG.info("工作流数据查询检查 actionRun={} template={} trigger={} result={} recorded={} elapsedMs={}",
            row.getActionRunId(), config.queryCode(), trigger.getCode(), result, view.isRecorded(),
            (System.nanoTime() - startedAt) / 1_000_000L);
    }

    /**
     * 取实例内的动作运行。
     *
     * @param context 实例上下文
     * @param actionRunId 动作运行 ID
     * @return 动作运行
     */
    static WorkflowActionRun actionRun(WorkflowInstanceContext context, long actionRunId) {
        return context.getActionRuns().stream().filter(run -> run.getActionRunId() == actionRunId).findFirst()
            .orElseThrow(() -> new ErrorException("实例中不存在动作运行：" + actionRunId));
    }
}
