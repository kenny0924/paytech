package com.paytech.account.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.account.api.enums.BalanceMovementErrorCode;
import com.paytech.account.domain.view.AccountEntryView;
import com.paytech.account.domainservice.service.OwnerAccountQueryService;
import com.paytech.account.service.processor.request.ListAccountEntriesRequest;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/**
 * 分页查询 Owner 账户的明细流水（{@code GET /owner-accounts/v1/{accountId}/entries}）
 * <li>账户不存在 ACCOUNT_NOT_FOUND；流水按批次序号、行号升序。只读，不开事务。</li>
 */
@Component
@AllArgsConstructor
public class ListAccountEntriesProcessor
    extends AbstractProcessor<ListAccountEntriesRequest, PagingResponseBody<AccountEntryView>> {
    private final OwnerAccountQueryService ownerAccountQueryService;

    @Override
    protected PagingResponseBody<AccountEntryView> processRequest(ListAccountEntriesRequest request) {
        if (ownerAccountQueryService.findAccount(request.getAccountId()) == null) {
            throw new FailException(BalanceMovementErrorCode.ACCOUNT_NOT_FOUND);
        }
        return ownerAccountQueryService.pageEntries(request.getAccountId(), request.getPaging());
    }
}
