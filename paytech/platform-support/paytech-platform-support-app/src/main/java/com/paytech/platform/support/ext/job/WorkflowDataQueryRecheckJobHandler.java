package com.paytech.platform.support.ext.job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.platform.support.workflow.service.processor.RecheckWorkflowDataQueryProcessor;
import com.paytech.platform.support.workflow.service.processor.request.RecheckDataQueryRequest;
import com.paytech.platform.support.workflow.service.processor.response.DataQueryRecheckSummary;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;

import lombok.AllArgsConstructor;

/**
 * XXL-Job 入口：工作流 DATA_QUERY 定时重查（REQ-05 R3，technique 10.2），按 XXL 分片（instance_id 取模）扫描本分片
 * <li>建议 CRON 每 5 分钟、路由 SHARDING_BROADCAST、阻塞策略 DISCARD_LATER、超时 300 秒、参数为空。</li>
 * <li>查询失败、未满足属于正常检查结果；只有处理中出现意外异常时标记任务失败，失败的动作由下一轮重试。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class WorkflowDataQueryRecheckJobHandler {

    /** XXL-Job Handler 名。 */
    public static final String HANDLER_NAME = "workflowDataQueryRecheck";

    private final RecheckWorkflowDataQueryProcessor processor;

    /** 按分片执行一轮并记录结果。 */
    @XxlJob(HANDLER_NAME)
    public void recheck() {
        try {
            DataQueryRecheckSummary result = runOnce(XxlJobHelper.getShardIndex(), XxlJobHelper.getShardTotal());
            String summary = "workflow data-query recheck: checked " + result.getChecked() + ", completed "
                + result.getCompleted() + ", unsatisfied " + result.getUnsatisfied() + ", queryFailed "
                + result.getQueryFailed() + ", skipped " + result.getSkipped() + ", busy " + result.getBusy()
                + ", failed " + result.getFailed();
            if (result.getFailed() > 0) {
                XxlJobHelper.handleFail(summary);
                return;
            }
            XxlJobHelper.log(summary);
        } catch (RuntimeException failure) {
            XxlJobHelper.handleFail("workflow data-query recheck failed: " + failure.getClass().getSimpleName());
        }
    }

    /**
     * 执行一轮
     *
     * @param shardIndex 分片序号
     * @param shardTotal 分片总数；未分片调度时 XXL 给出 1
     * @return 本轮计数
     */
    public DataQueryRecheckSummary runOnce(int shardIndex, int shardTotal) {
        return processor.process(new RecheckDataQueryRequest(Math.max(shardIndex, 0), Math.max(shardTotal, 1)));
    }
}
