package com.paytech.finance.api.accounting;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.finance.api.accounting.response.TrialBalanceDetailResponseBody;
import com.paytech.finance.api.accounting.response.TrialBalanceRunResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import jakarta.validation.constraints.Pattern;

/**
 * 试算平衡（内部能力 Trial Balance & Period Close）
 * <li>按账簿、会计期间与分录序号水位汇总可过账科目的期初、本期借贷与期末，校验期末借贷合计并检测总账异常。</li>
 * <li>Finance 内部开发接口：未接 Gateway 与授权，不得对外发布。</li>
 */
@HttpExchange(url = "/ledger-books/v1", accept = "application/json")
public interface TrialBalanceApi {

    /**
     * 执行一次试算
     *
     * @param ledgerBookId 账簿主键
     * @param periodId 会计期间主键，必须属于账簿主体且不是 FUTURE
     * @return 201 与试算摘要；账簿/期间不存在、期间不属于账簿主体或为 FUTURE 时 400；同一账簿期间并发试算时 409
     */
    @PostExchange("/{ledgerBookId}/periods/{periodId}/trial-balances")
    ResponseEntity<TrialBalanceRunResponseBody> runTrialBalance(
        @PathVariable("ledgerBookId") @Pattern(regexp = "[1-9][0-9]{0,18}") String ledgerBookId,
        @PathVariable("periodId") @Pattern(regexp = "[1-9][0-9]{0,18}") String periodId);

    /**
     * 按序号降序分页查询试算
     *
     * @param ledgerBookId 账簿主键
     * @param periodId 会计期间主键
     * @param page 可选页码
     * @param size 可选每页条数，只允许 10、20、50、100、200
     * @return 试算摘要分页；账簿或期间不存在时 400
     */
    @GetExchange("/{ledgerBookId}/periods/{periodId}/trial-balances")
    PagingResponseBody<TrialBalanceRunResponseBody> listTrialBalances(
        @PathVariable("ledgerBookId") @Pattern(regexp = "[1-9][0-9]{0,18}") String ledgerBookId,
        @PathVariable("periodId") @Pattern(regexp = "[1-9][0-9]{0,18}") String periodId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 查询单次试算详情
     *
     * @param ledgerBookId 账簿主键
     * @param periodId 会计期间主键
     * @param runNo 试算序号
     * @return 摘要、试算行与异常；不存在时 400
     */
    @GetExchange("/{ledgerBookId}/periods/{periodId}/trial-balances/{runNo}")
    TrialBalanceDetailResponseBody getTrialBalance(
        @PathVariable("ledgerBookId") @Pattern(regexp = "[1-9][0-9]{0,18}") String ledgerBookId,
        @PathVariable("periodId") @Pattern(regexp = "[1-9][0-9]{0,18}") String periodId,
        @PathVariable("runNo") @Pattern(regexp = "[1-9][0-9]{0,8}") String runNo);
}
