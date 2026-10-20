package com.paytech.billing.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.billing.api.enums.BillingFactErrorCode;
import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.core.constant.BillingLocks;
import com.paytech.billing.core.dal.dataobject.BillingFact;
import com.paytech.billing.domain.BillingFactContext;
import com.paytech.billing.domain.view.BillingFactView;
import com.paytech.billing.domainservice.service.BillingFactService;
import com.paytech.billing.service.processor.request.VoidBillingFactRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 作废填错的计费事实草稿（DRAFT → VOIDED）
 * <li>已作废：直接返回原事实；APPROVED、SUPERSEDED 以 FACT_NOT_DRAFT 拒绝。VOIDED 为终态，不能再批准。</li>
 * <li>单条条件更新（仍为 DRAFT 且版本 0），不登记发布任务、不发布事件；account / ledger 只消费已发布的 APPROVED 事实，不受影响。</li>
 * <li>与批准共用同一事实资源锁并持续等待，作废与批准串行，只有先到者成功；条件更新为最终防线。</li>
 */
@Component
@AllArgsConstructor
public class VoidBillingFactProcessor extends AbstractLockProcessor<VoidBillingFactRequest, BillingFactView> {
    private final Clock clock;
    private final BillingFactService billingFactService;

    /** 事实资源锁（与批准相同），持续等待。 */
    @Override
    protected LockCommand lockCommand(VoidBillingFactRequest request) {
        return LockCommand.lock(BillingLocks.billingFact(request.getFactId()));
    }

    @Override
    protected BillingFactView processWithinLock(VoidBillingFactRequest request) {
        // 查询原作废
        BillingFactContext context = getAndCheckFact(request.getFactId());
        if (context.getFact().getStatus() == BillingFactStatus.VOIDED) {
            return BillingFactView.of(context, null, List.of());
        }

        // 业务验证
        checkDraft(context.getFact());

        // 保存上下文
        billingFactService.voidDraft(context, clock.instant().truncatedTo(ChronoUnit.MICROS));

        // 响应处理
        return BillingFactView.of(context, null, List.of());
    }

    /** 读取事实；不存在时业务拒绝。 */
    BillingFactContext getAndCheckFact(long factId) {
        BillingFactContext context = billingFactService.loadContext(factId);
        if (context == null) {
            throw new BizCheckFailException("计费事实不存在: " + factId);
        }
        return context;
    }

    /** 只有草稿可以作废。 */
    void checkDraft(BillingFact fact) {
        if (fact.getStatus() != BillingFactStatus.DRAFT) {
            throw new FailException(BillingFactErrorCode.FACT_NOT_DRAFT);
        }
    }
}
