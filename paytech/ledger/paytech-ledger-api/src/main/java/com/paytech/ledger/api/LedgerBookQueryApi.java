package com.paytech.ledger.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.api.response.AccountJournalLineResponseBody;
import com.paytech.ledger.api.response.AccountResponseBody;
import com.paytech.ledger.api.response.JournalEntryDetailResponseBody;
import com.paytech.ledger.api.response.LedgerBookResponseBody;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * FIN-I05：账本流水与账户查询（内部能力 Ledger & Account Balance）
 * <li>只读，不加锁、不开事务；金额为主货币单位字符串，按账簿币种精度补齐。</li>
 * <li>分页复用公共 page/size：page 默认 1，size 默认 10 且只允许 10、20、50、100、200，非法值返回 400 INVALID_REQUEST。</li>
 * <li>Finance 内部开发接口：未接 Gateway 与 Owner/OE 授权，不得对外发布。</li>
 */
@HttpExchange(url = "/ledger-books/v1", accept = "application/json")
public interface LedgerBookQueryApi {

    /**
     * 分页查询账簿，按主键升序
     *
     * @param entityType 可选主体类型：OPERATING_ENTITY 或 FINANCE_ENTITY
     * @param entityId 可选主体标识
     * @param currency 可选币种
     * @param page 可选页码
     * @param size 可选每页条数
     * @return 账簿分页
     */
    @GetExchange
    PagingResponseBody<LedgerBookResponseBody> listLedgerBooks(
        @RequestParam(value = "entityType", required = false) @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY") String entityType,
        @RequestParam(value = "entityId", required = false) String entityId,
        @RequestParam(value = "currency", required = false) @Pattern(regexp = "[A-Z]{3}") String currency,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 分页查询账簿内账户及余额，按科目代码、核算对象、余额桶排序
     *
     * @param ledgerBookId 账簿主键
     * @param accountNo 可选账户号，精确匹配
     * @param accountCode 可选科目代码
     * @param ownerType 可选核算对象类型，取值见 {@code LedgerOwnerType}：OPERATING_ENTITY、FINANCE_ENTITY、CHANNEL_ACCOUNT、CASH_ACCOUNT、TAX_AUTHORITY；
     *        Client / Partner 明细在 account，控制科目账户的 Owner 为账簿主体
     * @param ownerId 可选核算对象标识
     * @param balanceBucket 可选余额桶；ledger 账户一律为 NONE，保留参数仅用于兼容筛选
     * @param page 可选页码
     * @param size 可选每页条数
     * @return 账户分页；账簿不存在时 400
     */
    @GetExchange("/{ledgerBookId}/accounts")
    PagingResponseBody<AccountResponseBody> listAccounts(
        @PathVariable("ledgerBookId") @Pattern(regexp = "[1-9][0-9]{0,18}") String ledgerBookId,
        @RequestParam(value = "accountNo", required = false) @Size(max = 48) String accountNo,
        @RequestParam(value = "accountCode", required = false) String accountCode,
        @RequestParam(value = "ownerType", required = false) @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY|CHANNEL_ACCOUNT|CASH_ACCOUNT|TAX_AUTHORITY") String ownerType,
        @RequestParam(value = "ownerId", required = false) String ownerId,
        @RequestParam(value = "balanceBucket", required = false) @Pattern(regexp = "PENDING|AVAILABLE|LOCKED|NONE") String balanceBucket,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 分页查询账户流水，按分录序号、行号升序
     *
     * @param ledgerBookId 账簿主键
     * @param accountId 账户主键，必须属于该账簿
     * @param postingDateFrom 可选过账日期下限（含），ISO 日期 yyyy-MM-dd
     * @param postingDateTo 可选过账日期上限（含），不早于下限
     * @param page 可选页码
     * @param size 可选每页条数
     * @return 流水分页；每行 balanceAfter 按全量历史累计；账簿不存在或账户不属于账簿时 400
     */
    @GetExchange("/{ledgerBookId}/accounts/{accountId}/journal-lines")
    PagingResponseBody<AccountJournalLineResponseBody> listAccountJournalLines(
        @PathVariable("ledgerBookId") @Pattern(regexp = "[1-9][0-9]{0,18}") String ledgerBookId,
        @PathVariable("accountId") @Pattern(regexp = "[1-9][0-9]{0,18}") String accountId,
        @RequestParam(value = "postingDateFrom", required = false) @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}") String postingDateFrom,
        @RequestParam(value = "postingDateTo", required = false) @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}") String postingDateTo,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 查询分录详情
     *
     * @param ledgerBookId 账簿主键
     * @param journalEntryId 分录主键，必须属于该账簿
     * @return 分录头、来源与全部分录行；账簿不存在或分录不属于账簿时 400
     */
    @GetExchange("/{ledgerBookId}/journal-entries/{journalEntryId}")
    JournalEntryDetailResponseBody getJournalEntry(
        @PathVariable("ledgerBookId") @Pattern(regexp = "[1-9][0-9]{0,18}") String ledgerBookId,
        @PathVariable("journalEntryId") @Pattern(regexp = "[1-9][0-9]{0,18}") String journalEntryId);
}
