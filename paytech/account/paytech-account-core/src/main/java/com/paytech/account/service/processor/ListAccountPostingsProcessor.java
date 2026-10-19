package com.paytech.account.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.account.domain.view.AccountPostingView;
import com.paytech.account.domainservice.service.AccountPostingQueryService;
import com.paytech.account.domainservice.validator.EntityIdentityValidator;
import com.paytech.account.service.processor.request.ListAccountPostingsRequest;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 按入账流与起始序号读取批次（{@code GET /account-postings/v1}，ledger 追平）
 * <li>结构与 AccountPosted 载荷一致，按序号升序；流不存在或已读到末尾时返回空页。</li>
 * <li>只读，不开事务：批次写入后不再变化，分页之间不需要一致快照。</li>
 */
@Component
@AllArgsConstructor
public class ListAccountPostingsProcessor extends AbstractProcessor<ListAccountPostingsRequest, List<AccountPostingView>> {
    private final AccountPostingQueryService accountPostingQueryService;

    @Override
    protected void validate(ListAccountPostingsRequest request) {
        EntityIdentityValidator.requireConsistent(request.getEntityType(), request.getEntityId());
    }

    @Override
    protected List<AccountPostingView> processRequest(ListAccountPostingsRequest request) {
        return accountPostingQueryService.findPostings(request.getEntityType(), request.getEntityId(),
            request.getCurrency(), request.getShardNo(), request.getFromSequence(), request.getLimit());
    }
}
