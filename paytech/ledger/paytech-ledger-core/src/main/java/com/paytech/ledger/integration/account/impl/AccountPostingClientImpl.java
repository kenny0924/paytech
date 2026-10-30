package com.paytech.ledger.integration.account.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.account.api.AccountPostingApi;
import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.event.AccountPostedEntryPayload;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.account.api.response.AccountPostingListResponseBody;
import com.paytech.account.api.response.AccountPostingProgressResponseBody;
import com.paytech.account.api.response.AccountPostingResponseBody;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.messaging.api.AggregateKey;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.domain.AccountPostingBatch;
import com.paytech.ledger.domain.AccountPostingPage;
import com.paytech.ledger.domain.AccountPostingProgress;
import com.paytech.ledger.integration.account.AccountPostingClient;

import lombok.AllArgsConstructor;

/**
 * 只适配 account-api 过账进度与批次 DTO；HTTP、MDC 与错误解码交给 Foundation 公共客户端
 * <li>批次 DTO 与 AccountPosted 载荷同构，转换为载荷后与消费入口走同一份校验。</li>
 */
@Component
@AllArgsConstructor
public class AccountPostingClientImpl implements AccountPostingClient {
    private final AccountPostingApi accountPostingApi;

    /** 响应必须回显主体并给出计数与流清单。 */
    @Override
    public AccountPostingProgress getProgress(LedgerBook book) {
        AccountPostingProgressResponseBody body = accountPostingApi.getPostingProgress(book.getEntityType().getCode(),
            book.getEntityId(), book.getCurrency());
        if (body == null || !book.getEntityId().equals(body.getEntityId()) || body.getAppliedFactCount() == null
            || body.getStreams() == null) {
            throw new ErrorException("account progress 响应无效: 账簿 " + book.getId());
        }
        List<AccountPostingProgress.Stream> streams = new ArrayList<>();
        for (AccountPostingProgressResponseBody.Stream stream : body.getStreams()) {
            AccountEntityType entityType = AccountEntityType.valueOf(stream.getEntityType());
            String aggregateId = AccountPostedPayload.aggregateId(entityType, stream.getEntityId(), stream.getCurrency(),
                Integer.parseInt(stream.getShardNo()));
            streams.add(new AccountPostingProgress.Stream(AggregateKey.of(AccountPostedPayload.AGGREGATE_TYPE, aggregateId),
                Long.parseLong(stream.getMaxPostingSequence()), stream.getLastPostedTime()));
        }
        Long maxApplied = body.getMaxAppliedFactId() == null ? null : Long.valueOf(body.getMaxAppliedFactId());
        return new AccountPostingProgress(Long.parseLong(body.getAppliedFactCount()), maxApplied, streams);
    }

    /** 逐批次转换为载荷并校验；空页的下一页序号为空。 */
    @Override
    public AccountPostingPage listPostings(AccountPostedPayload.StreamKey stream, long fromSequence, int limit) {
        AccountPostingListResponseBody body = accountPostingApi.listPostings(stream.getEntityType().getCode(),
            stream.getEntityId(), stream.getCurrency(), Integer.toString(stream.getShardNo()), Long.toString(fromSequence),
            Integer.toString(limit));
        if (body == null || body.getItems() == null) {
            throw new ErrorException("account postings 响应无效: " + stream.aggregateId());
        }
        List<AccountPostingBatch> batches;
        try {
            batches = body.getItems().stream().map(item -> AccountPostingBatch.of(toPayload(item))).toList();
        } catch (IllegalArgumentException invalid) {
            throw new ErrorException("account postings 批次内容非法: " + stream.aggregateId(), invalid);
        }
        Long next = body.getNextFromSequence() == null ? null : Long.valueOf(body.getNextFromSequence());
        return new AccountPostingPage(batches, next);
    }

    /** 批次 DTO 转换为 AccountPosted 载荷。 */
    AccountPostedPayload toPayload(AccountPostingResponseBody item) {
        AccountPostedPayload payload = new AccountPostedPayload();
        payload.setPostingId(toLong(item.getPostingId()));
        payload.setEntityType(item.getEntityType());
        payload.setEntityId(item.getEntityId());
        payload.setCurrency(item.getCurrency());
        payload.setShardNo(item.getShardNo() == null ? null : Integer.valueOf(item.getShardNo()));
        payload.setPostingSequence(toLong(item.getPostingSequence()));
        payload.setSourceType(item.getSourceType());
        payload.setPostedTime(item.getPostedTime());
        payload.setFactId(toLong(item.getFactId()));
        payload.setFactVersion(toLong(item.getFactVersion()));
        payload.setFactType(item.getFactType());
        payload.setSupersedesFactId(toLong(item.getSupersedesFactId()));
        payload.setFactApplyStatus(item.getFactApplyStatus());
        payload.setFinanceEntityId(toLong(item.getFinanceEntityId()));
        payload.setMovementId(toLong(item.getMovementId()));
        payload.setMovementSourceType(item.getMovementSourceType());
        payload.setMovementSourceRef(item.getMovementSourceRef());
        payload.setEntries(item.getEntries() == null ? null : item.getEntries().stream().map(this::toEntry).toList());
        return payload;
    }

    /** 流水 DTO 转换为载荷流水；金额取主货币单位数值。 */
    AccountPostedEntryPayload toEntry(AccountPostingResponseBody.Entry entry) {
        AccountPostedEntryPayload payload = new AccountPostedEntryPayload();
        payload.setEntryId(toLong(entry.getEntryId()));
        payload.setLineNo(entry.getLineNo() == null ? null : Integer.valueOf(entry.getLineNo()));
        payload.setAccountId(toLong(entry.getAccountId()));
        payload.setAccountCode(entry.getAccountCode());
        payload.setOwnerType(entry.getOwnerType());
        payload.setOwnerId(entry.getOwnerId());
        payload.setBalanceBucket(entry.getBalanceBucket());
        payload.setEntrySide(entry.getEntrySide());
        payload.setAmount(entry.getAmount() == null ? null : entry.getAmount().getAmount());
        payload.setFactLineNo(entry.getFactLineNo() == null ? null : Integer.valueOf(entry.getFactLineNo()));
        payload.setReversesEntryId(toLong(entry.getReversesEntryId()));
        return payload;
    }

    /** 可空十进制字符串转 Long。 */
    static Long toLong(String text) {
        return text == null ? null : Long.valueOf(text);
    }
}
