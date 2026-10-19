package com.paytech.commercial.ext.job;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.commercial.service.processor.ActivateDueCommercialTermVersionsProcessor;
import com.paytech.commercial.service.processor.response.CommercialTermActivationRunResponse;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;

import lombok.AllArgsConstructor;

/**
 * XXL-Job 入口：商业条款版本定时激活（technique.md §2.2）；有版本激活失败时标记任务失败，失败版本保持 SCHEDULED 由下一轮重试
 * <li>无本机 XXL-Job 时由 POST /commercial-terms/v1/versions/{versionId}/activate 手工触发同一激活用例。</li>
 */
@Component
@AllArgsConstructor
public class CommercialTermVersionActivationJobHandler {
    /** XXL-Job Handler 名。 */
    public static final String HANDLER_NAME = "commercialTermVersionActivation";

    private final Clock clock;
    private final ActivateDueCommercialTermVersionsProcessor processor;

    /** 执行一轮定时激活并记录结果。 */
    @XxlJob(HANDLER_NAME)
    public void activate() {
        try {
            CommercialTermActivationRunResponse result = runOnce();
            String summary = "commercial term activation: due " + result.getDueCount() + ", activated "
                + result.getActivatedCount() + ", failed " + result.getFailedCount();
            if (result.getFailedCount() > 0) {
                XxlJobHelper.handleFail(summary);
                return;
            }
            XxlJobHelper.log(summary);
        } catch (RuntimeException failure) {
            XxlJobHelper.handleFail("commercial term activation failed: " + failure.getClass().getSimpleName());
        }
    }

    /**
     * 按当前 UTC 时间执行一轮
     *
     * @return 本轮结果
     */
    public CommercialTermActivationRunResponse runOnce() {
        return processor.process(clock.instant().truncatedTo(ChronoUnit.MICROS));
    }
}
