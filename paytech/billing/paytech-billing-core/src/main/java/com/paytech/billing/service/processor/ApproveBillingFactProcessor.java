package com.paytech.billing.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.billing.api.enums.BillingFactErrorCode;
import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.core.constant.BillingLocks;
import com.paytech.billing.core.dal.dataobject.BillingFact;
import com.paytech.billing.domain.BillingFactContext;
import com.paytech.billing.domain.view.BillingFactView;
import com.paytech.billing.domainservice.repository.BillingFactRepository;
import com.paytech.billing.domainservice.service.BillingFactService;
import com.paytech.billing.service.processor.request.ApproveBillingFactRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * Checker 批准计费事实（Maker-Checker）
 * <li>已批准（含随后被替代）的事实：同一复核人重复批准返回原事实，不重复登记事件；其他人以 FACT_NOT_DRAFT 拒绝。</li>
 * <li>VOIDED（已作废）以 FACT_NOT_DRAFT 拒绝批准。</li>
 * <li>DRAFT：复核人必须不同于编制人（SELF_REVIEW_FORBIDDEN）；有被替代事实时它必须仍为 APPROVED（FACT_SUPERSEDE_INVALID），新事实版本 = 被替代事实版本 + 1，否则为 1。</li>
 * <li>同一本地事务：事实 DRAFT → APPROVED 并定稿版本、approved_time 与 business_time 写批准时间、被替代事实 APPROVED → SUPERSEDED、
 * 登记 BILLING_FACT_RECORDED 发布任务；发布由补偿 Handler 在提交后执行，本入口事务内不调用外部服务。</li>
 * <li>同一事实的批准与作废共用资源锁并持续等待，并发批准只有一次进入 DRAFT 分支；两条更正事实替代同一事实时由被替代事实的状态与版本条件更新保护。</li>
 */
@Component
@AllArgsConstructor
public class ApproveBillingFactProcessor extends AbstractLockProcessor<ApproveBillingFactRequest, BillingFactView> {
    private final Clock clock;
    private final BillingFactService billingFactService;
    private final BillingFactRepository billingFactRepository;

    /** 事实资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(ApproveBillingFactRequest request) {
        return LockCommand.lock(BillingLocks.billingFact(request.getFactId()));
    }

    @Override
    protected BillingFactView processWithinLock(ApproveBillingFactRequest request) {
        // 查询原批准
        BillingFactContext loaded = getAndCheckFact(request.getFactId());
        if (loaded.recorded()) {
            return getOriginalApproval(loaded, request.getCheckerId());
        }

        // 构建上下文
        BillingFactContext context = buildApproveContext(loaded, request.getCheckerId());

        // 保存上下文
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        billingFactService.approve(context, request.getCheckerId(), now);

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

    /** 已批准事实只对原复核人返回原结果。 */
    BillingFactView getOriginalApproval(BillingFactContext context, long checkerId) {
        BillingFact fact = context.getFact();
        if (!Objects.equals(fact.getCheckerId(), checkerId)) {
            throw new FailException(BillingFactErrorCode.FACT_NOT_DRAFT);
        }
        return BillingFactView.of(context, null, List.of());
    }

    /** 未作废且复核人不同于编制人；有被替代事实时加载并要求其仍为 APPROVED。 */
    BillingFactContext buildApproveContext(BillingFactContext loaded, long checkerId) {
        BillingFact fact = loaded.getFact();
        if (fact.getStatus() == BillingFactStatus.VOIDED) {
            throw new FailException(BillingFactErrorCode.FACT_NOT_DRAFT);
        }
        if (Objects.equals(fact.getMakerId(), checkerId)) {
            throw new FailException(BillingFactErrorCode.SELF_REVIEW_FORBIDDEN);
        }
        BillingFact superseded = null;
        if (fact.getSupersedesFactId() != null) {
            superseded = billingFactRepository.findById(fact.getSupersedesFactId());
            if (superseded == null || superseded.getStatus() != BillingFactStatus.APPROVED) {
                throw new FailException(BillingFactErrorCode.FACT_SUPERSEDE_INVALID);
            }
        }
        return new BillingFactContext(fact, loaded.getRefs(), loaded.getLines(), superseded, loaded.getMinorUnit());
    }
}
