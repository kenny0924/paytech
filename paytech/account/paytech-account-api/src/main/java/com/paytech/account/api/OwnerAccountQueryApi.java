package com.paytech.account.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.account.api.response.OwnerAccountEntryResponseBody;
import com.paytech.account.api.response.OwnerAccountResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import jakarta.validation.constraints.Pattern;

/**
 * Owner 账户与明细流水查询（明细账 Owner：account）
 * <li>只读，不加锁、不开事务；金额为 Money（主货币单位字符串，按币种精度补齐），ID 与版本为十进制字符串。</li>
 * <li>分页复用公共 page/size：page 默认 1，size 默认 10 且只允许 10、20、50、100、200，非法值返回 400 INVALID_REQUEST。</li>
 * <li>内部接口：经 Operations BFF 转发给控制台；未接 Gateway 与 Owner/OE 授权前不得对外发布。</li>
 */
@HttpExchange(url = "/owner-accounts/v1", accept = "application/json")
public interface OwnerAccountQueryApi {

    /**
     * 分页查询主体与币种下的 Owner 账户及余额，按科目代码、核算对象类型、核算对象标识、余额桶排序
     *
     * @param entityType 主体类型：OPERATING_ENTITY 或 FINANCE_ENTITY
     * @param entityId 主体标识：OE 为 entityNo，Finance Entity 为十进制主键
     * @param currency 大写三字母币种
     * @param ownerType 可选核算对象类型：CLIENT、LOCAL_PARTNER 或 CASH_ACCOUNT
     * @param ownerId 可选核算对象标识
     * @param accountCode 可选科目代码
     * @param balanceBucket 可选余额桶：PENDING、AVAILABLE、LOCKED 或 NONE
     * @param accountNo 可选可读账户号，精确匹配；主体、币种仍必填
     * @param page 可选页码
     * @param size 可选每页条数
     * @return Owner 账户分页；没有匹配账户时为空页
     */
    @GetExchange
    PagingResponseBody<OwnerAccountResponseBody> listOwnerAccounts(
        @RequestParam("entityType") @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY") String entityType,
        @RequestParam("entityId") @Pattern(regexp = "[A-Z0-9]{1,19}") String entityId,
        @RequestParam("currency") @Pattern(regexp = "[A-Z]{3}") String currency,
        @RequestParam(value = "ownerType", required = false) @Pattern(regexp = "CLIENT|LOCAL_PARTNER|CASH_ACCOUNT") String ownerType,
        @RequestParam(value = "ownerId", required = false) @Pattern(regexp = "[1-9][0-9]{0,18}") String ownerId,
        @RequestParam(value = "accountCode", required = false) @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String accountCode,
        @RequestParam(value = "balanceBucket", required = false) @Pattern(regexp = "PENDING|AVAILABLE|LOCKED|NONE") String balanceBucket,
        @RequestParam(value = "accountNo", required = false) @Pattern(regexp = "[A-Z0-9][A-Z0-9-]{0,47}") String accountNo,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 分页查询 Owner 账户的明细流水，按批次序号、行号升序
     *
     * @param accountId Owner 账户主键
     * @param page 可选页码
     * @param size 可选每页条数
     * @return 流水分页；账户不存在 400 ACCOUNT_NOT_FOUND（{@code BalanceMovementErrorCode}）
     */
    @GetExchange("/{accountId}/entries")
    PagingResponseBody<OwnerAccountEntryResponseBody> listOwnerAccountEntries(
        @PathVariable("accountId") @Pattern(regexp = "[1-9][0-9]{0,18}") String accountId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);
}
