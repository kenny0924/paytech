package com.paytech.account.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.account.core.dal.dataobject.OwnerAccount;
import com.paytech.account.domainservice.service.OwnerAccountQueryService;
import com.paytech.account.domainservice.validator.EntityIdentityValidator;
import com.paytech.account.service.processor.request.ListOwnerAccountsRequest;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/**
 * 分页查询主体与币种下的 Owner 账户（{@code GET /owner-accounts/v1}，控制台明细账查询）
 * <li>没有匹配账户时为空页；只读，不开事务。</li>
 */
@Component
@AllArgsConstructor
public class ListOwnerAccountsProcessor extends AbstractProcessor<ListOwnerAccountsRequest, PagingResponseBody<OwnerAccount>> {
    private final OwnerAccountQueryService ownerAccountQueryService;

    @Override
    protected void validate(ListOwnerAccountsRequest request) {
        EntityIdentityValidator.requireConsistent(request.getEntityType(), request.getEntityId());
    }

    @Override
    protected PagingResponseBody<OwnerAccount> processRequest(ListOwnerAccountsRequest request) {
        return ownerAccountQueryService.pageAccounts(request.getEntityType(), request.getEntityId(),
            request.getCurrency(), request.getOwnerType(), request.getOwnerId(), request.getAccountCode(),
            request.getBalanceBucket(), request.getPaging());
    }
}
