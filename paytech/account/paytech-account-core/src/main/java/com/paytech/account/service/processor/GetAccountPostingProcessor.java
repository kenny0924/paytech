package com.paytech.account.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.account.api.enums.AccountPostingErrorCode;
import com.paytech.account.domain.view.AccountPostingView;
import com.paytech.account.domainservice.service.AccountPostingQueryService;
import com.paytech.account.service.processor.request.GetAccountPostingRequest;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 入账批次详情（{@code GET /account-postings/v1/{postingId}}，控制台批次抽屉）
 * <li>批次头、来源与按行号升序的全部流水；批次不存在 POSTING_NOT_FOUND。只读，不开事务。</li>
 */
@Component
@AllArgsConstructor
public class GetAccountPostingProcessor extends AbstractProcessor<GetAccountPostingRequest, AccountPostingView> {
    private final AccountPostingQueryService accountPostingQueryService;

    @Override
    protected AccountPostingView processRequest(GetAccountPostingRequest request) {
        AccountPostingView view = accountPostingQueryService.findPosting(request.getPostingId());
        if (view == null) {
            throw new FailException(AccountPostingErrorCode.POSTING_NOT_FOUND);
        }
        return view;
    }
}
