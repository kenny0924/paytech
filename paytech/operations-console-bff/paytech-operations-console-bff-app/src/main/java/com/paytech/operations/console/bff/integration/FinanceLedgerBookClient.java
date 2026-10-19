package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.AccountJournalLineResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.CreateLedgerAccountRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.CreateLedgerAccountResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.CreateLedgerBookRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.CreateLedgerBookResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.JournalEntryDetailResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.LedgerAccountResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.LedgerBookResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

/**
 * ledger 账簿、账户与流水的本地端口；账务事实与余额计算归 ledger
 * <li>命令以会话 Account ID 与 Session ID 作为操作人 Header 转发；查询不携带操作人。</li>
 *
 * @author Claude
 */
public interface FinanceLedgerBookClient {

    /**
     * 账簿分页
     *
     * @param entityType 可选主体类型
     * @param entityId 可选主体标识
     * @param currency 可选币种
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 账簿分页
     */
    PagingResponseBody<LedgerBookResponseBody> listLedgerBooks(String entityType, String entityId, String currency,
        int page, int size);

    /**
     * 新建账簿
     *
     * @param context 操作人
     * @param request 页面请求
     * @return 新账簿主键
     */
    CreateLedgerBookResponseBody createLedgerBook(OperatorContext context, CreateLedgerBookRequestBody request);

    /**
     * 账簿内账户分页
     *
     * @param ledgerBookId 账簿主键
     * @param accountCode 可选科目代码
     * @param ownerType 可选核算对象类型
     * @param ownerId 可选核算对象标识
     * @param balanceBucket 可选余额桶
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 账户分页
     */
    PagingResponseBody<LedgerAccountResponseBody> listAccounts(String ledgerBookId, String accountCode,
        String ownerType, String ownerId, String balanceBucket, int page, int size);

    /**
     * 新建账户
     *
     * @param context 操作人
     * @param ledgerBookId 账簿主键
     * @param request 页面请求
     * @return 新账户主键
     */
    CreateLedgerAccountResponseBody createAccount(OperatorContext context, String ledgerBookId,
        CreateLedgerAccountRequestBody request);

    /**
     * 账户流水分页
     *
     * @param ledgerBookId 账簿主键
     * @param accountId 账户主键
     * @param postingDateFrom 可选过账日期下限
     * @param postingDateTo 可选过账日期上限
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 流水分页
     */
    PagingResponseBody<AccountJournalLineResponseBody> listAccountJournalLines(String ledgerBookId, String accountId,
        String postingDateFrom, String postingDateTo, int page, int size);

    /**
     * 分录详情
     *
     * @param ledgerBookId 账簿主键
     * @param journalEntryId 分录主键
     * @return 分录头与全部分录行
     */
    JournalEntryDetailResponseBody getJournalEntry(String ledgerBookId, String journalEntryId);
}
