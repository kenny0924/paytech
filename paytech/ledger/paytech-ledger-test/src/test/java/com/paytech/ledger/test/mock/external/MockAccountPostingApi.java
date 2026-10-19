package com.paytech.ledger.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.account.api.AccountPostingApi;
import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.account.api.request.RebuildPostingStreamsRequestBody;
import com.paytech.account.api.response.AccountPostingListResponseBody;
import com.paytech.account.api.response.AccountPostingProgressResponseBody;
import com.paytech.account.api.response.AccountPostingResponseBody;
import com.paytech.account.api.response.PostingStreamsRebuildResponseBody;
import com.paytech.ledger.test.mocker.external.AccountPostingMocker;

import lombok.AllArgsConstructor;

/** 在独立 Mock 服务继承 account 正式合同（无 Context Path）；ledger 只调用进度与批次列表，其余入口记为意外。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockAccountPostingApi implements AccountPostingApi {
    private final AccountPostingMocker mocker;

    @Override
    public AccountPostingProgressResponseBody getPostingProgress(String entityType, String entityId, String currency) {
        MockRequestId.mark(AccountPostingMocker.REQUEST_ID);
        return mocker.answerProgress(entityType, entityId, currency);
    }

    @Override
    public AccountPostingListResponseBody listPostings(String entityType, String entityId, String currency,
        String shardNo, String fromSequence, String limit) {
        MockRequestId.mark(AccountPostingMocker.REQUEST_ID);
        String aggregateId = AccountPostedPayload.aggregateId(AccountEntityType.valueOf(entityType), entityId, currency,
            Integer.parseInt(shardNo));
        return mocker.answerPostings(aggregateId, Long.parseLong(fromSequence),
            limit == null ? 200 : Integer.parseInt(limit));
    }

    @Override
    public AccountPostingResponseBody getPosting(String postingId) {
        MockRequestId.mark(AccountPostingMocker.REQUEST_ID);
        throw mocker.unexpected("getPosting");
    }

    @Override
    public PostingStreamsRebuildResponseBody rebuildPostingStreams(RebuildPostingStreamsRequestBody body) {
        MockRequestId.mark(AccountPostingMocker.REQUEST_ID);
        throw mocker.unexpected("rebuildPostingStreams");
    }
}
