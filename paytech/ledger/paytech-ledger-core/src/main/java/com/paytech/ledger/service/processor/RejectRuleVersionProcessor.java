package com.paytech.ledger.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.ledger.core.constant.AccountingLocks;
import com.paytech.ledger.domain.AccountingRuleVersionContext;
import com.paytech.ledger.domain.view.RuleVersionView;
import com.paytech.ledger.domainservice.service.AccountingRuleService;
import com.paytech.ledger.domainservice.validator.rule.RuleVersionReviewValidator;
import com.paytech.ledger.service.processor.request.ReviewRuleVersionRequest;

import lombok.AllArgsConstructor;

/**
 * 驳回规则版本：Checker ≠ Maker，PENDING_APPROVAL → REJECTED，批准时间为空
 * <li>与同一规则集的其他写入口共用资源锁并持续等待；事务内先锁规则集再锁版本，状态以原状态条件更新。</li>
 */
@Component
@AllArgsConstructor
public class RejectRuleVersionProcessor extends AbstractLockProcessor<ReviewRuleVersionRequest, RuleVersionView> {
    private final AccountingRuleService accountingRuleService;

    /** 规则集资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(ReviewRuleVersionRequest request) {
        return LockCommand.lock(AccountingLocks.accountingRuleBundle(request.getBundleCode()));
    }

    @Override
    protected RuleVersionView processWithinLock(ReviewRuleVersionRequest request) {
        AccountingRuleVersionContext context =
            accountingRuleService.loadVersion(request.getBundleCode(), request.getBundleVer());
        RuleVersionReviewValidator.checkReviewable(request.getBundleCode(), context.getVer(), request.getCheckerId());
        accountingRuleService.rejectVersion(context, request.getCheckerId());
        return context.toView();
    }
}
