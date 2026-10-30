package com.paytech.ledger.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.api.fact.BillingFactLinePayload;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.billing.api.fact.BillingFactRefPayload;
import com.paytech.billing.api.response.BillingFactLineResponseBody;
import com.paytech.billing.api.response.BillingFactProgressResponseBody;
import com.paytech.billing.api.response.BillingFactRefResponseBody;
import com.paytech.billing.api.response.BillingFactResponseBody;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/**
 * billing 计费事实查询接口 Mock：按事实主键配置详情 DTO（AccountPosted 消费与追平读事实），按账簿主体配置发布进度 DTO（关账步骤 3）；
 * 未配置的查询记为意外
 * <li>详情 DTO 由测试侧把计费事实载荷转换为 billing 正式响应：头、外部引用与明细行（付方 / 收方）原样带出，批准时间同登记时间，
 * 金额写普通十进制字符串；定价侧表为空，行链接为空列表。</li>
 * <li>{@link #mockNothingPublishedByDefault()} 让任意账簿主体返回“没有未发布事实、也没有已发布事实”。</li>
 */
@TestComponent
public class BillingFactMocker implements Clear {
    /** 故障响应的 Request ID。 */
    public static final String REQUEST_ID = "billing-test";

    private final Map<String, Object> progress = new HashMap<>();
    private final Map<String, Object> facts = new HashMap<>();
    private final List<String> calls = new ArrayList<>();
    private boolean nothingPublishedByDefault;
    private int unexpected;

    /** 任意未单独配置的账簿主体返回零未发布、零已发布。 */
    public synchronized void mockNothingPublishedByDefault() {
        nothingPublishedByDefault = true;
    }

    /** 配置账簿主体的未发布数、已发布数与已发布最大 factId。 */
    public synchronized void mockProgress(String bookEntityType, String bookEntityId, String currency, long pendingCount,
        long publishedFactCount, Long maxPublishedFactId) {
        mockProgress(bookEntityType, bookEntityId, currency, pendingCount, null, publishedFactCount, maxPublishedFactId);
    }

    /** 配置账簿主体的未发布数与未发布最大 factId、已发布数与已发布最大 factId。 */
    public synchronized void mockProgress(String bookEntityType, String bookEntityId, String currency, long pendingCount,
        Long maxPendingFactId, long publishedFactCount, Long maxPublishedFactId) {
        progress.put(key(bookEntityType, bookEntityId, currency),
            progressBody(pendingCount, maxPendingFactId, publishedFactCount, maxPublishedFactId));
    }

    /** 配置账簿主体的读取故障。 */
    public synchronized void mockProgressFailure(String bookEntityType, String bookEntityId, String currency,
        MockHttpFault fault) {
        progress.put(key(bookEntityType, bookEntityId, currency), fault);
    }

    /** 配置事实详情：以已批准状态返回载荷对应的 billing 响应。 */
    public synchronized void mockFact(BillingFactPayload fact) {
        facts.put(fact.getFactId().toString(), factBody(fact, BillingFactStatus.APPROVED));
    }

    /** 配置事实详情读取故障。 */
    public synchronized void mockFactFailure(long factId, MockHttpFault fault) {
        facts.put(Long.toString(factId), fault);
    }

    /** 503 Problem。 */
    public static MockHttpFault unavailable() {
        return MockHttpFault.json(HttpStatus.SERVICE_UNAVAILABLE.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
            "Service Unavailable", "合成故障", "SERVICE_UNAVAILABLE", REQUEST_ID, "2026-10-19T00:00:00.000000Z", null));
    }

    /** 记录并返回已配置的发布进度或故障。 */
    public synchronized BillingFactProgressResponseBody answerProgress(String entityType, String entityId,
        String currency) {
        String key = key(entityType, entityId, currency);
        calls.add("progress:" + key);
        Object configured = progress.get(key);
        if (configured == null && nothingPublishedByDefault) {
            return progressBody(0, null, 0, null);
        }
        return (BillingFactProgressResponseBody)answer(configured, "progress " + key);
    }

    /** 记录并返回已配置的事实详情或故障。 */
    public synchronized BillingFactResponseBody answerFact(String factId) {
        calls.add("fact:" + factId);
        return (BillingFactResponseBody)answer(facts.get(factId), "fact " + factId);
    }

    /** 已配置的故障抛出，未配置记为意外。 */
    Object answer(Object configured, String what) {
        if (configured instanceof MockHttpFault fault) {
            throw fault;
        }
        if (configured == null) {
            unexpected++;
            throw MockHttpFault.json(HttpStatus.INTERNAL_SERVER_ERROR.value(), new ApiProblemResponse(
                ApiProblemProfile.TYPE, "Internal Server Error", "未配置的 billing 查询 " + what, "INTERNAL_ERROR",
                REQUEST_ID, "2026-10-19T00:00:00.000000Z", null));
        }
        return configured;
    }

    /** 记录一次不在本 Mock 范围内的 billing 调用。 */
    public synchronized MockHttpFault unexpectedCall(String operation) {
        unexpected++;
        calls.add(operation);
        return MockHttpFault.json(HttpStatus.INTERNAL_SERVER_ERROR.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
            "Internal Server Error", "ledger 不应调用 " + operation, "INTERNAL_ERROR", REQUEST_ID,
            "2026-10-19T00:00:00.000000Z", null));
    }

    /** 调用记录：“progress:类型/标识/币种”或“fact:事实主键”。 */
    public synchronized List<String> calls() {
        return List.copyOf(calls);
    }

    /** 某个事实被读取的次数。 */
    public synchronized long factCalls(long factId) {
        return calls.stream().filter(call -> call.equals("fact:" + factId)).count();
    }

    /** 全局 AfterEach：不允许出现未配置的查询。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的 billing 调用: " + calls);
    }

    /** 每例清除配置与调用记录。 */
    @Override
    public synchronized void clear() {
        progress.clear();
        facts.clear();
        calls.clear();
        nothingPublishedByDefault = false;
        unexpected = 0;
    }

    /** 账簿主体键。 */
    static String key(String bookEntityType, String bookEntityId, String currency) {
        return bookEntityType + "/" + bookEntityId + "/" + currency;
    }

    /** 构造进度响应 DTO。 */
    static BillingFactProgressResponseBody progressBody(long pendingCount, Long maxPendingFactId,
        long publishedFactCount, Long maxPublishedFactId) {
        BillingFactProgressResponseBody body = new BillingFactProgressResponseBody();
        body.setPendingCount(Long.toString(pendingCount));
        body.setMaxPendingFactId(maxPendingFactId == null ? null : maxPendingFactId.toString());
        body.setPublishedFactCount(Long.toString(publishedFactCount));
        body.setMaxPublishedFactId(maxPublishedFactId == null ? null : maxPublishedFactId.toString());
        return body;
    }

    /** 计费事实载荷转换为 billing 详情响应（与 billing 详情接口同字段）。 */
    static BillingFactResponseBody factBody(BillingFactPayload fact, BillingFactStatus status) {
        BillingFactResponseBody body = new BillingFactResponseBody();
        body.setFactId(text(fact.getFactId()));
        body.setFactVersion(text(fact.getFactVersion()));
        body.setSupersedesFactId(text(fact.getSupersedesFactId()));
        body.setParentFactId(text(fact.getParentFactId()));
        body.setFactType(fact.getFactType());
        body.setOriginType(fact.getOriginType());
        body.setStatus(status.getCode());
        body.setSourceSystem(fact.getSourceSystem());
        body.setSourceEventKey(fact.getSourceEventKey());
        body.setSourceVersion(text(fact.getSourceVersion()));
        body.setSourcePayloadHash(fact.getSourcePayloadHash());
        body.setBusinessTime(fact.getBusinessTime());
        body.setRecordedTime(fact.getRecordedTime());
        body.setBalanceAvailableTime(fact.getBalanceAvailableTime());
        body.setClientId(text(fact.getClientId()));
        body.setEntityNo(fact.getEntityNo());
        body.setLocalPartnerId(text(fact.getLocalPartnerId()));
        body.setFinanceEntityId(text(fact.getFinanceEntityId()));
        body.setCurrency(fact.getCurrency());
        body.setGrossAmount(text(fact.getGrossAmount()));
        body.setApprovedTime(fact.getRecordedTime());
        body.setRefs(fact.getRefs() == null ? null : fact.getRefs().stream().map(BillingFactMocker::refBody).toList());
        body.setLines(fact.getLines() == null ? null : fact.getLines().stream().map(BillingFactMocker::lineBody).toList());
        body.setLinks(List.of());
        return body;
    }

    /** 外部引用转换。 */
    static BillingFactRefResponseBody refBody(BillingFactRefPayload ref) {
        BillingFactRefResponseBody body = new BillingFactRefResponseBody();
        body.setRefType(ref.getRefType());
        body.setRefId(ref.getRefId());
        body.setExternalReferenceType(ref.getExternalReferenceType());
        body.setExternalTransactionId(ref.getExternalTransactionId());
        return body;
    }

    /** 明细行转换。 */
    static BillingFactLineResponseBody lineBody(BillingFactLinePayload line) {
        BillingFactLineResponseBody body = new BillingFactLineResponseBody();
        body.setLineNo(text(line.getLineNo()));
        body.setComponentType(line.getComponentType());
        body.setPayerRole(line.getPayerRole());
        body.setPayerId(line.getPayerId());
        body.setPayeeRole(line.getPayeeRole());
        body.setPayeeId(line.getPayeeId());
        body.setDirection(line.getDirection());
        body.setPostedAmount(text(line.getPostedAmount()));
        body.setTargetBucket(line.getTargetBucket());
        return body;
    }

    /** 十进制文本；为空时为空。 */
    static String text(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        return value == null ? null : value.toString();
    }
}
