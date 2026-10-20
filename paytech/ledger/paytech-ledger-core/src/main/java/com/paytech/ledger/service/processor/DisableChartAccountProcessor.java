package com.paytech.ledger.service.processor;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.paytech.account.api.enums.OwnerAccountType;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.dataobject.ChartAccountChange;
import com.paytech.ledger.core.dal.dataobject.ChartOfAccounts;
import com.paytech.ledger.core.enums.ChartAccountChangeType;
import com.paytech.ledger.core.enums.ChartAccountStatus;
import com.paytech.ledger.domain.ChartAccountChangeContext;
import com.paytech.ledger.domainservice.repository.AccountingRuleLineRepository;
import com.paytech.ledger.domainservice.repository.ChartAccountRepository;
import com.paytech.ledger.domainservice.repository.ChartOfAccountsRepository;
import com.paytech.ledger.domainservice.repository.CloseAccountRuleRepository;
import com.paytech.ledger.domainservice.service.LedgerSetupService;
import com.paytech.ledger.service.processor.request.ChartAccountStatusRequest;

/**
 * 停用科目并记录 DISABLE 变更
 * <li>已停用直接返回，不写变更记录。</li>
 * <li>仍被引用时 CHART_ACCOUNT_IN_USE：各规则集当前生效版本的规则行（按科目代码）、当前生效的损益结转规则（来源、本期损益或留存收益科目）、
 * account 账户类型目录（{@link OwnerAccountType}，即 StandardAccountCodes 中的 Owner 科目），以及启用中的下级科目。</li>
 * <li>停用后不能新建账户、不能被新规则版本引用；已有账户照常记账与查询。状态以原状态为条件更新。</li>
 */
@Component
public class DisableChartAccountProcessor extends AbstractChartAccountProcessor<ChartAccountStatusRequest> {
    private final LedgerSetupService ledgerSetupService;
    private final CloseAccountRuleRepository closeAccountRuleRepository;
    private final AccountingRuleLineRepository accountingRuleLineRepository;

    /** 注入公共维护依赖、本地保存服务与引用检查所需的仓储。 */
    public DisableChartAccountProcessor(Clock clock, IdGenerator idGenerator, LedgerSetupService ledgerSetupService,
        ChartAccountRepository chartAccountRepository, ChartOfAccountsRepository chartOfAccountsRepository,
        CloseAccountRuleRepository closeAccountRuleRepository,
        AccountingRuleLineRepository accountingRuleLineRepository) {
        super(clock, idGenerator, chartAccountRepository, chartOfAccountsRepository);
        this.ledgerSetupService = ledgerSetupService;
        this.closeAccountRuleRepository = closeAccountRuleRepository;
        this.accountingRuleLineRepository = accountingRuleLineRepository;
    }

    /** 请求指定的科目表代码，用于构造科目表资源锁。 */
    @Override
    protected String chartCode(ChartAccountStatusRequest request) {
        return request.getChartCode();
    }

    @Override
    protected ChartAccount processWithinLock(ChartAccountStatusRequest request) {
        ChartOfAccounts chart = getActiveChart(request.getChartCode());
        ChartAccount account = getAccount(chart, request.getChartAccountId());
        if (account.getAccountStatus() == ChartAccountStatus.INACTIVE) {
            return account;
        }
        checkNotInUse(chart, account);
        ChartAccountChange change = newChange(account, ChartAccountChangeType.DISABLE, request.getOperatorId());
        change.setAfterAccountStatus(ChartAccountStatus.INACTIVE);
        ledgerSetupService.saveChartAccountChange(new ChartAccountChangeContext(chart, account, null, change));
        return account;
    }

    /** 规则、结转规则、账户类型目录或启用中的下级仍引用该科目时拒绝。 */
    void checkNotInUse(ChartOfAccounts chart, ChartAccount account) {
        String reason = inUseReason(chart, account);
        if (reason != null) {
            throw new FailException(LedgerSetupErrorCode.CHART_ACCOUNT_IN_USE,
                "科目" + reason + "，不能停用: " + account.getAccountNo() + " " + account.getAccountCode());
        }
    }

    /** 第一个命中的引用原因；未被引用时为空。 */
    String inUseReason(ChartOfAccounts chart, ChartAccount account) {
        if (OwnerAccountType.ofCode(account.getAccountCode()) != null) {
            return "属于 account 账户类型目录";
        }
        if (accountingRuleLineRepository.existsCurrentByAccountCode(account.getAccountCode())) {
            return "被当前生效的记账规则引用";
        }
        if (isCloseRuleAccount(chart, account)) {
            return "被损益结转规则引用";
        }
        boolean activeChild = chartAccountRepository.findChildren(account.getId()).stream()
            .anyMatch(child -> child.getAccountStatus() == ChartAccountStatus.ACTIVE);
        return activeChild ? "仍有启用中的下级" : null;
    }

    /** 当前生效的结转规则把该科目作为来源、本期损益或留存收益科目。 */
    boolean isCloseRuleAccount(ChartOfAccounts chart, ChartAccount account) {
        Long id = account.getId();
        return closeAccountRuleRepository.findAllEffective(chart.getId(), now()).stream()
            .anyMatch(rule -> id.equals(rule.getSourceAccountId()) || id.equals(rule.getCurrentResultAccountId())
                || id.equals(rule.getRetainedEarningsAccountId()));
    }
}
