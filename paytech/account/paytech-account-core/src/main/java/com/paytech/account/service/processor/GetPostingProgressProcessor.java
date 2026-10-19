package com.paytech.account.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.account.domain.view.PostingProgressView;
import com.paytech.account.domainservice.service.AccountPostingQueryService;
import com.paytech.account.domainservice.validator.EntityIdentityValidator;
import com.paytech.account.service.processor.request.EntityCurrencyQuery;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 主体与币种下的事实应用进度与入账流最大序号（{@code GET /account-postings/v1/progress}，ledger 关账两段检查）
 * <li>appliedFactCount 只计自身事件已到达的事实（排除 PENDING_SUPERSEDED）；FINANCE_ENTITY 主体时流包含引用它的事实所在的 OE 流。</li>
 * <li>只读，不开事务；没有任何流时返回空列表与 0 计数。</li>
 */
@Component
@AllArgsConstructor
public class GetPostingProgressProcessor extends AbstractProcessor<EntityCurrencyQuery, PostingProgressView> {
    private final AccountPostingQueryService accountPostingQueryService;

    @Override
    protected void validate(EntityCurrencyQuery request) {
        EntityIdentityValidator.requireConsistent(request.getEntityType(), request.getEntityId());
    }

    @Override
    protected PostingProgressView processRequest(EntityCurrencyQuery request) {
        return accountPostingQueryService.progress(request.getEntityType(), request.getEntityId(), request.getCurrency());
    }
}
