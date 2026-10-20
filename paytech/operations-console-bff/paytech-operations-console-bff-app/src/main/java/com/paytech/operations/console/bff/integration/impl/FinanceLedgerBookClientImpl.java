package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.convert;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.page;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireIdentity;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireSame;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.write;

import org.springframework.stereotype.Component;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.api.LedgerBookCommandApi;
import com.paytech.ledger.api.LedgerBookQueryApi;
import com.paytech.ledger.api.TrialBalanceApi;
import com.paytech.operations.console.bff.integration.FinanceLedgerBookClient;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.AccountBalanceListResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.AccountJournalLineResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.CreateLedgerAccountRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.CreateLedgerAccountResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.CreateLedgerBookRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.CreateLedgerBookResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.JournalEntryDetailResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.LedgerAccountResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.LedgerBookResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.TrialBalanceDetailResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.TrialBalanceRunResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import lombok.AllArgsConstructor;

/**
 * ledger 账簿、账户与流水的 HTTP 转发：每个入口只调用一次 ledger，不重试
 * <li>命令把会话 Account ID 与 Session ID 填入操作人 Header；查询只读，不携带操作人。</li>
 * <li>分录详情、科目余额、发起试算与试算详情的账簿、期间等主键必须与请求一致；发起试算无请求体，操作人只供 ledger 记日志。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class FinanceLedgerBookClientImpl implements FinanceLedgerBookClient {
    private final LedgerBookQueryApi queryApi;
    private final LedgerBookCommandApi commandApi;
    private final TrialBalanceApi trialBalanceApi;

    /** 账簿分页，核对分页回执。 */
    @Override
    public PagingResponseBody<LedgerBookResponseBody> listLedgerBooks(String entityType, String entityId,
        String currency, int page, int size) {
        return page(queryApi.listLedgerBooks(entityType, entityId, currency, page, size), page, size,
            LedgerBookResponseBody.class);
    }

    /** 新建账簿；回执须带新主键。 */
    @Override
    public CreateLedgerBookResponseBody createLedgerBook(OperatorContext context, CreateLedgerBookRequestBody request) {
        var created = write(request, () -> commandApi.createLedgerBook(context.accountId(), context.sessionId(),
            convert(request, com.paytech.ledger.api.request.CreateLedgerBookRequestBody.class)).getBody(),
            CreateLedgerBookResponseBody.class);
        requireIdentity(created.getLedgerBookId());
        return created;
    }

    /** 账户分页，核对分页回执。 */
    @Override
    public PagingResponseBody<LedgerAccountResponseBody> listAccounts(String ledgerBookId, String accountNo,
        String accountCode, String ownerType, String ownerId, String balanceBucket, int page, int size) {
        return page(queryApi.listAccounts(ledgerBookId, accountNo, accountCode, ownerType, ownerId, balanceBucket, page,
            size), page, size, LedgerAccountResponseBody.class);
    }

    /** 新建账户；回执须带新主键。 */
    @Override
    public CreateLedgerAccountResponseBody createAccount(OperatorContext context, String ledgerBookId,
        CreateLedgerAccountRequestBody request) {
        var created = write(request, () -> commandApi.createAccount(context.accountId(), context.sessionId(),
            ledgerBookId, convert(request, com.paytech.ledger.api.request.CreateAccountRequestBody.class)).getBody(),
            CreateLedgerAccountResponseBody.class);
        requireIdentity(created.getAccountId());
        return created;
    }

    /** 账户流水分页，核对分页回执。 */
    @Override
    public PagingResponseBody<AccountJournalLineResponseBody> listAccountJournalLines(String ledgerBookId,
        String accountId, String postingDateFrom, String postingDateTo, int page, int size) {
        return page(queryApi.listAccountJournalLines(ledgerBookId, accountId, postingDateFrom, postingDateTo, page,
            size), page, size, AccountJournalLineResponseBody.class);
    }

    /** 分录详情；分录与账簿主键须与请求一致。 */
    @Override
    public JournalEntryDetailResponseBody getJournalEntry(String ledgerBookId, String journalEntryId) {
        var entry = convert(queryApi.getJournalEntry(ledgerBookId, journalEntryId), JournalEntryDetailResponseBody.class);
        requireSame(journalEntryId, entry.getJournalEntryId());
        requireSame(ledgerBookId, entry.getLedgerBookId());
        FinanceContractConverter.require(entry.getLines() != null);
        return entry;
    }

    /** 实时科目余额；账簿与期间须与请求一致，行列表不可缺失。 */
    @Override
    public AccountBalanceListResponseBody listAccountBalances(String ledgerBookId, String periodId) {
        var balances = convert(trialBalanceApi.listAccountBalances(ledgerBookId, periodId),
            AccountBalanceListResponseBody.class);
        requireSame(ledgerBookId, balances.getLedgerBookId());
        requireSame(periodId, balances.getPeriodId());
        FinanceContractConverter.require(balances.getLines() != null);
        return balances;
    }

    /** 试算记录分页，核对分页回执。 */
    @Override
    public PagingResponseBody<TrialBalanceRunResponseBody> listTrialBalances(String ledgerBookId, String periodId,
        int page, int size) {
        return page(trialBalanceApi.listTrialBalances(ledgerBookId, periodId, page, size), page, size,
            TrialBalanceRunResponseBody.class);
    }

    /** 发起试算，无请求体；回执账簿与期间须与请求一致并带新序号。 */
    @Override
    public TrialBalanceRunResponseBody runTrialBalance(OperatorContext context, String ledgerBookId, String periodId) {
        var run = convert(trialBalanceApi.runTrialBalance(context.accountId(), context.sessionId(), ledgerBookId,
            periodId).getBody(), TrialBalanceRunResponseBody.class);
        requireSame(ledgerBookId, run.getLedgerBookId());
        requireSame(periodId, run.getPeriodId());
        requireIdentity(run.getRunNo());
        return run;
    }

    /** 试算详情；账簿、期间与序号须与请求一致，三个列表不可缺失。 */
    @Override
    public TrialBalanceDetailResponseBody getTrialBalance(String ledgerBookId, String periodId, String runNo) {
        var detail = convert(trialBalanceApi.getTrialBalance(ledgerBookId, periodId, runNo),
            TrialBalanceDetailResponseBody.class);
        requireSame(ledgerBookId, detail.getLedgerBookId());
        requireSame(periodId, detail.getPeriodId());
        requireSame(runNo, detail.getRunNo());
        FinanceContractConverter.require(detail.getLines() != null && detail.getSummaryLines() != null
            && detail.getExceptions() != null);
        return detail;
    }
}
