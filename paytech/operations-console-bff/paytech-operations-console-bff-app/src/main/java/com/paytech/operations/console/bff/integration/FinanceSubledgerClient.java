package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSubledgerModels.AccountPostingResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSubledgerModels.OwnerAccountEntryResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSubledgerModels.OwnerAccountResponseBody;

/**
 * account 明细账（Owner 账户、明细流水、过账批次）的本地只读端口；明细事实归 account
 *
 * @author Claude
 */
public interface FinanceSubledgerClient {

    /**
     * 主体与币种下的 Owner 账户分页
     *
     * @param entityType 主体类型
     * @param entityId 主体标识
     * @param currency 币种
     * @param ownerType 可选核算对象类型
     * @param ownerId 可选核算对象标识
     * @param accountCode 可选科目代码
     * @param balanceBucket 可选余额桶
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return Owner 账户分页
     */
    PagingResponseBody<OwnerAccountResponseBody> listOwnerAccounts(String entityType, String entityId, String currency,
        String ownerType, String ownerId, String accountCode, String balanceBucket, int page, int size);

    /**
     * Owner 账户明细流水分页，按批次序号、行号升序
     *
     * @param accountId Owner 账户主键
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 流水分页
     */
    PagingResponseBody<OwnerAccountEntryResponseBody> listOwnerAccountEntries(String accountId, int page, int size);

    /**
     * 过账批次详情
     *
     * @param postingId 批次主键
     * @return 批次头与全部流水
     */
    AccountPostingResponseBody getPosting(String postingId);
}
