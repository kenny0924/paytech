package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.convert;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.page;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.require;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireSame;

import org.springframework.stereotype.Component;

import com.paytech.account.api.AccountPostingApi;
import com.paytech.account.api.OwnerAccountQueryApi;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.FinanceSubledgerClient;
import com.paytech.operations.console.bff.service.model.FinanceSubledgerModels.AccountPostingResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSubledgerModels.OwnerAccountEntryResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSubledgerModels.OwnerAccountResponseBody;

import lombok.AllArgsConstructor;

/**
 * account 明细账的 HTTP 转发：每个入口只调用一次 account，不重试
 * <li>分页回执须与请求窗口一致且自洽；批次详情的主键须与请求一致并带流水列表。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class FinanceSubledgerClientImpl implements FinanceSubledgerClient {
    private final AccountPostingApi postingApi;
    private final OwnerAccountQueryApi accountApi;

    /** Owner 账户分页，核对分页回执。 */
    @Override
    public PagingResponseBody<OwnerAccountResponseBody> listOwnerAccounts(String entityType, String entityId,
        String currency, String ownerType, String ownerId, String accountCode, String balanceBucket, int page,
        int size) {
        return page(accountApi.listOwnerAccounts(entityType, entityId, currency, ownerType, ownerId, accountCode,
            balanceBucket, page, size), page, size, OwnerAccountResponseBody.class);
    }

    /** 明细流水分页，核对分页回执。 */
    @Override
    public PagingResponseBody<OwnerAccountEntryResponseBody> listOwnerAccountEntries(String accountId, int page,
        int size) {
        return page(accountApi.listOwnerAccountEntries(accountId, page, size), page, size,
            OwnerAccountEntryResponseBody.class);
    }

    /** 批次详情；批次主键须与请求一致，流水列表不可缺失。 */
    @Override
    public AccountPostingResponseBody getPosting(String postingId) {
        var posting = convert(postingApi.getPosting(postingId), AccountPostingResponseBody.class);
        requireSame(postingId, posting.getPostingId());
        require(posting.getEntries() != null);
        return posting;
    }
}
