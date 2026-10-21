package com.paytech.reconciliation.app.ext.job;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.reconciliation.service.processor.ExpireWaitingChannelLinesProcessor;
import com.paytech.reconciliation.service.processor.request.ExpireWaitingChannelLinesRequest;
import com.paytech.reconciliation.service.processor.response.ExpireWaitingChannelLinesResponse;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;

import lombok.AllArgsConstructor;

/** XXL-Job 入口：账单行等待期满转 CHANNEL_LINE_UNMATCHED 异常（technique §2.4，每 10 分钟）；委托 core Processor。 */
@Component
@AllArgsConstructor
public class ChannelLineWaitExpiryJobHandler {
    /** XXL-Job Handler 名。 */
    public static final String HANDLER_NAME = "channelLineWaitExpiry";

    private final Clock clock;
    private final ExpireWaitingChannelLinesProcessor processor;

    /** 执行一轮并记录结果；整轮失败时标记任务失败，下一轮重试。 */
    @XxlJob(HANDLER_NAME)
    public void expire() {
        try {
            ExpireWaitingChannelLinesResponse result = runOnce();
            XxlJobHelper.log("reconciliation channel line wait expiry: opened " + result.getOpenedCount()
                + ", skipped " + result.getSkippedCount());
        } catch (RuntimeException failure) {
            XxlJobHelper.handleFail("reconciliation channel line wait expiry failed: "
                + failure.getClass().getSimpleName());
        }
    }

    /**
     * 按当前 UTC 时间执行一轮
     *
     * @return 本轮结果
     */
    public ExpireWaitingChannelLinesResponse runOnce() {
        return processor.process(new ExpireWaitingChannelLinesRequest(clock.instant().truncatedTo(ChronoUnit.MICROS)));
    }
}
