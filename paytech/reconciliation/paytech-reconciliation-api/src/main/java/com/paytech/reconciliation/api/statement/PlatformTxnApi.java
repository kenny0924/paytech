package com.paytech.reconciliation.api.statement;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.api.statement.response.PlatformTxnResponseBody;

import jakarta.validation.constraints.Pattern;

/**
 * 平台流水查询（UC-R05 结果、R17）
 * <li>平台流水由消费 billing BillingFactRecorded 生成（只处理 PAYMENT 应收、REFUND 应付），只记逐笔对账进度，不记余额；本接口只读。</li>
 */
@HttpExchange(url = "/reconciliation/v1/platform-txns", accept = "application/json")
public interface PlatformTxnApi {

    /**
     * 分页查询平台流水
     *
     * @param clearingAccountId 可选清算账户主键
     * @param statementId 可选渠道账单主键：只返回该账单行匹配到的平台流水（决定 I-02，billing 按账单取明细时使用）
     * @param status 可选状态：OPEN、PARTIALLY_MATCHED、MATCHED、SETTLED、EXCEPTION
     * @param direction 可选方向：RECEIVABLE、PAYABLE
     * @param externalTransactionId 可选渠道外部交易号（精确）
     * @param businessDateFrom 可选业务发生日起（含，UTC），YYYY-MM-DD
     * @param businessDateTo 可选业务发生日止（含，UTC），YYYY-MM-DD
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按业务发生时间降序、主键降序的分页结果；参数非法时 400
     */
    @GetExchange
    PagingResponseBody<PlatformTxnResponseBody> listPlatformTxns(
        @RequestParam(value = "clearingAccountId", required = false) @Pattern(regexp = "[1-9][0-9]{0,18}") String clearingAccountId,
        @RequestParam(value = "statementId", required = false) @Pattern(regexp = "[1-9][0-9]{0,18}") String statementId,
        @RequestParam(value = "status", required = false)
        @Pattern(regexp = "OPEN|PARTIALLY_MATCHED|MATCHED|SETTLED|EXCEPTION") String status,
        @RequestParam(value = "direction", required = false) @Pattern(regexp = "RECEIVABLE|PAYABLE") String direction,
        @RequestParam(value = "externalTransactionId", required = false) @Pattern(regexp = "[\\x21-\\x7E]{1,128}")
        String externalTransactionId,
        @RequestParam(value = "businessDateFrom", required = false) @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}") String businessDateFrom,
        @RequestParam(value = "businessDateTo", required = false) @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}") String businessDateTo,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);
}
