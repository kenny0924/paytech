package com.paytech.ledger.service.processor;

import java.time.Clock;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.foundation.id.IdGenerator;

import com.paytech.foundation.common.error.FailException;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.dataobject.ChartAccountChange;
import com.paytech.ledger.core.dal.dataobject.ChartOfAccounts;
import com.paytech.ledger.core.enums.ChartAccountChangeType;
import com.paytech.ledger.domain.ChartAccountChangeContext;
import com.paytech.ledger.domainservice.repository.ChartAccountRepository;
import com.paytech.ledger.domainservice.repository.ChartOfAccountsRepository;
import com.paytech.ledger.domainservice.repository.LedgerBookRepository;
import com.paytech.ledger.domainservice.service.LedgerSetupService;
import com.paytech.ledger.domainservice.validator.chart.ChartAccountNoRule;
import com.paytech.ledger.service.processor.request.UpdateChartAccountRequest;

/**
 * 修改科目名称、编号与上级并记录 UPDATE 变更
 * <li>名称任何时候可改；与当前值完全相同时直接返回、不写变更记录。</li>
 * <li>编号或上级有变化时：科目表已被任一账簿采用 → CHART_STRUCTURE_FROZEN；科目有下级 → CHART_PARENT_INVALID（先移动下级）；
 * 新上级须为同表、启用、类别相同的汇总科目，且不能是自身或自身的下级。</li>
 * <li>“未采用”检查与新建账簿共用科目表锁，检查后到提交前不会出现新采用。</li>
 */
@Component
public class UpdateChartAccountProcessor extends AbstractChartAccountProcessor<UpdateChartAccountRequest> {
    private final LedgerSetupService ledgerSetupService;
    private final LedgerBookRepository ledgerBookRepository;

    /** 注入公共维护依赖、本地保存服务与账簿仓储（判断采用冻结）。 */
    public UpdateChartAccountProcessor(Clock clock, IdGenerator idGenerator, LedgerSetupService ledgerSetupService,
        LedgerBookRepository ledgerBookRepository, ChartAccountRepository chartAccountRepository,
        ChartOfAccountsRepository chartOfAccountsRepository) {
        super(clock, idGenerator, chartAccountRepository, chartOfAccountsRepository);
        this.ledgerSetupService = ledgerSetupService;
        this.ledgerBookRepository = ledgerBookRepository;
    }

    /** 请求指定的科目表代码，用于构造科目表资源锁。 */
    @Override
    protected String chartCode(UpdateChartAccountRequest request) {
        return request.getChartCode();
    }

    @Override
    protected ChartAccount processWithinLock(UpdateChartAccountRequest request) {
        ChartOfAccounts chart = getActiveChart(request.getChartCode());
        ChartAccount account = getAccount(chart, request.getChartAccountId());
        ChartAccount parent = resolveParent(chart, account, request);
        Long parentId = parent == null ? null : parent.getId();
        boolean structureChanged = !account.getAccountNo().equals(request.getAccountNo())
            || !Objects.equals(account.getParentAccountId(), parentId);
        if (!structureChanged && account.getAccountName().equals(request.getAccountName())) {
            return account;
        }
        if (structureChanged) {
            checkStructureChangeAllowed(chart, account, parent, request.getAccountNo());
        }
        ChartAccountChange change = newChange(account, ChartAccountChangeType.UPDATE, request.getOperatorId());
        change.setAfterAccountName(request.getAccountName());
        change.setAfterAccountNo(request.getAccountNo());
        change.setAfterParentAccountId(parentId);
        ledgerSetupService.saveChartAccountChange(new ChartAccountChangeContext(chart, account, parent, change));
        return account;
    }

    /** 上级未变时沿用当前上级，不重新要求其满足新建资格；变化时按新增规则校验。 */
    ChartAccount resolveParent(ChartOfAccounts chart, ChartAccount account, UpdateChartAccountRequest request) {
        ChartAccount current = account.getParentAccountId() == null ? null
            : chartAccountRepository.findById(account.getParentAccountId());
        String currentNo = current == null ? null : current.getAccountNo();
        if (Objects.equals(currentNo, request.getParentAccountNo())) {
            return current;
        }
        return getParent(chart, request.getParentAccountNo(), account);
    }

    /** 结构变化的冻结、下级、无环与编号规则。 */
    void checkStructureChangeAllowed(ChartOfAccounts chart, ChartAccount account, ChartAccount parent,
        String accountNo) {
        if (ledgerBookRepository.existsByChart(chart.getId())) {
            throw new FailException(LedgerSetupErrorCode.CHART_STRUCTURE_FROZEN);
        }
        if (!chartAccountRepository.findChildren(account.getId()).isEmpty()) {
            throw new FailException(LedgerSetupErrorCode.CHART_PARENT_INVALID, "科目有下级，不能调整编号或上级，请先移动下级: "
                + account.getAccountNo());
        }
        if (parent != null && formsCycle(account, parent)) {
            throw new FailException(LedgerSetupErrorCode.CHART_PARENT_INVALID, "上级科目不能是自身或自身的下级: "
                + parent.getAccountNo());
        }
        ChartAccountNoRule.check(accountNo, account.getAccountClass(), parent == null ? null : parent.getAccountNo());
    }

    /** 从新上级逐级向上，遇到自身即成环；树最多 4 级。 */
    boolean formsCycle(ChartAccount account, ChartAccount parent) {
        ChartAccount node = parent;
        for (int depth = 0; node != null && depth <= ChartAccountNoRule.MAX_LEVEL; depth++) {
            if (node.getId().equals(account.getId())) {
                return true;
            }
            node = node.getParentAccountId() == null ? null : chartAccountRepository.findById(node.getParentAccountId());
        }
        return node != null;
    }
}
