package com.paytech.payout.api.balance;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.payout.api.balance.response.BalanceExplanationResponseBody;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Owner 余额解释（technique 8.1 P12，调用方 Client BFF、Operations BFF）
 * <li>只读：事务外读取 account 的 Owner 余额列表与本库收款账户，实时判定阻断原因，不缓存。</li>
 * <li>本期阻断原因只有 NO_AVAILABLE_BALANCE、NO_USABLE_ACCOUNT、ACCOUNT_COOLING、RECOVERY_OUTSTANDING（B9）。</li>
 */
@HttpExchange(url = "/payout/v1/balance-explanations", accept = "application/json")
public interface BalanceExplanationApi {

    /**
     * 查询 Owner 各 OE × 币种的余额与当前不能提款的原因
     *
     * @param ownerType Owner 类型：CLIENT、LOCAL_PARTNER
     * @param ownerId Owner 标识
     * @return 余额位置与各币种可用账户；Owner 在 account 无账户时 positions 为空；account 不可用时 503
     */
    @GetExchange
    BalanceExplanationResponseBody explainBalance(
        @RequestParam("ownerType") @NotBlank @Pattern(regexp = "CLIENT|LOCAL_PARTNER") String ownerType,
        @RequestParam("ownerId") @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String ownerId);
}
