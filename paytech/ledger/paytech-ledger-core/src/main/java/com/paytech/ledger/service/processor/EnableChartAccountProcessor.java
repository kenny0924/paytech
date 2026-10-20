package com.paytech.ledger.service.processor;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.dataobject.ChartAccountChange;
import com.paytech.ledger.core.dal.dataobject.ChartOfAccounts;
import com.paytech.ledger.core.enums.ChartAccountChangeType;
import com.paytech.ledger.core.enums.ChartAccountStatus;
import com.paytech.ledger.domain.ChartAccountChangeContext;
import com.paytech.ledger.domainservice.repository.ChartAccountRepository;
import com.paytech.ledger.domainservice.repository.ChartOfAccountsRepository;
import com.paytech.ledger.domainservice.service.LedgerSetupService;
import com.paytech.ledger.service.processor.request.ChartAccountStatusRequest;

/**
 * 启用科目并记录 ENABLE 变更
 * <li>已启用直接返回，不写变更记录。</li>
 * <li>有上级时上级必须为启用状态，否则 CHART_PARENT_INVALID；状态以原状态为条件更新。</li>
 */
@Component
public class EnableChartAccountProcessor extends AbstractChartAccountProcessor<ChartAccountStatusRequest> {
    private final LedgerSetupService ledgerSetupService;

    /** 注入公共维护依赖与本地保存服务。 */
    public EnableChartAccountProcessor(Clock clock, IdGenerator idGenerator, LedgerSetupService ledgerSetupService,
        ChartAccountRepository chartAccountRepository, ChartOfAccountsRepository chartOfAccountsRepository) {
        super(clock, idGenerator, chartAccountRepository, chartOfAccountsRepository);
        this.ledgerSetupService = ledgerSetupService;
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
        if (account.getAccountStatus() == ChartAccountStatus.ACTIVE) {
            return account;
        }
        ChartAccount parent = getActiveParent(account);
        ChartAccountChange change = newChange(account, ChartAccountChangeType.ENABLE, request.getOperatorId());
        change.setAfterAccountStatus(ChartAccountStatus.ACTIVE);
        ledgerSetupService.saveChartAccountChange(new ChartAccountChangeContext(chart, account, parent, change));
        return account;
    }

    /** 上级必须启用；一级科目返回空。 */
    ChartAccount getActiveParent(ChartAccount account) {
        if (account.getParentAccountId() == null) {
            return null;
        }
        ChartAccount parent = chartAccountRepository.findById(account.getParentAccountId());
        if (parent == null || parent.getAccountStatus() != ChartAccountStatus.ACTIVE) {
            throw new FailException(LedgerSetupErrorCode.CHART_PARENT_INVALID, "上级科目已停用，请先启用上级: "
                + account.getAccountNo());
        }
        return parent;
    }
}
