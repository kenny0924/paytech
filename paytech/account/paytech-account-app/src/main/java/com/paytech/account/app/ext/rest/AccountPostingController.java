package com.paytech.account.app.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.account.api.AccountPostingApi;
import com.paytech.account.api.request.RebuildPostingStreamsRequestBody;
import com.paytech.account.api.response.AccountPostingListResponseBody;
import com.paytech.account.api.response.AccountPostingProgressResponseBody;
import com.paytech.account.api.response.AccountPostingResponseBody;
import com.paytech.account.api.response.PostingStreamsRebuildResponseBody;
import com.paytech.account.app.ext.rest.converter.AccountPostingConverter;
import com.paytech.account.service.processor.GetAccountPostingProcessor;
import com.paytech.account.service.processor.GetPostingProgressProcessor;
import com.paytech.account.service.processor.ListAccountPostingsProcessor;
import com.paytech.account.service.processor.RebuildPostingStreamsProcessor;
import com.paytech.account.service.processor.request.GetAccountPostingRequest;
import com.paytech.account.service.processor.request.ListAccountPostingsRequest;

import lombok.AllArgsConstructor;

/** 入账进度、批次列表与详情、按流重建的 HTTP 协议适配；每个入口委托一个 Processor。 */
@RestController
@AllArgsConstructor
public class AccountPostingController implements AccountPostingApi {
    private final AccountPostingConverter converter;
    private final GetAccountPostingProcessor getProcessor;
    private final ListAccountPostingsProcessor listProcessor;
    private final GetPostingProgressProcessor progressProcessor;
    private final RebuildPostingStreamsProcessor rebuildProcessor;

    /** 事实应用进度与各流最大序号。 */
    @Override
    public AccountPostingProgressResponseBody getPostingProgress(String entityType, String entityId, String currency) {
        return converter.toResponseBody(progressProcessor.process(converter.toProgressQuery(entityType, entityId,
            currency)));
    }

    /** 按流与起始序号读取批次。 */
    @Override
    public AccountPostingListResponseBody listPostings(String entityType, String entityId, String currency,
        String shardNo, String fromSequence, String limit) {
        ListAccountPostingsRequest request = converter.toListRequest(entityType, entityId, currency, shardNo,
            fromSequence, limit);
        return converter.toResponseBody(listProcessor.process(request), request.getLimit());
    }

    /** 批次详情。 */
    @Override
    public AccountPostingResponseBody getPosting(String postingId) {
        return converter.toResponseBody(getProcessor.process(new GetAccountPostingRequest(Long.parseLong(postingId))));
    }

    /** 按流重建主体与币种下的余额。 */
    @Override
    public PostingStreamsRebuildResponseBody rebuildPostingStreams(RebuildPostingStreamsRequestBody body) {
        return converter.toResponseBody(rebuildProcessor.process(converter.toRebuildRequest(body)));
    }
}
