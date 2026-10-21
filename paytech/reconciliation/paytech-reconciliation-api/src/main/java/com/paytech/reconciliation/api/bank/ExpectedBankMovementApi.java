package com.paytech.reconciliation.api.bank;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.api.bank.response.ExpectedBankMovementResponseBody;

import jakarta.validation.constraints.Pattern;

/**
 * 预期银行流水查询（UC-R17）
 * <li>切片 1 只有渠道账单对平生成的 CHANNEL_SETTLEMENT；本接口只读。</li>
 */
@HttpExchange(url = "/reconciliation/v1/expected-bank-movements", accept = "application/json")
public interface ExpectedBankMovementApi {

    /**
     * 分页查询预期银行流水
     *
     * @param cashAccountId 可选银行账户主键
     * @param movementType 可选来源：CHANNEL_SETTLEMENT、PAYOUT、TAX_PAYMENT
     * @param sourceId 可选来源 ID（如渠道账单主键），与 movementType 一起使用
     * @param status 可选状态：OPEN、PARTIALLY_CONFIRMED、CONFIRMED、CANCELLED、EXCEPTION
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按预期日升序、主键升序的分页结果；参数非法时 400
     */
    @GetExchange
    PagingResponseBody<ExpectedBankMovementResponseBody> listExpectedBankMovements(
        @RequestParam(value = "cashAccountId", required = false) @Pattern(regexp = "[1-9][0-9]{0,18}") String cashAccountId,
        @RequestParam(value = "movementType", required = false)
        @Pattern(regexp = "CHANNEL_SETTLEMENT|PAYOUT|TAX_PAYMENT") String movementType,
        @RequestParam(value = "sourceId", required = false) @Pattern(regexp = "[1-9][0-9]{0,18}") String sourceId,
        @RequestParam(value = "status", required = false)
        @Pattern(regexp = "OPEN|PARTIALLY_CONFIRMED|CONFIRMED|CANCELLED|EXCEPTION") String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);
}
