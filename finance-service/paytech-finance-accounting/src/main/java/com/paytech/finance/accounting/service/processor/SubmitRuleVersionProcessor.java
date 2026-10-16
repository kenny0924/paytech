package com.paytech.finance.accounting.service.processor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.finance.accounting.core.constant.AccountingLocks;
import com.paytech.finance.accounting.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.finance.accounting.core.dal.dataobject.AccountingRuleLine;
import com.paytech.finance.accounting.core.enums.AutoInstructionType;
import com.paytech.finance.accounting.core.enums.EntrySide;
import com.paytech.finance.accounting.core.enums.RuleVersionStatus;
import com.paytech.finance.accounting.domain.AccountingRuleVersionContext;
import com.paytech.finance.accounting.domain.view.RuleVersionView;
import com.paytech.finance.accounting.domainservice.service.AccountingRuleService;
import com.paytech.finance.accounting.service.processor.request.SubmitRuleVersionRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 提交规则版本待复核：DRAFT → PENDING_APPROVAL，此后规则行集冻结
 * <li>只有编制人可以提交；版本中每个指令类型至少一条借方与一条贷方规则行。</li>
 * <li>与同一规则集的其他写入口共用资源锁并持续等待；事务内先锁规则集再锁版本，状态以原状态条件更新。</li>
 */
@Component
@AllArgsConstructor
public class SubmitRuleVersionProcessor extends AbstractLockProcessor<SubmitRuleVersionRequest, RuleVersionView> {
    private final AccountingRuleService accountingRuleService;

    /** 规则集资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(SubmitRuleVersionRequest request) {
        return LockCommand.lock(AccountingLocks.accountingRuleBundle(request.getBundleCode()));
    }

    @Override
    protected RuleVersionView processWithinLock(SubmitRuleVersionRequest request) {
        AccountingRuleVersionContext context =
            accountingRuleService.loadVersion(request.getBundleCode(), request.getBundleVer());
        checkSubmittable(request, context.getVer());
        checkBothSides(request, context.getLines());
        accountingRuleService.submitVersion(context);
        return context.toView();
    }

    /** 只能提交 DRAFT 版本，且提交人为编制人。 */
    void checkSubmittable(SubmitRuleVersionRequest request, AccountingRuleBundleVer ver) {
        if (ver.getStatus() != RuleVersionStatus.DRAFT) {
            throw new BizCheckFailException("规则版本不是草稿: 规则集 " + request.getBundleCode() + ", 版本 "
                + request.getBundleVer() + ", 状态 " + ver.getStatus().getCode());
        }
        if (!ver.getMakerId().equals(request.getMakerId())) {
            throw new BizCheckFailException("提交人必须是编制人: 规则集 " + request.getBundleCode() + ", 版本 "
                + request.getBundleVer() + ", 编制人 " + ver.getMakerId() + ", 提交人 " + request.getMakerId());
        }
    }

    /** 每个指令类型至少一条借方与一条贷方规则行。 */
    void checkBothSides(SubmitRuleVersionRequest request, List<AccountingRuleLine> lines) {
        Map<AutoInstructionType, Set<EntrySide>> sides = lines.stream().collect(Collectors.groupingBy(
            AccountingRuleLine::getInstructionType, Collectors.mapping(AccountingRuleLine::getEntrySide, Collectors.toSet())));
        for (Map.Entry<AutoInstructionType, Set<EntrySide>> entry : sides.entrySet()) {
            if (entry.getValue().size() < EntrySide.values().length) {
                throw new BizCheckFailException("指令类型缺少借方或贷方规则行: 规则集 " + request.getBundleCode() + ", 版本 "
                    + request.getBundleVer() + ", 指令类型 " + entry.getKey().getCode());
            }
        }
    }
}
