package com.paytech.account.app.ext.job;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.account.domain.model.MaturityRunResult;
import com.paytech.account.service.processor.MatureBalancesProcessor;
import com.paytech.account.service.processor.request.MatureBalancesRequest;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;

import lombok.AllArgsConstructor;

/** XXL-Job 入口：应付余额到期转可用（设计 8.5）；有事实处理失败时标记任务失败，事实保持 PENDING 由下一轮重试。 */
@Component
@AllArgsConstructor
public class BalanceMaturityJobHandler {
    /** XXL-Job Handler 名。 */
    public static final String HANDLER_NAME = "accountBalanceMaturity";

    private final Clock clock;
    private final MatureBalancesProcessor processor;

    /** 执行一轮到期转可用并记录结果。 */
    @XxlJob(HANDLER_NAME)
    public void mature() {
        try {
            MaturityRunResult result = runOnce();
            String summary = "account balance maturity: matured " + result.getMaturedFactCount() + ", movements "
                + result.getMovementCount() + ", failed " + result.getFailedFactCount();
            if (result.getFailedFactCount() > 0) {
                XxlJobHelper.handleFail(summary);
                return;
            }
            XxlJobHelper.log(summary);
        } catch (RuntimeException failure) {
            XxlJobHelper.handleFail("account balance maturity failed: " + failure.getClass().getSimpleName());
        }
    }

    /**
     * 按当前 UTC 时间执行一轮
     *
     * @return 本轮结果
     */
    public MaturityRunResult runOnce() {
        return processor.process(new MatureBalancesRequest(clock.instant().truncatedTo(ChronoUnit.MICROS)));
    }
}
