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
import com.paytech.operations.console.bff.integration.ReconciliationStatementClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.ReconciliationStatementModels.AppendChannelStatementLinesRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationStatementModels.ChannelStatementCompletionResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationStatementModels.ChannelStatementDetailResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationStatementModels.ChannelStatementLineResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationStatementModels.ChannelStatementLineSearchItemResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationStatementModels.ChannelStatementResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationStatementModels.PlatformTxnResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationStatementModels.StartChannelStatementImportRequestBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 渠道账单与平台流水的浏览器入口（块 123），全部简单转发 reconciliation ChannelStatementApi、PlatformTxnApi
 * <li>渠道流水页的跨账单账单行查询（块 126）同样只读，绑定 FINANCE_RECONCILIATION_READ；每页上限 100 由 reconciliation 校验。</li>
 * <li>Gateway 绑定：查询 FINANCE_RECONCILIATION_READ；开始导入、追加行、完成导入、作废 FINANCE_RECONCILIATION_OPERATE。</li>
 * <li>分段导入（D-01）：浏览器本地解析 CSV，每段 ≤ 100 行；追加行与开始导入 Body 上限 65536 字节，与 Gateway 路由上限一致。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/finance/reconciliation/v1")
public class ReconciliationStatementController {
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final String DATE = "[0-9]{4}-[0-9]{2}-[0-9]{2}";
    private static final String REFERENCE = "[\\x21-\\x7E]{1,128}";

    private final ReconciliationStatementClient client;

    /** 渠道账单分页。 */
    @GetMapping("/channel-statements")
    public PagingResponseBody<ChannelStatementResponseBody> listChannelStatements(HttpServletRequest servletRequest,
        @RequestParam(value = "clearingAccountId", required = false) @Pattern(regexp = ID) String clearingAccountId,
        @RequestParam(value = "channelBatchRef", required = false) @Pattern(regexp = REFERENCE) String channelBatchRef,
        @RequestParam(value = "status", required = false)
        @Pattern(regexp = "RECEIVING|REJECTED|MATCHING|VOIDED|RECONCILED|CASH_CONFIRMED") String status,
        @RequestParam(value = "settlementDateFrom", required = false) @Pattern(regexp = DATE) String settlementDateFrom,
        @RequestParam(value = "settlementDateTo", required = false) @Pattern(regexp = DATE) String settlementDateTo,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("clearingAccountId", "channelBatchRef", "status", "settlementDateFrom",
            "settlementDateTo", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listChannelStatements(clearingAccountId, channelBatchRef, status, settlementDateFrom,
            settlementDateTo, paging.page(), paging.size());
    }

    /** 渠道账单详情与对平门禁。 */
    @GetMapping("/channel-statements/{statementId}")
    public ChannelStatementDetailResponseBody getChannelStatement(HttpServletRequest servletRequest,
        @PathVariable("statementId") @Pattern(regexp = ID) String statementId) {
        requireQueryKeys(servletRequest, Set.of());
        return client.getChannelStatement(statementId);
    }

    /** 渠道账单行分页。 */
    @GetMapping("/channel-statements/{statementId}/lines")
    public PagingResponseBody<ChannelStatementLineResponseBody> listChannelStatementLines(
        HttpServletRequest servletRequest, @PathVariable("statementId") @Pattern(regexp = ID) String statementId,
        @RequestParam(value = "matchStatus", required = false) @Pattern(regexp = "UNMATCHED|MATCHED|EXCEPTION") String matchStatus,
        @RequestParam(value = "lineType", required = false)
        @Pattern(regexp = "PAYMENT|REFUND|CHARGEBACK|FEE|ADJUSTMENT") String lineType,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("matchStatus", "lineType", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listChannelStatementLines(statementId, matchStatus, lineType, paging.page(), paging.size());
    }

    /** 跨账单查询渠道账单行（渠道流水页）。 */
    @GetMapping("/channel-statement-lines")
    public PagingResponseBody<ChannelStatementLineSearchItemResponseBody> searchChannelStatementLines(
        HttpServletRequest servletRequest,
        @RequestParam(value = "clearingAccountId", required = false) @Pattern(regexp = ID) String clearingAccountId,
        @RequestParam(value = "statementStatus", required = false)
        @Pattern(regexp = "RECEIVING|REJECTED|MATCHING|VOIDED|RECONCILED|CASH_CONFIRMED") String statementStatus,
        @RequestParam(value = "matchStatus", required = false) @Pattern(regexp = "UNMATCHED|MATCHED|EXCEPTION") String matchStatus,
        @RequestParam(value = "lineType", required = false)
        @Pattern(regexp = "PAYMENT|REFUND|CHARGEBACK|FEE|ADJUSTMENT") String lineType,
        @RequestParam(value = "referenceType", required = false) @Pattern(regexp = "[A-Z][A-Z0-9_]{0,31}") String referenceType,
        @RequestParam(value = "externalTransactionId", required = false) @Pattern(regexp = REFERENCE)
        String externalTransactionId,
        @RequestParam(value = "transactionDateFrom", required = false) @Pattern(regexp = DATE) String transactionDateFrom,
        @RequestParam(value = "transactionDateTo", required = false) @Pattern(regexp = DATE) String transactionDateTo,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("clearingAccountId", "statementStatus", "matchStatus", "lineType",
            "referenceType", "externalTransactionId", "transactionDateFrom", "transactionDateTo", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.searchChannelStatementLines(clearingAccountId, statementStatus, matchStatus, lineType,
            referenceType, externalTransactionId, transactionDateFrom, transactionDateTo, paging.page(), paging.size());
    }

    /** 开始导入渠道账单。 */
    @PostMapping("/channel-statements")
    @ResponseStatus(HttpStatus.CREATED)
    public ChannelStatementResponseBody startChannelStatementImport(HttpServletRequest servletRequest,
        OperatorContext context, @RequestBody @NotNull @Valid StartChannelStatementImportRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.startChannelStatementImport(context, body);
    }

    /** 追加一段渠道账单行。 */
    @PostMapping("/channel-statements/{statementId}/lines")
    public ChannelStatementResponseBody appendChannelStatementLines(HttpServletRequest servletRequest,
        OperatorContext context, @PathVariable("statementId") @Pattern(regexp = ID) String statementId,
        @RequestBody @NotNull @Valid AppendChannelStatementLinesRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.appendChannelStatementLines(context, statementId, body);
    }

    /** 完成导入渠道账单：通过进入 MATCHING，不通过 REJECTED 并返回逐行原因。 */
    @PostMapping("/channel-statements/{statementId}/completion")
    public ChannelStatementCompletionResponseBody completeChannelStatementImport(HttpServletRequest servletRequest,
        OperatorContext context, @PathVariable("statementId") @Pattern(regexp = ID) String statementId) {
        requireQueryKeys(servletRequest, Set.of());
        return client.completeChannelStatementImport(context, statementId);
    }

    /** 作废渠道账单。 */
    @PostMapping("/channel-statements/{statementId}/void")
    public ChannelStatementResponseBody voidChannelStatement(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("statementId") @Pattern(regexp = ID) String statementId) {
        requireQueryKeys(servletRequest, Set.of());
        return client.voidChannelStatement(context, statementId);
    }

    /** 平台流水分页。 */
    @GetMapping("/platform-txns")
    public PagingResponseBody<PlatformTxnResponseBody> listPlatformTxns(HttpServletRequest servletRequest,
        @RequestParam(value = "clearingAccountId", required = false) @Pattern(regexp = ID) String clearingAccountId,
        @RequestParam(value = "statementId", required = false) @Pattern(regexp = ID) String statementId,
        @RequestParam(value = "status", required = false)
        @Pattern(regexp = "OPEN|PARTIALLY_MATCHED|MATCHED|SETTLED|EXCEPTION") String status,
        @RequestParam(value = "direction", required = false) @Pattern(regexp = "RECEIVABLE|PAYABLE") String direction,
        @RequestParam(value = "externalTransactionId", required = false) @Pattern(regexp = REFERENCE)
        String externalTransactionId,
        @RequestParam(value = "businessDateFrom", required = false) @Pattern(regexp = DATE) String businessDateFrom,
        @RequestParam(value = "businessDateTo", required = false) @Pattern(regexp = DATE) String businessDateTo,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("clearingAccountId", "statementId", "status", "direction",
            "externalTransactionId",
            "businessDateFrom", "businessDateTo", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listPlatformTxns(clearingAccountId, statementId, status, direction, externalTransactionId,
            businessDateFrom,
            businessDateTo, paging.page(), paging.size());
    }
}
