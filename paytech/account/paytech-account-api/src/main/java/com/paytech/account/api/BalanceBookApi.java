package com.paytech.account.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.account.api.request.RebuildBookBalancesRequestBody;
import com.paytech.account.api.response.BalanceConsistencyResponseBody;
import com.paytech.account.api.response.BalanceRebuildResponseBody;
import com.paytech.account.api.response.BookBalancesResponseBody;

/**
 * 按 ledger 账簿读取与维护 Owner 余额（design/04 §6、§8）
 * <li>账簿身份（主体类型、主体标识、币种）经 ledger-api {@code GET /ledger/v1/books/{bookId}} 读取；ledger 不可用时返回 503，不猜测账簿。</li>
 * <li>内部接口：ledger 关账步骤 2、3 与运营重建调用；未接 Gateway 与统一身份前不得对外发布。</li>
 */
@HttpExchange(url = "/balances/v1/books", accept = "application/json")
public interface BalanceBookApi {

    /**
     * 账簿的余额消费进度与非零账户统计（关账一致性检查）
     *
     * @param bookId ledger 账簿主键
     * @return 已消费经济事实数与最大 factId、按 Owner 类型的非零余额账户数；账簿不存在 400；ledger 不可用 503
     */
    @GetExchange("/{bookId}/consistency")
    BalanceConsistencyResponseBody getBalanceConsistency(
        @PathVariable("bookId") @Pattern(regexp = "[1-9][0-9]{0,18}") String bookId);

    /**
     * 账簿内全部余额账户
     *
     * @param bookId ledger 账簿主键
     * @return 按科目代码、核算对象、余额桶排序的余额；账簿不存在 400；ledger 不可用 503
     */
    @GetExchange("/{bookId}/accounts")
    BookBalancesResponseBody listBookBalances(
        @PathVariable("bookId") @Pattern(regexp = "[1-9][0-9]{0,18}") String bookId);

    /**
     * 从 billing 重放账簿主体的全部已批准经济事实，重算余额
     * <li>事务外经 billing-api 分页取数，再以一个短事务重写本账簿的余额与已处理事实记录。</li>
     *
     * @param bookId ledger 账簿主键
     * @param body 发起人
     * @return 重放的事实数、最大 factId 与重算后的余额账户数；重建期间有新事实写入时 409；ledger 或 billing 不可用 503
     */
    @PostExchange("/{bookId}/rebuild")
    BalanceRebuildResponseBody rebuildBookBalances(
        @PathVariable("bookId") @Pattern(regexp = "[1-9][0-9]{0,18}") String bookId,
        @Valid @RequestBody RebuildBookBalancesRequestBody body);
}
