package com.paytech.account.app.ext.rest.converter;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.request.RebuildPostingStreamsRequestBody;
import com.paytech.account.api.response.AccountPostingListResponseBody;
import com.paytech.account.api.response.AccountPostingProgressResponseBody;
import com.paytech.account.api.response.AccountPostingResponseBody;
import com.paytech.account.api.response.PostingStreamsRebuildResponseBody;
import com.paytech.account.core.dal.dataobject.AccountEntry;
import com.paytech.account.core.dal.dataobject.AccountPosting;
import com.paytech.account.core.dal.dataobject.AccountPostingStream;
import com.paytech.account.core.dal.dataobject.OwnerAccount;
import com.paytech.account.domain.model.StreamRebuildResult;
import com.paytech.account.domain.view.AccountPostingView;
import com.paytech.account.domain.view.PostingProgressView;
import com.paytech.account.service.processor.request.EntityCurrencyQuery;
import com.paytech.account.service.processor.request.ListAccountPostingsRequest;
import com.paytech.account.service.processor.request.RebuildPostingStreamsRequest;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.money.Money;

/** 入账批次、进度与按流重建的 Wire 转换；ID、版本、序号与行号输出为十进制字符串，金额为 Money。 */
@Component
public class AccountPostingConverter {

    /** 进度查询参数。 */
    public EntityCurrencyQuery toProgressQuery(String entityType, String entityId, String currency) {
        return new EntityCurrencyQuery(CodeEnumConverter.parse(AccountEntityType.class, entityType), entityId, currency);
    }

    /** 追平列表参数；limit 缺省 200，超过 200 为参数错误。 */
    public ListAccountPostingsRequest toListRequest(String entityType, String entityId, String currency, String shardNo,
        String fromSequence, String limit) {
        int pageLimit = limit == null ? ListAccountPostingsRequest.MAX_LIMIT : Integer.parseInt(limit);
        if (pageLimit > ListAccountPostingsRequest.MAX_LIMIT) {
            throw new InvalidParameterException("limit 必须在 1 到 " + ListAccountPostingsRequest.MAX_LIMIT + " 之间: " + limit);
        }
        return new ListAccountPostingsRequest(CodeEnumConverter.parse(AccountEntityType.class, entityType), entityId,
            currency, Integer.parseInt(shardNo), Long.parseLong(fromSequence), pageLimit);
    }

    /** 按流重建请求。 */
    public RebuildPostingStreamsRequest toRebuildRequest(RebuildPostingStreamsRequestBody body) {
        return new RebuildPostingStreamsRequest(CodeEnumConverter.parse(AccountEntityType.class, body.getEntityType()),
            body.getEntityId(), body.getCurrency(), Long.parseLong(body.getOperatorId()));
    }

    /** 进度响应。 */
    public AccountPostingProgressResponseBody toResponseBody(PostingProgressView view) {
        AccountPostingProgressResponseBody body = new AccountPostingProgressResponseBody();
        body.setEntityType(view.getEntityType().getCode());
        body.setEntityId(view.getEntityId());
        body.setCurrency(view.getCurrency());
        body.setAppliedFactCount(Long.toString(view.getAppliedFactCount()));
        body.setMaxAppliedFactId(CodeEnumConverter.text(view.getMaxAppliedFactId()));
        body.setStreams(view.getStreams().stream().map(AccountPostingConverter::stream).toList());
        return body;
    }

    /** 追平列表响应：本页满 limit 时给出下一页起始序号。 */
    public AccountPostingListResponseBody toResponseBody(List<AccountPostingView> views, int limit) {
        AccountPostingListResponseBody body = new AccountPostingListResponseBody();
        body.setItems(views.stream().map(this::toResponseBody).toList());
        body.setNextFromSequence(views.size() < limit ? null
            : Long.toString(views.getLast().getPosting().getPostingSequence() + 1));
        return body;
    }

    /** 一个批次及其全部流水。 */
    public AccountPostingResponseBody toResponseBody(AccountPostingView view) {
        AccountPosting posting = view.getPosting();
        AccountPostingResponseBody body = new AccountPostingResponseBody();
        body.setPostingId(posting.getId().toString());
        body.setEntityType(posting.getEntityType().getCode());
        body.setEntityId(posting.getEntityId());
        body.setCurrency(posting.getCurrency());
        body.setShardNo(posting.getShardNo().toString());
        body.setPostingSequence(posting.getPostingSequence().toString());
        body.setSourceType(posting.getSourceType().getCode());
        body.setPostedTime(posting.getPostedTime());
        body.setPublishedTime(posting.getPublishedTime());
        fillSource(body, view);
        body.setEntries(view.getLines().stream().map(line -> entry(posting.getCurrency(), line)).toList());
        return body;
    }

    /** 按流重建响应。 */
    public PostingStreamsRebuildResponseBody toResponseBody(StreamRebuildResult result) {
        PostingStreamsRebuildResponseBody body = new PostingStreamsRebuildResponseBody();
        body.setAccountCount(Integer.toString(result.getAccountCount()));
        body.setEntryCount(Long.toString(result.getEntryCount()));
        body.setMaxPostingSequence(Long.toString(result.getMaxPostingSequence()));
        return body;
    }

    /** 事实批次填 fact*，桶移动批次填 movement*。 */
    static void fillSource(AccountPostingResponseBody body, AccountPostingView view) {
        if (view.getAppliedFact() != null) {
            body.setFactId(CodeEnumConverter.text(view.getPosting().getFactId()));
            body.setFactVersion(CodeEnumConverter.text(view.getPosting().getFactVersion()));
            body.setFactType(view.getAppliedFact().getFactType());
            body.setSupersedesFactId(CodeEnumConverter.text(view.getAppliedFact().getSupersedesFactId()));
            body.setFactApplyStatus(view.factApplyStatus().getCode());
            body.setFinanceEntityId(CodeEnumConverter.text(view.getAppliedFact().getFinanceEntityId()));
        }
        if (view.getMovement() != null) {
            body.setMovementId(view.getMovement().getId().toString());
            body.setMovementSourceType(view.getMovement().getSourceType().getCode());
            body.setMovementSourceRef(view.getMovement().getSourceRef());
        }
    }

    /** 一条流水。 */
    static AccountPostingResponseBody.Entry entry(String currency, AccountPostingView.Line line) {
        AccountEntry entry = line.getEntry();
        OwnerAccount account = line.getAccount();
        AccountPostingResponseBody.Entry body = new AccountPostingResponseBody.Entry();
        body.setEntryId(entry.getId().toString());
        body.setLineNo(entry.getLineNo().toString());
        body.setAccountId(account.getId().toString());
        body.setAccountNo(account.getAccountNo());
        body.setAccountCode(account.getAccountCode());
        body.setOwnerType(account.getOwnerType().getCode());
        body.setOwnerId(account.getOwnerId());
        body.setBalanceBucket(account.getBalanceBucket().getCode());
        body.setEntrySide(entry.getEntrySide().getCode());
        body.setAmount(Money.of(currency, entry.getAmount()));
        body.setFactLineNo(CodeEnumConverter.text(entry.getFactLineNo()));
        body.setReversesEntryId(CodeEnumConverter.text(entry.getReversesEntryId()));
        return body;
    }

    /** 一条流的进度。 */
    static AccountPostingProgressResponseBody.Stream stream(AccountPostingStream stream) {
        AccountPostingProgressResponseBody.Stream body = new AccountPostingProgressResponseBody.Stream();
        body.setEntityType(stream.getEntityType().getCode());
        body.setEntityId(stream.getEntityId());
        body.setCurrency(stream.getCurrency());
        body.setShardNo(stream.getShardNo().toString());
        body.setMaxPostingSequence(stream.getLastSequence().toString());
        body.setLastPostedTime(stream.getLastPostedTime());
        return body;
    }
}
