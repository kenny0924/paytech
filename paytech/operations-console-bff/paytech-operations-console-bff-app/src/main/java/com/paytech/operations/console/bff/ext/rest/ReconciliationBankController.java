package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 银行对账的浏览器入口（块 123），全部简单转发 reconciliation BankStatementApi、ExpectedBankMovementApi、BankMatchApi
 * <li>Gateway 绑定：查询 FINANCE_RECONCILIATION_READ；对账单分段导入、确认 / 拒绝建议、人工多选匹配 FINANCE_RECONCILIATION_OPERATE。</li>
 * <li>人工多选匹配的带符号合计与剩余金额由页面实时提示，最终校验归 reconciliation。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/finance/reconciliation/v1")
public class ReconciliationBankController {
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final String DATE = "[0-9]{4}-[0-9]{2}-[0-9]{2}";

    private final ReconciliationBankClient client;

    /** 银行对账单分页。 */
    @GetMapping("/bank-statements")
    public PagingResponseBody<BankStatementResponseBody> listBankStatements(HttpServletRequest servletRequest,
        @RequestParam(value = "cashAccountId", required = false) @Pattern(regexp = ID) String cashAccountId,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "RECEIVING|IMPORTED|REJECTED") String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("cashAccountId", "status", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listBankStatements(cashAccountId, status, paging.page(), paging.size());
    }

    /** 一张对账单的流水分页。 */
    @GetMapping("/bank-statements/{bankStatementId}/lines")
    public PagingResponseBody<BankStatementLineResponseBody> listBankStatementLinesOfStatement(
        HttpServletRequest servletRequest, @PathVariable("bankStatementId") @Pattern(regexp = ID) String bankStatementId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listBankStatementLinesOfStatement(bankStatementId, paging.page(), paging.size());
    }

    /** 银行账户下的流水分页（未匹配流水按匹配状态与方向筛选）。 */
    @GetMapping("/bank-statement-lines")
    public PagingResponseBody<BankStatementLineResponseBody> listBankStatementLines(HttpServletRequest servletRequest,
        @RequestParam("cashAccountId") @Pattern(regexp = ID) String cashAccountId,
        @RequestParam(value = "matchStatus", required = false)
        @Pattern(regexp = "UNMATCHED|PARTIALLY_MATCHED|MATCHED|EXCEPTION|SUSPENDED") String matchStatus,
        @RequestParam(value = "direction", required = false) @Pattern(regexp = "CREDIT|DEBIT") String direction,
        @RequestParam(value = "valueDateFrom", required = false) @Pattern(regexp = DATE) String valueDateFrom,
        @RequestParam(value = "valueDateTo", required = false) @Pattern(regexp = DATE) String valueDateTo,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("cashAccountId", "matchStatus", "direction", "valueDateFrom",
            "valueDateTo", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listBankStatementLines(cashAccountId, matchStatus, direction, valueDateFrom, valueDateTo,
            paging.page(), paging.size());
    }

    /** 开始导入银行对账单。 */
    @PostMapping("/bank-statements")
    @ResponseStatus(HttpStatus.CREATED)
    public BankStatementResponseBody startBankStatementImport(HttpServletRequest servletRequest,
        OperatorContext context, @RequestBody @NotNull @Valid StartBankStatementImportRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.startBankStatementImport(context, body);
    }

    /** 追加一段银行流水。 */
    @PostMapping("/bank-statements/{bankStatementId}/lines")
    public BankStatementResponseBody appendBankStatementLines(HttpServletRequest servletRequest,
        OperatorContext context, @PathVariable("bankStatementId") @Pattern(regexp = ID) String bankStatementId,
        @RequestBody @NotNull @Valid AppendBankStatementLinesRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.appendBankStatementLines(context, bankStatementId, body);
    }

    /** 完成导入银行对账单：通过 IMPORTED，不通过 REJECTED 并返回逐行原因。 */
    @PostMapping("/bank-statements/{bankStatementId}/completion")
    public BankStatementCompletionResponseBody completeBankStatementImport(HttpServletRequest servletRequest,
        OperatorContext context, @PathVariable("bankStatementId") @Pattern(regexp = ID) String bankStatementId) {
        requireQueryKeys(servletRequest, Set.of());
        return client.completeBankStatementImport(context, bankStatementId);
    }

    /** 预期银行流水分页。 */
    @GetMapping("/expected-bank-movements")
    public PagingResponseBody<ExpectedBankMovementResponseBody> listExpectedBankMovements(
        HttpServletRequest servletRequest,
        @RequestParam(value = "cashAccountId", required = false) @Pattern(regexp = ID) String cashAccountId,
        @RequestParam(value = "movementType", required = false)
        @Pattern(regexp = "CHANNEL_SETTLEMENT|PAYOUT|TAX_PAYMENT") String movementType,
        @RequestParam(value = "sourceId", required = false) @Pattern(regexp = ID) String sourceId,
        @RequestParam(value = "status", required = false)
        @Pattern(regexp = "OPEN|PARTIALLY_CONFIRMED|CONFIRMED|CANCELLED|EXCEPTION") String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("cashAccountId", "movementType", "sourceId", "status", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listExpectedBankMovements(cashAccountId, movementType, sourceId, status, paging.page(),
            paging.size());
    }

    /** 银行匹配分页（按流水或预期流水）。 */
    @GetMapping("/bank-matches")
    public PagingResponseBody<BankMatchResponseBody> listBankMatches(HttpServletRequest servletRequest,
        @RequestParam(value = "bankStatementLineId", required = false) @Pattern(regexp = ID) String bankStatementLineId,
        @RequestParam(value = "expectedMovementId", required = false) @Pattern(regexp = ID) String expectedMovementId,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "PROPOSED|CONFIRMED|REJECTED") String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("bankStatementLineId", "expectedMovementId", "status", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listBankMatches(bankStatementLineId, expectedMovementId, status, paging.page(), paging.size());
    }

    /** 确认匹配建议。 */
    @PostMapping("/bank-matches/{bankMatchId}/confirmation")
    public BankMatchResponseBody confirmBankMatch(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("bankMatchId") @Pattern(regexp = ID) String bankMatchId) {
        requireQueryKeys(servletRequest, Set.of());
        return client.confirmBankMatch(context, bankMatchId);
    }

    /** 拒绝匹配建议。 */
    @PostMapping("/bank-matches/{bankMatchId}/rejection")
    public BankMatchResponseBody rejectBankMatch(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("bankMatchId") @Pattern(regexp = ID) String bankMatchId) {
        requireQueryKeys(servletRequest, Set.of());
        return client.rejectBankMatch(context, bankMatchId);
    }

    /** 人工多选匹配：一条银行流水分配给多条预期银行流水，直接 CONFIRMED。 */
    @PostMapping("/bank-matches")
    @ResponseStatus(HttpStatus.CREATED)
    public BankMatchListResponseBody createBankMatches(HttpServletRequest servletRequest, OperatorContext context,
        @RequestBody @NotNull @Valid CreateBankMatchesRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createBankMatches(context, body);
    }
}
