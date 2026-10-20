package com.paytech.ledger.app.ext.job;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.ledger.service.processor.ActivateDueRuleVersionsProcessor;
import com.paytech.ledger.service.processor.request.ActivateDueRuleVersionsRequest;
import com.paytech.ledger.service.processor.response.ActivateDueRuleVersionsResponse;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;

import lombok.AllArgsConstructor;

/**
 * XXL-Job 入口：把已到生效时间的 APPROVED 自动记账规则版本切换为生效（调度每分钟一次，2026-10-20 财务控制台 L4）
 * <li>已被当前版本取代的到期版本置为 RETIRED 并记 WARN，不算失败。</li>
 * <li>有版本生效失败时标记任务失败，该版本保持 APPROVED 由下一轮重试；重复执行幂等。</li>
 */
@Component
@AllArgsConstructor
public class AccountingRuleVersionActivationJobHandler {
    /** XXL-Job Handler 名。 */
    public static final String HANDLER_NAME = "accountingRuleVersionActivation";

    private final Clock clock;
    private final ActivateDueRuleVersionsProcessor processor;

    /** 执行一轮到期生效并记录结果。 */
    @XxlJob(HANDLER_NAME)
    public void activate() {
        try {
            ActivateDueRuleVersionsResponse result = runOnce();
            String summary = "accounting rule version activation: activated " + result.getActivatedCount()
                + ", retired " + result.getRetiredCount() + ", failed " + result.getFailedCount();
            if (result.getFailedCount() > 0) {
                XxlJobHelper.handleFail(summary);
                return;
            }
            XxlJobHelper.log(summary);
        } catch (RuntimeException failure) {
            XxlJobHelper.handleFail("accounting rule version activation failed: " + failure.getClass().getSimpleName());
        }
    }

    /**
     * 按当前 UTC 时间执行一轮
     *
     * @return 本轮生效与失败的版本数
     */
    public ActivateDueRuleVersionsResponse runOnce() {
        return processor.process(new ActivateDueRuleVersionsRequest(clock.instant().truncatedTo(ChronoUnit.MICROS)));
    }
}
