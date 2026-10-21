package com.paytech.reconciliation.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.stereotype.Component;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.reconciliation.api.enums.BankStatementStatus;
import com.paytech.reconciliation.api.enums.CashAccountStatus;
import com.paytech.reconciliation.core.dal.dataobject.BankStatement;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.domain.Amounts;
import com.paytech.reconciliation.domainservice.repository.BankStatementRepository;
import com.paytech.reconciliation.domainservice.repository.CashAccountRepository;
import com.paytech.reconciliation.domainservice.service.BankStatementService;
import com.paytech.reconciliation.service.processor.request.StartBankStatementImportRequest;
import lombok.AllArgsConstructor;

/**
 * 开始导入银行对账单（UC-R11，#18，technique D-01、§2.5）：建 RECEIVING 对账单头
 * <li>银行账户须存在且 ACTIVE；文件头末四位须等于所选账户末四位（不区分大小写）；币种须等于账户币种；期间不得倒置；余额不超过币种精度。</li>
 * <li>同一银行账户 + 对账单编号：不存在则新建；已 REJECTED 的复用原行重置为 RECEIVING（更正重传，换新头、文件信息、声明行数与上传人，
 * 清空计数与拒绝原因，其流水已在拒绝时删除；2026-10-20 主会话决定）；RECEIVING 或 IMPORTED 的拒绝。并发开始同一编号由
 * uk(cash_account_id, statement_ref) 或版本条件兜底为 409，不加锁。</li>
 */
@Component
@AllArgsConstructor
public class StartBankStatementImportProcessor extends AbstractProcessor<StartBankStatementImportRequest, BankStatement> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final BankStatementService bankStatementService;
    private final CashAccountRepository cashAccountRepository;
    private final BankStatementRepository bankStatementRepository;

    @Override
    protected BankStatement processRequest(StartBankStatementImportRequest request) {
        // 业务验证
        CashAccount account = loadAccount(request);
        checkHeader(request);
        BankStatement existing = bankStatementRepository.findByRef(account.getId(), request.getStatementRef());
        if (existing != null && existing.getStatus() != BankStatementStatus.REJECTED) {
            throw new BizCheckFailException("同一银行账户的对账单编号已存在: 银行账户 " + account.getId() + ", 编号 "
                + request.getStatementRef() + ", 状态 " + existing.getStatus().getCode());
        }

        // 本地保存：新建，或已拒绝的同编号更正重传
        BankStatement statement = build(request);
        if (existing == null) {
            bankStatementService.start(statement);
            return statement;
        }
        bankStatementService.restart(existing, statement);
        return existing;
    }

    /** 银行账户存在、ACTIVE，末四位与币种一致。 */
    CashAccount loadAccount(StartBankStatementImportRequest request) {
        CashAccount account = cashAccountRepository.findById(request.getCashAccountId());
        if (account == null || account.getStatus() != CashAccountStatus.ACTIVE) {
            throw new BizCheckFailException("银行账户不存在或不是 ACTIVE: " + request.getCashAccountId());
        }
        if (!account.getAccountLast4().equals(request.getBankAccountLast4().toUpperCase(Locale.ROOT))) {
            throw new BizCheckFailException("对账单的银行账号末四位与所选银行账户不一致: 银行账户 " + account.getId()
                + " 末四位 " + account.getAccountLast4() + ", 文件 " + request.getBankAccountLast4());
        }
        if (!account.getCurrency().equals(request.getCurrency())) {
            throw new BizCheckFailException("对账单币种必须等于银行账户币种: 银行账户 " + account.getId() + " 币种 "
                + account.getCurrency() + ", 对账单币种 " + request.getCurrency());
        }
        return account;
    }

    /** 期间不倒置，余额不超过币种精度。 */
    static void checkHeader(StartBankStatementImportRequest request) {
        if (request.getPeriodEnd().isBefore(request.getPeriodStart())) {
            throw new BizCheckFailException("对账单期间结束日早于开始日: " + request.getStatementRef());
        }
        String label = "对账单 " + request.getStatementRef();
        Amounts.checkPrecision(request.getCurrency(), request.getOpeningBalance(), label + " 期初余额");
        Amounts.checkPrecision(request.getCurrency(), request.getClosingBalance(), label + " 期末余额");
    }

    /** RECEIVING 对账单头。 */
    BankStatement build(StartBankStatementImportRequest request) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        BankStatement statement = new BankStatement();
        statement.setId(idGenerator.nextId());
        statement.setCashAccountId(request.getCashAccountId());
        statement.setStatementRef(request.getStatementRef());
        statement.setPeriodStart(request.getPeriodStart());
        statement.setPeriodEnd(request.getPeriodEnd());
        statement.setCurrency(request.getCurrency());
        statement.setOpeningBalance(request.getOpeningBalance());
        statement.setClosingBalance(request.getClosingBalance());
        statement.setDeclaredLineCount(request.getDeclaredLineCount());
        statement.setImportedLineCount(0);
        statement.setDuplicateLineCount(0);
        statement.setFileName(request.getFileName());
        statement.setFileContentHash(request.getFileContentHash());
        statement.setStatus(BankStatementStatus.RECEIVING);
        statement.setMakerId(request.getActorId());
        statement.setVersion(1L);
        statement.setCreateTime(now);
        statement.setUpdateTime(now);
        return statement;
    }
}
