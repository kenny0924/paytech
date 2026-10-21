package com.paytech.reconciliation.api.statement;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.api.statement.response.ChannelStatementLineSearchItemResponseBody;

import jakarta.validation.constraints.Pattern;

/**
 * 跨渠道账单查询账单行（渠道流水页，需求 20261021 对账控制台详情页与渠道流水 §2.1）
 * <li>只读；每行带所属账单（批次号、版本、状态、结算日）与清算账户编码，页面不需要再逐行回读账单。</li>
 * <li>未指定账单状态时排除 VOIDED、REJECTED、RECEIVING 账单的行（作废、被拒与导入中的账单行不是有效渠道流水）；指定状态时只按该状态过滤。</li>
 */
@HttpExchange(url = "/reconciliation/v1/channel-statement-lines", accept = "application/json")
public interface ChannelStatementLineApi {

    /**
     * 跨账单分页查询渠道账单行
     *
     * @param clearingAccountId 可选清算账户主键
     * @param statementStatus 可选所属账单状态：RECEIVING、REJECTED、MATCHING、VOIDED、RECONCILED、CASH_CONFIRMED；缺省排除
     *                        VOIDED、REJECTED、RECEIVING
     * @param matchStatus 可选匹配状态：UNMATCHED、MATCHED、EXCEPTION
     * @param lineType 可选行类型：PAYMENT、REFUND、CHARGEBACK、FEE、ADJUSTMENT
     * @param referenceType 可选外部交易号类型（精确）
     * @param externalTransactionId 可选渠道外部交易号（精确）
     * @param transactionDateFrom 可选交易日期起（含），YYYY-MM-DD
     * @param transactionDateTo 可选交易日期止（含），YYYY-MM-DD
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50、100
     * @return 按交易日期降序（无交易日期的行在最后）、行主键降序的分页结果；参数非法时 400
     */
    @GetExchange
    PagingResponseBody<ChannelStatementLineSearchItemResponseBody> searchChannelStatementLines(
        @RequestParam(value = "clearingAccountId", required = false) @Pattern(regexp = "[1-9][0-9]{0,18}") String clearingAccountId,
        @RequestParam(value = "statementStatus", required = false)
        @Pattern(regexp = "RECEIVING|REJECTED|MATCHING|VOIDED|RECONCILED|CASH_CONFIRMED") String statementStatus,
        @RequestParam(value = "matchStatus", required = false) @Pattern(regexp = "UNMATCHED|MATCHED|EXCEPTION") String matchStatus,
        @RequestParam(value = "lineType", required = false)
        @Pattern(regexp = "PAYMENT|REFUND|CHARGEBACK|FEE|ADJUSTMENT") String lineType,
        @RequestParam(value = "referenceType", required = false) @Pattern(regexp = "[A-Z][A-Z0-9_]{0,31}") String referenceType,
        @RequestParam(value = "externalTransactionId", required = false) @Pattern(regexp = "[\\x21-\\x7E]{1,128}")
        String externalTransactionId,
        @RequestParam(value = "transactionDateFrom", required = false) @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}")
        String transactionDateFrom,
        @RequestParam(value = "transactionDateTo", required = false) @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}")
        String transactionDateTo,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);
}
