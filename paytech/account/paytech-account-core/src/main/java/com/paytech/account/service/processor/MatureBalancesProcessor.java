package com.paytech.account.service.processor;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.account.core.dal.dataobject.BalanceAppliedFact;
import com.paytech.account.domain.model.MaturityRunResult;
import com.paytech.account.domainservice.service.BalanceMaturityService;
import com.paytech.account.service.processor.request.MatureBalancesRequest;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 一轮到期转可用（XXL-Job accountBalanceMaturity，设计 8.5）
 * <li>按主键游标分页读取到期的 PENDING 事实，每条事实一个短事务；单条失败只记 WARN 并保留 PENDING，下轮重试，不阻塞其他事实。</li>
 * <li>重复执行按 FACT_MATURITY 的 source_ref 幂等，已 MATURED 的事实不再处理。</li>
 */
@Component
@AllArgsConstructor
public class MatureBalancesProcessor extends AbstractProcessor<MatureBalancesRequest, MaturityRunResult> {
    /** 每页事实数。 */
    static final int PAGE_SIZE = 100;

    private static final Logger LOG = LoggerFactory.getLogger(MatureBalancesProcessor.class);

    private final BalanceMaturityService balanceMaturityService;

    @Override
    protected MaturityRunResult processRequest(MatureBalancesRequest request) {
        int matured = 0;
        int movements = 0;
        int failed = 0;
        long afterId = 0;
        List<BalanceAppliedFact> page;
        do {
            page = balanceMaturityService.findDue(request.getAsOf(), afterId, PAGE_SIZE);
            for (BalanceAppliedFact fact : page) {
                afterId = fact.getId();
                try {
                    movements += balanceMaturityService.mature(fact.getFactId());
                    matured++;
                } catch (RuntimeException failure) {
                    // 单条事实失败不阻塞其他事实；它保持 PENDING，由下一轮重试。
                    failed++;
                    LOG.warn("计费事实到期转可用失败: fact {}, {}", fact.getFactId(), failure.getClass().getSimpleName());
                }
            }
        } while (page.size() == PAGE_SIZE);
        return new MaturityRunResult(matured, movements, failed);
    }
}
