package com.paytech.ledger.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.api.response.AccountBalanceListResponseBody;
import com.paytech.ledger.api.response.TrialBalanceDetailResponseBody;
import com.paytech.ledger.api.response.TrialBalanceRunResponseBody;

import jakarta.validation.constraints.Pattern;

/**
 * 试算平衡（内部能力 Trial Balance & Period Close）
 * <li>按账簿、会计期间与分录序号水位汇总可过账科目的期初、本期借贷与期末，校验期末借贷合计并检测总账异常。</li>
 * <li>Finance 内部接口：发起试算的操作人取自 Operations BFF 从会话填入的 X-Gateway-Actor-Subject / X-Gateway-Session-Id Header，
 * 只记操作日志、不落库；仅经 Operations BFF 转发，不得直接对外发布。</li>
 */
@HttpExchange(url = "/ledger-books/v1", accept = "application/json")
public interface TrialBalanceApi {

    /**
     * 执行一次试算
     *
     * @param actorAccountId 操作人 Account ID（X-Gateway-Actor-Subject），正十进制整数字符串；缺失或格式错误时 400
     * @param sessionId 操作人会话 ID（X-Gateway-Session-Id）；缺失时 400
     * @param ledgerBookId 账簿主键
     * @param periodId 会计期间主键，必须属于账簿主体且不是 FUTURE
     * @return 201 与试算摘要；账簿/期间不存在、期间不属于账簿主体或为 FUTURE 时 400；同一账簿期间并发试算时 409
     */
    @PostExchange("/{ledgerBookId}/periods/{periodId}/trial-balances")
    ResponseEntity<TrialBalanceRunResponseBody> runTrialBalance(
        @RequestHeader("X-Gateway-Actor-Subject") @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
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
     * @return 摘要、末级试算行、查询时汇总的级次汇总行与异常；不存在时 400
     */
    @GetExchange("/{ledgerBookId}/periods/{periodId}/trial-balances/{runNo}")
    TrialBalanceDetailResponseBody getTrialBalance(
        @PathVariable("ledgerBookId") @Pattern(regexp = "[1-9][0-9]{0,18}") String ledgerBookId,
        @PathVariable("periodId") @Pattern(regexp = "[1-9][0-9]{0,18}") String periodId,
        @PathVariable("runNo") @Pattern(regexp = "[1-9][0-9]{0,8}") String runNo);

    /**
     * 实时查询账簿某期间的科目余额（末级 + 级次汇总）
     *
     * @param ledgerBookId 账簿主键
     * @param periodId 会计期间主键，必须属于账簿主体
     * @return 有发生额或余额的末级科目及其各级上级，按科目编号排序；任一汇总科目等于其直接下级之和；账簿或期间不存在、期间不属于账簿主体时 400
     */
    @GetExchange("/{ledgerBookId}/periods/{periodId}/account-balances")
    AccountBalanceListResponseBody listAccountBalances(
        @PathVariable("ledgerBookId") @Pattern(regexp = "[1-9][0-9]{0,18}") String ledgerBookId,
        @PathVariable("periodId") @Pattern(regexp = "[1-9][0-9]{0,18}") String periodId);
}
