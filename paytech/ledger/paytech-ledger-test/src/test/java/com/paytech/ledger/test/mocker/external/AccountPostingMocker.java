package com.paytech.ledger.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.account.api.event.AccountPostedEntryPayload;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.account.api.response.AccountPostingListResponseBody;
import com.paytech.account.api.response.AccountPostingProgressResponseBody;
import com.paytech.account.api.response.AccountPostingResponseBody;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.money.Money;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.ledger.test.probe.AccountPostedPublisher;

/**
 * account 过账进度与批次接口 Mock：按主体（类型/标识/币种）配置进度 DTO（关账步骤 3），按过账流配置批次列表（追平）；未配置的查询记为意外
 * <li>批次列表按序号升序保存，{@code listPostings} 按 fromSequence 与 limit 截取一页并给出下一页起始序号，不实现 account 的状态机。</li>
 * <li>{@link #mockCaughtUpByDefault()} 让任意主体返回“没有已应用事实、没有过账流”。</li>
 */
@TestComponent
public class AccountPostingMocker implements Clear {
    /** 故障响应的 Request ID。 */
    public static final String REQUEST_ID = "account-test";

    private final Map<String, Object> progress = new HashMap<>();
    private final Map<String, List<AccountPostedPayload>> streams = new HashMap<>();
    private final Map<String, MockHttpFault> streamFaults = new HashMap<>();
    private final List<String> calls = new ArrayList<>();
    private boolean caughtUpByDefault;
    private int unexpected;

    /** 任意未单独配置的主体返回零已应用事实、空流清单。 */
    public synchronized void mockCaughtUpByDefault() {
        caughtUpByDefault = true;
    }

    /** 配置主体的已应用事实数、最大 factId 与涉及的过账流。 */
    public synchronized void mockProgress(String entityType, String entityId, String currency, long appliedFactCount,
        Long maxAppliedFactId, StreamProgress... streamProgress) {
        AccountPostingProgressResponseBody body = new AccountPostingProgressResponseBody();
        body.setEntityType(entityType);
        body.setEntityId(entityId);
        body.setCurrency(currency);
        body.setAppliedFactCount(Long.toString(appliedFactCount));
        body.setMaxAppliedFactId(maxAppliedFactId == null ? null : maxAppliedFactId.toString());
        List<AccountPostingProgressResponseBody.Stream> items = new ArrayList<>();
        for (StreamProgress stream : streamProgress) {
            items.add(stream.toBody());
        }
        body.setStreams(items);
        progress.put(key(entityType, entityId, currency), body);
    }

    /** 配置主体进度的读取故障。 */
    public synchronized void mockProgressFailure(String entityType, String entityId, String currency,
        MockHttpFault fault) {
        progress.put(key(entityType, entityId, currency), fault);
    }

    /** 追加过账流批次（按聚合 ID 归流、按序号排序）。 */
    public synchronized void mockPostings(AccountPostedPayload... postings) {
        for (AccountPostedPayload posting : postings) {
            List<AccountPostedPayload> stream = streams.computeIfAbsent(AccountPostedPublisher.aggregateId(posting),
                ignored -> new ArrayList<>());
            stream.add(posting);
            stream.sort(Comparator.comparing(AccountPostedPayload::getPostingSequence));
        }
    }

    /** 配置过账流批次读取故障。 */
    public synchronized void mockPostingsFailure(String aggregateId, MockHttpFault fault) {
        streamFaults.put(aggregateId, fault);
    }

    /** 503 Problem。 */
    public static MockHttpFault unavailable() {
        return MockHttpFault.json(HttpStatus.SERVICE_UNAVAILABLE.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
            "Service Unavailable", "合成故障", "SERVICE_UNAVAILABLE", REQUEST_ID, "2026-10-19T00:00:00.000000Z", null));
    }

    /** 记录并返回已配置的进度或故障。 */
    public synchronized AccountPostingProgressResponseBody answerProgress(String entityType, String entityId,
        String currency) {
        String key = key(entityType, entityId, currency);
        calls.add("progress:" + key);
        Object configured = progress.get(key);
        if (configured instanceof MockHttpFault fault) {
            throw fault;
        }
        if (configured == null && caughtUpByDefault) {
            AccountPostingProgressResponseBody body = new AccountPostingProgressResponseBody();
            body.setEntityType(entityType);
            body.setEntityId(entityId);
            body.setCurrency(currency);
            body.setAppliedFactCount("0");
            body.setStreams(List.of());
            return body;
        }
        if (configured == null) {
            throw unexpected("progress " + key);
        }
        return (AccountPostingProgressResponseBody)configured;
    }

    /** 记录并按序号截取一页批次；流未配置时记为意外。 */
    public synchronized AccountPostingListResponseBody answerPostings(String aggregateId, long fromSequence, int limit) {
        calls.add("postings:" + aggregateId + "@" + fromSequence + "/" + limit);
        MockHttpFault fault = streamFaults.get(aggregateId);
        if (fault != null) {
            throw fault;
        }
        List<AccountPostedPayload> stream = streams.get(aggregateId);
        if (stream == null) {
            throw unexpected("postings " + aggregateId);
        }
        List<AccountPostedPayload> remaining = stream.stream().filter(p -> p.getPostingSequence() >= fromSequence).toList();
        List<AccountPostedPayload> page = remaining.stream().limit(limit).toList();
        AccountPostingListResponseBody body = new AccountPostingListResponseBody();
        body.setItems(page.stream().map(AccountPostingMocker::postingBody).toList());
        body.setNextFromSequence(remaining.size() > page.size()
            ? remaining.get(page.size()).getPostingSequence().toString() : null);
        return body;
    }

    /** 记录一次不在本 Mock 范围内的 account 调用。 */
    public synchronized MockHttpFault unexpected(String operation) {
        unexpected++;
        calls.add(operation);
        return MockHttpFault.json(HttpStatus.INTERNAL_SERVER_ERROR.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
            "Internal Server Error", "未配置或不应发生的 account 调用 " + operation, "INTERNAL_ERROR", REQUEST_ID,
            "2026-10-19T00:00:00.000000Z", null));
    }

    /** 调用记录。 */
    public synchronized List<String> calls() {
        return List.copyOf(calls);
    }

    /** 全局 AfterEach：不允许出现未配置的查询。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的 account 调用: " + calls);
    }

    /** 每例清除配置与调用记录。 */
    @Override
    public synchronized void clear() {
        progress.clear();
        streams.clear();
        streamFaults.clear();
        calls.clear();
        caughtUpByDefault = false;
        unexpected = 0;
    }

    /** 主体键。 */
    static String key(String entityType, String entityId, String currency) {
        return entityType + "/" + entityId + "/" + currency;
    }

    /** 载荷转换为 account 批次响应（与载荷一一对应，金额为 Money）。 */
    static AccountPostingResponseBody postingBody(AccountPostedPayload payload) {
        AccountPostingResponseBody body = new AccountPostingResponseBody();
        body.setPostingId(text(payload.getPostingId()));
        body.setEntityType(payload.getEntityType());
        body.setEntityId(payload.getEntityId());
        body.setCurrency(payload.getCurrency());
        body.setShardNo(text(payload.getShardNo()));
        body.setPostingSequence(text(payload.getPostingSequence()));
        body.setSourceType(payload.getSourceType());
        body.setPostedTime(payload.getPostedTime());
        body.setFactId(text(payload.getFactId()));
        body.setFactVersion(text(payload.getFactVersion()));
        body.setFactType(payload.getFactType());
        body.setSupersedesFactId(text(payload.getSupersedesFactId()));
        body.setFactApplyStatus(payload.getFactApplyStatus());
        body.setFinanceEntityId(text(payload.getFinanceEntityId()));
        body.setMovementId(text(payload.getMovementId()));
        body.setMovementSourceType(payload.getMovementSourceType());
        body.setMovementSourceRef(payload.getMovementSourceRef());
        body.setEntries(payload.getEntries().stream().map(entry -> entryBody(payload.getCurrency(), entry)).toList());
        return body;
    }

    /** 流水转换。 */
    static AccountPostingResponseBody.Entry entryBody(String currency, AccountPostedEntryPayload entry) {
        AccountPostingResponseBody.Entry body = new AccountPostingResponseBody.Entry();
        body.setEntryId(text(entry.getEntryId()));
        body.setLineNo(text(entry.getLineNo()));
        body.setAccountId(text(entry.getAccountId()));
        body.setAccountCode(entry.getAccountCode());
        body.setOwnerType(entry.getOwnerType());
        body.setOwnerId(entry.getOwnerId());
        body.setBalanceBucket(entry.getBalanceBucket());
        body.setEntrySide(entry.getEntrySide());
        body.setAmount(Money.of(currency, entry.getAmount()));
        body.setFactLineNo(text(entry.getFactLineNo()));
        body.setReversesEntryId(text(entry.getReversesEntryId()));
        return body;
    }

    /** 十进制文本；为空时为空。 */
    static String text(Object value) {
        return value == null ? null : value.toString();
    }

    /** 进度中的一条过账流。 */
    public record StreamProgress(String entityType, String entityId, String currency, int shardNo,
        long maxPostingSequence, Instant lastPostedTime) {

        /** 不带最后入账时间的流进度。 */
        public StreamProgress(String entityType, String entityId, String currency, int shardNo, long maxPostingSequence) {
            this(entityType, entityId, currency, shardNo, maxPostingSequence, null);
        }

        /** 转换为响应项。 */
        AccountPostingProgressResponseBody.Stream toBody() {
            AccountPostingProgressResponseBody.Stream body = new AccountPostingProgressResponseBody.Stream();
            body.setEntityType(entityType);
            body.setEntityId(entityId);
            body.setCurrency(currency);
            body.setShardNo(Integer.toString(shardNo));
            body.setMaxPostingSequence(Long.toString(maxPostingSequence));
            body.setLastPostedTime(lastPostedTime);
            return body;
        }
    }
}
