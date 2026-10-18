package com.paytech.ledger.api;

import jakarta.validation.constraints.Pattern;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.ledger.api.response.ChartAccountListResponseBody;
import com.paytech.ledger.api.response.LedgerAccountTotalsResponseBody;
import com.paytech.ledger.api.response.LedgerBookDetailResponseBody;

/**
 * 供 account 等内部服务读取账簿、科目与按分录聚合的 Owner 账户余额（design/04 §6、§8）
 * <li>只读，不加锁、不开事务；金额为主货币单位字符串，按账簿币种精度补齐。</li>
 * <li>内部接口：未接 Gateway 与统一身份前不得对外发布。</li>
 */
@HttpExchange(url = "/ledger/v1", accept = "application/json")
public interface LedgerReadApi {

    /**
     * 查询账簿身份与科目表
     *
     * @param bookId 账簿主键
     * @return 账簿主体类型、主体标识、币种、科目表与状态；账簿不存在时 400
     */
    @GetExchange("/books/{bookId}")
    LedgerBookDetailResponseBody getLedgerBook(@PathVariable("bookId") @Pattern(regexp = "[1-9][0-9]{0,18}") String bookId);

    /**
     * 按主键批量读取科目
     *
     * @param ids 逗号分隔的科目主键，1–200 个
     * @return 按主键升序的科目；不存在的主键不返回；数量越界或格式非法时 400
     */
    @GetExchange("/chart-accounts")
    ChartAccountListResponseBody listChartAccounts(
        @RequestParam("ids") @Pattern(regexp = "[1-9][0-9]{0,18}(,[1-9][0-9]{0,18}){0,199}") String ids);

    /**
     * 账簿内 Owner 账户（CLIENT、LOCAL_PARTNER、CHANNEL_ACCOUNT、CASH_ACCOUNT）按全部分录聚合的余额，供 account 日终核对
     *
     * @param bookId 账簿主键
     * @return 按科目代码、核算对象、余额桶排序的账户与余额；账簿不存在时 400
     */
    @GetExchange("/books/{bookId}/account-totals")
    LedgerAccountTotalsResponseBody getAccountTotals(
        @PathVariable("bookId") @Pattern(regexp = "[1-9][0-9]{0,18}") String bookId);
}
