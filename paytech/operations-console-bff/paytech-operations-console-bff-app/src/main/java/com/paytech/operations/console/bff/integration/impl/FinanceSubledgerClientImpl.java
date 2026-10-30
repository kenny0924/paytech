package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.convert;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.page;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.require;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireSame;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.write;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.paytech.account.api.AccountPostingApi;
import com.paytech.account.api.OwnerAccountQueryApi;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.FinanceSubledgerClient;
import com.paytech.operations.console.bff.service.model.FinancePostingOpsModels.PostingListResponseBody;
import com.paytech.operations.console.bff.service.model.FinancePostingOpsModels.PostingSummary;
import com.paytech.operations.console.bff.service.model.FinancePostingOpsModels.RebuildPostingStreamsRequestBody;
import com.paytech.operations.console.bff.service.model.FinancePostingOpsModels.RebuildPostingStreamsResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSubledgerModels.AccountPostingResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSubledgerModels.OwnerAccountEntryResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSubledgerModels.OwnerAccountResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

/**
 * account 明细账的 HTTP 转发：每个入口只调用一次 account，不重试
 * <li>分页回执须与请求窗口一致且自洽；批次详情的主键须与请求一致并带流水列表。</li>
 * <li>明细账运维：按流批次列表的每个批次须属于请求的流、序号不小于起始序号且严格递增，裁剪为不含流水的摘要；按流重建以长耗时、
 * 外显 Owner detail 的代理调用，Account ID 作为 operatorId。</li>
 *
 * @author Claude
 */
@Component
public class FinanceSubledgerClientImpl implements FinanceSubledgerClient {
    private final AccountPostingApi postingApi;
    private final OwnerAccountQueryApi accountApi;
    private final AccountPostingApi longRunningPostingApi;

    /** 普通批次代理、Owner 账户代理与重建使用的长耗时批次代理。 */
    public FinanceSubledgerClientImpl(AccountPostingApi postingApi, OwnerAccountQueryApi accountApi,
        @Qualifier("longRunningAccountPostingApi") AccountPostingApi longRunningPostingApi) {
        this.postingApi = postingApi;
        this.accountApi = accountApi;
        this.longRunningPostingApi = longRunningPostingApi;
    }

    /** Owner 账户分页，核对分页回执。 */
    @Override
    public PagingResponseBody<OwnerAccountResponseBody> listOwnerAccounts(String entityType, String entityId,
        String currency, String ownerType, String ownerId, String accountCode, String balanceBucket, String accountNo,
        int page, int size) {
        return page(accountApi.listOwnerAccounts(entityType, entityId, currency, ownerType, ownerId, accountCode,
            balanceBucket, accountNo, page, size), page, size, OwnerAccountResponseBody.class);
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

    /** 按流批次列表：核对批次归属、序号与条数后裁剪为摘要。 */
    @Override
    public PostingListResponseBody listStreamPostings(AccountPostedPayload.StreamKey stream, long fromSequence,
        int limit) {
        var page = postingApi.listPostings(stream.getEntityType().getCode(), stream.getEntityId(), stream.getCurrency(),
            Integer.toString(stream.getShardNo()), Long.toString(fromSequence), Integer.toString(limit));
        require(page != null && page.getItems() != null && page.getItems().size() <= limit);
        List<PostingSummary> items = new ArrayList<>();
        long previous = fromSequence - 1;
        for (Object item : page.getItems()) {
            var posting = convert(item, AccountPostingResponseBody.class);
            long sequence = sequenceInStream(stream, posting);
            require(sequence > previous);
            previous = sequence;
            items.add(summary(posting));
        }
        require(page.getNextFromSequence() == null || page.getNextFromSequence().equals(Long.toString(previous + 1)));
        var result = new PostingListResponseBody();
        result.setItems(items);
        result.setNextFromSequence(page.getNextFromSequence());
        return result;
    }

    /** 按流重建；Account ID 作为 operatorId，回执四个计数不可缺失。 */
    @Override
    public RebuildPostingStreamsResponseBody rebuildPostingStreams(OperatorContext context,
        RebuildPostingStreamsRequestBody request) {
        var ownerRequest = new com.paytech.account.api.request.RebuildPostingStreamsRequestBody();
        ownerRequest.setEntityType(request.getEntityType());
        ownerRequest.setEntityId(request.getEntityId());
        ownerRequest.setCurrency(request.getCurrency());
        ownerRequest.setOperatorId(context.accountId());
        var result = write(request, () -> longRunningPostingApi.rebuildPostingStreams(ownerRequest),
            RebuildPostingStreamsResponseBody.class);
        require(result.getAccountCount() != null && result.getChangedAccountCount() != null
            && result.getEntryCount() != null && result.getMaxPostingSequence() != null);
        return result;
    }

    /** 批次必须属于请求的流，并带合法序号与流水列表。 */
    static long sequenceInStream(AccountPostedPayload.StreamKey stream, AccountPostingResponseBody posting) {
        require(stream.getEntityType().getCode().equals(posting.getEntityType())
            && stream.getEntityId().equals(posting.getEntityId()) && stream.getCurrency().equals(posting.getCurrency())
            && Integer.toString(stream.getShardNo()).equals(posting.getShardNo()) && posting.getEntries() != null
            && posting.getPostingSequence() != null && posting.getPostingSequence().matches("[1-9][0-9]{0,18}"));
        return Long.parseLong(posting.getPostingSequence());
    }

    /** 批次摘要：不含流水，只带流水条数。 */
    static PostingSummary summary(AccountPostingResponseBody posting) {
        var summary = new PostingSummary();
        summary.setPostingId(posting.getPostingId());
        summary.setPostingSequence(posting.getPostingSequence());
        summary.setSourceType(posting.getSourceType());
        summary.setFactId(posting.getFactId());
        summary.setFactType(posting.getFactType());
        summary.setFactApplyStatus(posting.getFactApplyStatus());
        summary.setPostedTime(posting.getPostedTime());
        summary.setPublishedTime(posting.getPublishedTime());
        summary.setEntryCount(Integer.toString(posting.getEntries().size()));
        return summary;
    }
}
