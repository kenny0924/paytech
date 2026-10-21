package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.convert;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.page;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.require;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireIdentity;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireSame;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.write;

import org.springframework.stereotype.Component;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.ReconciliationBankClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.ReconciliationBankModels.AppendBankStatementLinesRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationBankModels.BankMatchListResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationBankModels.BankMatchResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationBankModels.BankStatementCompletionResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationBankModels.BankStatementLineResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationBankModels.BankStatementResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationBankModels.CreateBankMatchesRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationBankModels.ExpectedBankMovementResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationBankModels.StartBankStatementImportRequestBody;
import com.paytech.reconciliation.api.bank.BankMatchApi;
import com.paytech.reconciliation.api.bank.BankStatementApi;
import com.paytech.reconciliation.api.bank.ExpectedBankMovementApi;

import lombok.AllArgsConstructor;

/**
 * reconciliation 银行对账的 HTTP 转发：每个入口只调用一次 reconciliation，不重试
 * <li>对账单流水列表的元素须属于请求对账单，银行账户流水列表的元素须属于请求银行账户；命令回执主键须与请求一致。</li>
 * <li>人工多选匹配的回执条数须等于请求分配条数且同属请求流水。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class ReconciliationBankClientImpl implements ReconciliationBankClient {
    private final BankMatchApi matchApi;
    private final BankStatementApi statementApi;
    private final ExpectedBankMovementApi movementApi;

    /** 对账单分页，核对分页回执。 */
    @Override
    public PagingResponseBody<BankStatementResponseBody> listBankStatements(String cashAccountId, String status,
        int page, int size) {
        return page(statementApi.listBankStatements(cashAccountId, status, page, size), page, size,
            BankStatementResponseBody.class);
    }

    /** 对账单流水分页；元素须属于请求对账单。 */
    @Override
    public PagingResponseBody<BankStatementLineResponseBody> listBankStatementLinesOfStatement(String bankStatementId,
        int page, int size) {
        var lines = page(statementApi.listBankStatementLinesOfStatement(bankStatementId, page, size), page, size,
            BankStatementLineResponseBody.class);
        lines.items().forEach(line -> requireSame(bankStatementId, line.getBankStatementId()));
        return lines;
    }

    /** 银行账户流水分页；元素须属于请求银行账户。 */
    @Override
    public PagingResponseBody<BankStatementLineResponseBody> listBankStatementLines(String cashAccountId,
        String matchStatus, String direction, String valueDateFrom, String valueDateTo, int page, int size) {
        var lines = page(statementApi.listBankStatementLines(cashAccountId, matchStatus, direction, valueDateFrom,
            valueDateTo, page, size), page, size, BankStatementLineResponseBody.class);
        lines.items().forEach(line -> requireSame(cashAccountId, line.getCashAccountId()));
        return lines;
    }

    /** 开始导入；回执带新主键。 */
    @Override
    public BankStatementResponseBody startBankStatementImport(OperatorContext context,
        StartBankStatementImportRequestBody request) {
        var started = write(request, () -> statementApi.startBankStatementImport(context.accountId(),
            context.sessionId(), convert(request,
                com.paytech.reconciliation.api.bank.request.StartBankStatementImportRequestBody.class)).getBody(),
            BankStatementResponseBody.class);
        requireIdentity(started.getBankStatementId());
        return started;
    }

    /** 追加一段流水；回执主键一致。 */
    @Override
    public BankStatementResponseBody appendBankStatementLines(OperatorContext context, String bankStatementId,
        AppendBankStatementLinesRequestBody request) {
        var statement = write(request, () -> statementApi.appendBankStatementLines(context.accountId(),
            context.sessionId(), bankStatementId, convert(request,
                com.paytech.reconciliation.api.bank.request.AppendBankStatementLinesRequestBody.class)),
            BankStatementResponseBody.class);
        requireSame(bankStatementId, statement.getBankStatementId());
        return statement;
    }

    /** 完成导入；回执主键一致，拒绝原因集合不可缺失。 */
    @Override
    public BankStatementCompletionResponseBody completeBankStatementImport(OperatorContext context,
        String bankStatementId) {
        var completion = convert(statementApi.completeBankStatementImport(context.accountId(), context.sessionId(),
            bankStatementId), BankStatementCompletionResponseBody.class);
        requireSame(bankStatementId, completion.getBankStatementId());
        require(completion.getRejections() != null);
        return completion;
    }

    /** 预期银行流水分页，核对分页回执。 */
    @Override
    public PagingResponseBody<ExpectedBankMovementResponseBody> listExpectedBankMovements(String cashAccountId,
        String movementType, String sourceId, String status, int page, int size) {
        return page(movementApi.listExpectedBankMovements(cashAccountId, movementType, sourceId, status, page, size),
            page, size, ExpectedBankMovementResponseBody.class);
    }

    /** 银行匹配分页，核对分页回执。 */
    @Override
    public PagingResponseBody<BankMatchResponseBody> listBankMatches(String bankStatementLineId,
        String expectedMovementId, String status, int page, int size) {
        return page(matchApi.listBankMatches(bankStatementLineId, expectedMovementId, status, page, size), page, size,
            BankMatchResponseBody.class);
    }

    /** 确认建议；回执主键一致。 */
    @Override
    public BankMatchResponseBody confirmBankMatch(OperatorContext context, String bankMatchId) {
        var match = convert(matchApi.confirmBankMatch(context.accountId(), context.sessionId(), bankMatchId),
            BankMatchResponseBody.class);
        requireSame(bankMatchId, match.getBankMatchId());
        return match;
    }

    /** 拒绝建议；回执主键一致。 */
    @Override
    public BankMatchResponseBody rejectBankMatch(OperatorContext context, String bankMatchId) {
        var match = convert(matchApi.rejectBankMatch(context.accountId(), context.sessionId(), bankMatchId),
            BankMatchResponseBody.class);
        requireSame(bankMatchId, match.getBankMatchId());
        return match;
    }

    /** 人工多选匹配；回执条数等于分配条数且同属请求流水。 */
    @Override
    public BankMatchListResponseBody createBankMatches(OperatorContext context, CreateBankMatchesRequestBody request) {
        var matches = write(request, () -> matchApi.createBankMatches(context.accountId(), context.sessionId(),
            convert(request, com.paytech.reconciliation.api.bank.request.CreateBankMatchesRequestBody.class)).getBody(),
            BankMatchListResponseBody.class);
        require(matches.getItems() != null && matches.getItems().size() == request.getAllocations().size());
        matches.getItems().forEach(match -> requireSame(request.getBankStatementLineId(), match.getBankStatementLineId()));
        return matches;
    }
}
