package com.paytech.ledger.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.ledger.api.enums.LedgerErrorCode;
import com.paytech.ledger.core.constant.AccountingLocks;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.ledger.core.enums.RuleVersionStatus;
import com.paytech.ledger.domain.AccountingRuleVersionContext;
import com.paytech.ledger.domain.view.RuleVersionView;
import com.paytech.ledger.domainservice.service.AccountingRuleService;
import com.paytech.ledger.service.processor.request.VoidRuleVersionRequest;

import lombok.AllArgsConstructor;

/**
 * 作废规则草稿：DRAFT → VOIDED，只能由编制人操作；作废后不再计入"已有草稿或待复核版本"检查，可新建版本
 * <li>编制人重复作废已作废的版本直接返回当前结果。</li>
 * <li>与同一规则集的其他写入口共用资源锁并持续等待；事务内先锁规则集再锁版本，状态以原状态条件更新。</li>
 */
@Component
@AllArgsConstructor
public class VoidRuleVersionProcessor extends AbstractLockProcessor<VoidRuleVersionRequest, RuleVersionView> {
    private final AccountingRuleService accountingRuleService;

    /** 规则集资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(VoidRuleVersionRequest request) {
        return LockCommand.lock(AccountingLocks.accountingRuleBundle(request.getBundleCode()));
    }

    @Override
    protected RuleVersionView processWithinLock(VoidRuleVersionRequest request) {
        AccountingRuleVersionContext context =
            accountingRuleService.loadVersion(request.getBundleCode(), request.getBundleVer());
        checkMaker(request, context.getVer());
        if (context.getVer().getStatus() == RuleVersionStatus.VOIDED) {
            return context.toView();
        }
        if (context.getVer().getStatus() != RuleVersionStatus.DRAFT) {
            throw new FailException(LedgerErrorCode.RULE_VERSION_STATUS_INVALID);
        }
        accountingRuleService.voidVersion(context);
        return context.toView();
    }

    /** 只有编制人可以作废。 */
    void checkMaker(VoidRuleVersionRequest request, AccountingRuleBundleVer ver) {
        if (!ver.getMakerId().equals(request.getMakerId())) {
            throw new FailException(LedgerErrorCode.RULE_SUBMITTER_NOT_MAKER);
        }
    }
}
