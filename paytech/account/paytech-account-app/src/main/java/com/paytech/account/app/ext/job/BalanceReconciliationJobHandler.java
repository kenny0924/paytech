package com.paytech.account.app.ext.job;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

import com.paytech.account.service.processor.ReconcileBalancesProcessor;
import com.paytech.account.service.processor.request.ReconcileBalancesRequest;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;

import lombok.AllArgsConstructor;

/** XXL-Job 入口：日终核对账户余额与 ledger 分录聚合余额，差异写 t_balance_discrepancy 并记 WARN（design/04 §7）；阶段 0 不接工单。 */
@Component
@AllArgsConstructor
public class BalanceReconciliationJobHandler {
    /** XXL-Job Handler 名。 */
    public static final String HANDLER_NAME = "accountBalanceReconciliation";

    private final Clock clock;
    private final ReconcileBalancesProcessor processor;

    /** 执行一轮核对并记录差异数；失败时标记任务失败，由调度重试。 */
    @XxlJob(HANDLER_NAME)
    public void reconcile() {
        try {
            XxlJobHelper.log("account balance discrepancies: " + runOnce());
        } catch (RuntimeException failure) {
            XxlJobHelper.handleFail("account balance reconciliation failed: " + failure.getClass().getSimpleName());
        }
    }

    /**
     * 按当前 UTC 日期执行一轮核对
     *
     * @return 本轮记录的差异数
     */
    public int runOnce() {
        return processor.process(new ReconcileBalancesRequest(LocalDate.now(clock.withZone(ZoneOffset.UTC))));
    }
}
