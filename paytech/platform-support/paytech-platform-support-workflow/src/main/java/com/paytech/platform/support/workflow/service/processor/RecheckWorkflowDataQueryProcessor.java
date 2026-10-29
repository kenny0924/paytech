package com.paytech.platform.support.workflow.service.processor;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.workflow.core.configuration.WorkflowProperties;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;
import com.paytech.platform.support.workflow.core.enums.DataQueryCheckTrigger;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowActionRunRepository;
import com.paytech.platform.support.workflow.service.processor.request.CheckDataQueryRequest;
import com.paytech.platform.support.workflow.service.processor.request.RecheckDataQueryRequest;
import com.paytech.platform.support.workflow.service.processor.response.DataQueryRecheckSummary;

import lombok.RequiredArgsConstructor;

/**
 * 定时重查一个分片的 PENDING DATA_QUERY 动作（REQ-05 R3），无锁
 * <li>按 instance_id 取模分片，取最近检查早于 workflow.data-query.recheck-min-age 的动作（从未检查的优先），
 * 每轮最多 recheck-batch-size 个，累计耗时超过 recheck-round-budget 即停。</li>
 * <li>逐个交 {@link CheckWorkflowDataQueryProcessor}：检查锁被占用计 skipped，落库冲突计 busy，意外异常计 failed 并继续。</li>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class RecheckWorkflowDataQueryProcessor
    extends AbstractProcessor<RecheckDataQueryRequest, DataQueryRecheckSummary> {

    private static final Logger LOG = LoggerFactory.getLogger(RecheckWorkflowDataQueryProcessor.class);

    private final WorkflowProperties properties;
    private final WorkflowProcessorSupport support;
    private final WorkflowActionRunRepository actionRuns;
    private final CheckWorkflowDataQueryProcessor checker;

    @Override
    protected DataQueryRecheckSummary processRequest(RecheckDataQueryRequest request) {
        WorkflowProperties.DataQuery settings = properties.getDataQuery();
        long deadline = System.nanoTime() + settings.getRecheckRoundBudget().toNanos();
        Instant checkedBefore = support.now().minus(settings.getRecheckMinAge());
        List<WorkflowActionRun> due = actionRuns.findDueDataQueryChecks(request.getShardIndex(),
            request.getShardTotal(), checkedBefore, settings.getRecheckBatchSize());
        DataQueryRecheckSummary summary = new DataQueryRecheckSummary();
        for (WorkflowActionRun row : due) {
            if (System.nanoTime() > deadline) {
                break;
            }
            check(row.getActionRunId(), summary);
        }
        return summary;
    }

    /**
     * 检查一个动作并计数；单个动作的失败不影响本轮其他动作。
     *
     * @param actionRunId 动作运行 ID
     * @param summary 本轮计数
     */
    void check(long actionRunId, DataQueryRecheckSummary summary) {
        try {
            summary.add(checker.process(CheckDataQueryRequest.system(actionRunId, DataQueryCheckTrigger.SCHEDULE)));
        } catch (ConcurrentRequestException inProgress) {
            summary.skip();
        } catch (RuntimeException failure) {
            // 单个动作的意外异常只计数并继续本轮，由 XXL 记失败
            summary.fail();
            LOG.warn("工作流数据查询定时重查异常 actionRun={} type={}", actionRunId, failure.getClass().getSimpleName(),
                failure);
        }
    }
}
