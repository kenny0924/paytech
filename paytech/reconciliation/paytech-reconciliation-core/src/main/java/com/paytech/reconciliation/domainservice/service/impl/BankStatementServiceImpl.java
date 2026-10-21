package com.paytech.reconciliation.domainservice.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.reconciliation.api.enums.BankStatementStatus;
import com.paytech.reconciliation.core.dal.dataobject.BankStatement;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.domainservice.repository.BankStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.BankStatementRepository;
import com.paytech.reconciliation.domainservice.service.BankStatementService;
import com.paytech.reconciliation.service.handler.BankMatchSuggestHandler;
import lombok.AllArgsConstructor;

/** 银行对账单分段导入；事务传播为 REQUIRED。 */
@Service
@AllArgsConstructor
public class BankStatementServiceImpl implements BankStatementService {
    private final TransactionTemplate transactionTemplate;
    private final BankStatementRepository bankStatementRepository;
    private final CompensationEventRegistrar compensationEventRegistrar;
    private final BankStatementLineRepository bankStatementLineRepository;

    /** 插入对账单头。 */
    @Override
    public void start(BankStatement statement) {
        transactionTemplate.executeWithoutResult(status -> bankStatementRepository.insert(statement));
    }

    /** 重置已拒绝的对账单头。 */
    @Override
    public void restart(BankStatement statement, BankStatement replacement) {
        transactionTemplate.executeWithoutResult(status -> bankStatementRepository.restart(statement, replacement));
    }

    /** 插入新流水并更新进度；没有新行也没有新记下的重复行时不写。 */
    @Override
    public void appendLines(BankStatement statement, List<BankStatementLine> lines, int duplicateCount,
        String pendingReasons) {
        if (lines.isEmpty() && duplicateCount == 0) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            bankStatementLineRepository.insertBatch(lines);
            bankStatementRepository.updateImportProgress(statement, statement.getImportedLineCount() + lines.size(),
                statement.getDuplicateLineCount() + duplicateCount, pendingReasons);
        });
    }

    /** IMPORTED 并登记匹配建议任务。 */
    @Override
    public void accept(BankStatement statement) {
        transactionTemplate.executeWithoutResult(status -> {
            bankStatementRepository.changeStatus(statement, BankStatementStatus.IMPORTED, null);
            compensationEventRegistrar.register(BankMatchSuggestHandler.buildStatementEvent(statement.getCashAccountId(),
                statement.getId()));
        });
    }

    /** REJECTED 并删除已接收流水。 */
    @Override
    public void reject(BankStatement statement, String rejectReason) {
        transactionTemplate.executeWithoutResult(status -> {
            bankStatementLineRepository.deleteByStatement(statement.getId());
            bankStatementRepository.changeStatus(statement, BankStatementStatus.REJECTED, rejectReason);
        });
    }
}
