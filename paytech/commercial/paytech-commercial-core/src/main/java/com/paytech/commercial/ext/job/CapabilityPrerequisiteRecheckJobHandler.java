package com.paytech.commercial.ext.job;

import org.springframework.stereotype.Component;

import com.paytech.commercial.service.processor.RecheckDueCapabilityPrerequisitesProcessor;
import com.paytech.commercial.service.processor.request.RecheckDueCapabilityPrerequisitesRequest;
import com.paytech.commercial.service.processor.response.CapabilityPrerequisiteRecheckResponse;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;

import lombok.AllArgsConstructor;

/**
 * XXL-Job 入口：开通项前置定时重查（开通项前置门禁 technique.md §12、S-06），按 XXL 分片扫描本分片的合同
 * <li>任务参数 {@code scope=PENDING}（默认，建议每 5 分钟）只扫等待前置与前置失效暂停；{@code scope=ALL}（建议每日 03:00）另含已开通，
 * 防止资格事件丢失。</li>
 * <li>有合同核对失败时标记任务失败，失败合同状态不变，由下一轮重试；无本机 XXL-Job 时由运营"检查前置"触发同一门禁。</li>
 */
@Component
@AllArgsConstructor
public class CapabilityPrerequisiteRecheckJobHandler {
    /** XXL-Job Handler 名。 */
    public static final String HANDLER_NAME = "capabilityPrerequisiteRecheck";

    /** 全量复核的任务参数。 */
    static final String SCOPE_ALL = "scope=ALL";

    private final RecheckDueCapabilityPrerequisitesProcessor processor;

    /** 按任务参数与分片执行一轮并记录结果。 */
    @XxlJob(HANDLER_NAME)
    public void recheck() {
        try {
            CapabilityPrerequisiteRecheckResponse result = runOnce(XxlJobHelper.getJobParam(),
                XxlJobHelper.getShardIndex(), XxlJobHelper.getShardTotal());
            String summary = "capability prerequisite recheck: checked " + result.getCheckedCount() + ", changed "
                + result.getChangedCount() + ", unknown " + result.getUnknownCount() + ", failed " + result.getFailedCount();
            if (result.getFailedCount() > 0) {
                XxlJobHelper.handleFail(summary);
                return;
            }
            XxlJobHelper.log(summary);
        } catch (RuntimeException failure) {
            XxlJobHelper.handleFail("capability prerequisite recheck failed: " + failure.getClass().getSimpleName());
        }
    }

    /**
     * 执行一轮
     *
     * @param jobParam 任务参数；{@value #SCOPE_ALL} 含已开通，其他（含空）只扫等待门禁处理的开通项
     * @param shardIndex 分片序号
     * @param shardTotal 分片总数；未分片调度时 XXL 给出 1
     * @return 本轮结果
     */
    public CapabilityPrerequisiteRecheckResponse runOnce(String jobParam, int shardIndex, int shardTotal) {
        boolean includeEnabled = jobParam != null && SCOPE_ALL.equals(jobParam.trim());
        return processor.process(new RecheckDueCapabilityPrerequisitesRequest(includeEnabled, shardIndex,
            Math.max(shardTotal, 1)));
    }
}
