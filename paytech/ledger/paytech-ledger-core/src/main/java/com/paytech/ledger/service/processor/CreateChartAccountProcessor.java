package com.paytech.ledger.service.processor;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.paytech.foundation.id.IdGenerator;

import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.dataobject.ChartAccountChange;
import com.paytech.ledger.core.dal.dataobject.ChartOfAccounts;
import com.paytech.ledger.core.enums.ChartAccountChangeType;
import com.paytech.ledger.core.enums.ChartAccountStatus;
import com.paytech.ledger.domain.ChartAccountChangeContext;
import com.paytech.ledger.domainservice.repository.ChartAccountRepository;
import com.paytech.ledger.domainservice.repository.ChartOfAccountsRepository;
import com.paytech.ledger.domainservice.service.LedgerSetupService;
import com.paytech.ledger.domainservice.validator.chart.ChartAccountNoRule;
import com.paytech.ledger.service.processor.request.CreateChartAccountRequest;

/**
 * 在科目表下新增 ACTIVE 科目并记录 CREATE 变更
 * <li>上级须为同表、启用、类别相同的汇总科目；无上级时只能是 4 位一级科目；科目表已被采用时仍允许追加（含一级科目）。</li>
 * <li>编号符合 4-2-2-2 与类别首位；编号与代码在科目表内唯一由唯一索引兜底，冲突分别转为 CHART_ACCOUNT_NO_DUPLICATE /
 * CHART_ACCOUNT_CODE_DUPLICATE。无业务操作身份，重复提交以唯一键拒绝。</li>
 */
@Component
public class CreateChartAccountProcessor extends AbstractChartAccountProcessor<CreateChartAccountRequest> {
    private final LedgerSetupService ledgerSetupService;

    /** 注入公共维护依赖与本地保存服务。 */
    public CreateChartAccountProcessor(Clock clock, IdGenerator idGenerator, LedgerSetupService ledgerSetupService,
        ChartAccountRepository chartAccountRepository, ChartOfAccountsRepository chartOfAccountsRepository) {
        super(clock, idGenerator, chartAccountRepository, chartOfAccountsRepository);
        this.ledgerSetupService = ledgerSetupService;
    }

    /** 请求指定的科目表代码，用于构造科目表资源锁。 */
    @Override
    protected String chartCode(CreateChartAccountRequest request) {
        return request.getChartCode();
    }

    @Override
    protected ChartAccount processWithinLock(CreateChartAccountRequest request) {
        ChartOfAccounts chart = getActiveChart(request.getChartCode());
        ChartAccount account = buildAccount(chart, request);
        ChartAccount parent = getParent(chart, request.getParentAccountNo(), account);
        ChartAccountNoRule.check(request.getAccountNo(), request.getAccountClass(), request.getParentAccountNo());
        account.setParentAccountId(parent == null ? null : parent.getId());
        ChartAccountChange change = newChange(account, ChartAccountChangeType.CREATE, request.getOperatorId());
        ledgerSetupService.saveChartAccountChange(new ChartAccountChangeContext(chart, account, parent, change));
        return account;
    }

    /** 构建新的 ACTIVE 科目；上级在校验后回填。 */
    ChartAccount buildAccount(ChartOfAccounts chart, CreateChartAccountRequest request) {
        ChartAccount account = new ChartAccount();
        account.setId(idGenerator.nextId());
        account.setChartId(chart.getId());
        account.setAccountNo(request.getAccountNo());
        account.setAccountCode(request.getAccountCode());
        account.setAccountName(request.getAccountName());
        account.setAccountClass(request.getAccountClass());
        account.setNormalSide(request.getNormalSide());
        account.setPostingAllowed(request.getPostingAllowed());
        account.setAccountStatus(ChartAccountStatus.ACTIVE);
        account.setCreateTime(now());
        account.setUpdateTime(account.getCreateTime());
        return account;
    }
}
