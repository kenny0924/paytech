package com.paytech.channel.connector.ext.job;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.domain.ChannelCostActivationResult;
import com.paytech.channel.connector.service.processor.ActivateDueChannelCostVersionsProcessor;
import com.paytech.channel.connector.service.processor.request.ActivateDueChannelCostVersionsRequest;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;

import lombok.AllArgsConstructor;

/** XXL-Job 入口：渠道成本 APPROVED 版本到期切换为当前版本；有版本切换失败时标记任务失败，版本保持 APPROVED 由下一轮重试。 */
@Component
@AllArgsConstructor
public class ChannelCostVersionActivationJobHandler {
    /** XXL-Job Handler 名。 */
    public static final String HANDLER_NAME = "channelCostVersionActivation";

    private final Clock clock;
    private final ActivateDueChannelCostVersionsProcessor processor;

    /** 执行一轮到期切换并记录结果。 */
    @XxlJob(HANDLER_NAME)
    public void activate() {
        try {
            ChannelCostActivationResult result = runOnce();
            String summary = "channel cost version activation: activated " + result.getActivatedCount() + ", skipped "
                + result.getSkippedCount() + ", failed " + result.getFailedCount();
            if (result.getFailedCount() > 0) {
                XxlJobHelper.handleFail(summary);
                return;
            }
            XxlJobHelper.log(summary);
        } catch (RuntimeException failure) {
            XxlJobHelper.handleFail("channel cost version activation failed: " + failure.getClass().getSimpleName());
        }
    }

    /**
     * 按当前 UTC 时间执行一轮
     *
     * @return 本轮结果
     */
    public ChannelCostActivationResult runOnce() {
        return processor.process(
            new ActivateDueChannelCostVersionsRequest(clock.instant().truncatedTo(ChronoUnit.MICROS)));
    }
}
