package com.paytech.ledger.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.billing.api.enums.EconomicFactStatus;
import com.paytech.billing.api.fact.EconomicFactLinePayload;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.billing.api.response.EconomicFactLineResponseBody;
import com.paytech.billing.api.response.EconomicFactResponseBody;
import com.paytech.billing.api.response.PendingEconomicFactsResponseBody;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/**
 * billing 经济事实接口 Mock：按事实主键配置详情 DTO（AccountPosted 消费与追平读事实），按账簿主体配置发布进度 DTO（关账步骤 3）；
 * 未配置的查询记为意外
 * <li>详情 DTO 由测试侧把经济事实载荷转换为 billing 正式响应：登记时间写批准时间，明细行金额写普通十进制字符串。</li>
 * <li>{@link #mockNothingPublishedByDefault()} 让任意账簿主体返回“没有未发布事实、也没有已发布事实”。</li>
 */
@TestComponent
public class EconomicFactMocker implements Clear {
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
        progress.put(key(bookEntityType, bookEntityId, currency),
            progressBody(pendingCount, publishedFactCount, maxPublishedFactId));
    }

    /** 配置账簿主体的读取故障。 */
    public synchronized void mockProgressFailure(String bookEntityType, String bookEntityId, String currency,
        MockHttpFault fault) {
        progress.put(key(bookEntityType, bookEntityId, currency), fault);
    }

    /** 配置事实详情：以已批准状态返回载荷对应的 billing 响应。 */
    public synchronized void mockFact(EconomicFactPayload fact) {
        facts.put(fact.getFactId().toString(), factBody(fact, EconomicFactStatus.APPROVED));
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
    public synchronized PendingEconomicFactsResponseBody answerProgress(String bookEntityType, String bookEntityId,
        String currency) {
        String key = key(bookEntityType, bookEntityId, currency);
        calls.add("pending:" + key);
        Object configured = progress.get(key);
        if (configured == null && nothingPublishedByDefault) {
            return progressBody(0, 0, null);
        }
        return (PendingEconomicFactsResponseBody)answer(configured, "pending " + key);
    }

    /** 记录并返回已配置的事实详情或故障。 */
    public synchronized EconomicFactResponseBody answerFact(String factId) {
        calls.add("fact:" + factId);
        return (EconomicFactResponseBody)answer(facts.get(factId), "fact " + factId);
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

    /** 调用记录：“pending:类型/标识/币种”或“fact:事实主键”。 */
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
    static PendingEconomicFactsResponseBody progressBody(long pendingCount, long publishedFactCount,
        Long maxPublishedFactId) {
        PendingEconomicFactsResponseBody body = new PendingEconomicFactsResponseBody();
        body.setPendingCount(Long.toString(pendingCount));
        body.setMaxPendingFactId(null);
        body.setPublishedFactCount(Long.toString(publishedFactCount));
        body.setMaxPublishedFactId(maxPublishedFactId == null ? null : maxPublishedFactId.toString());
        return body;
    }

    /** 经济事实载荷转换为 billing 详情响应（与 billing EconomicFactConverter 同字段）。 */
    static EconomicFactResponseBody factBody(EconomicFactPayload fact, EconomicFactStatus status) {
        EconomicFactResponseBody body = new EconomicFactResponseBody();
        body.setFactId(text(fact.getFactId()));
        body.setFactType(fact.getFactType());
        body.setStatus(status.getCode());
        body.setFactVersion(text(fact.getFactVersion()));
        body.setSourceSystem(fact.getSourceSystem());
        body.setSourceReference(fact.getSourceEventKey());
        body.setContentHash(fact.getSourcePayloadHash());
        body.setParentFactId(text(fact.getParentFactId()));
        body.setSupersedesFactId(text(fact.getSupersedesFactId()));
        body.setClientId(text(fact.getClientId()));
        body.setEntityNo(fact.getEntityNo());
        body.setLocalPartnerId(text(fact.getLocalPartnerId()));
        body.setPartnerAgreementOeId(text(fact.getPartnerAgreementOeId()));
        body.setFinanceEntityId(text(fact.getFinanceEntityId()));
        body.setCurrency(fact.getCurrency());
        body.setGrossAmount(text(fact.getGrossAmount()));
        body.setChannelAccountId(text(fact.getChannelAccountId()));
        body.setApprovedTime(fact.getRecordedTime());
        body.setPaymentSucceededTime(fact.getPaymentSucceededTime());
        body.setLines(fact.getLines() == null ? null : fact.getLines().stream().map(EconomicFactMocker::lineBody).toList());
        return body;
    }

    /** 明细行转换。 */
    static EconomicFactLineResponseBody lineBody(EconomicFactLinePayload line) {
        EconomicFactLineResponseBody body = new EconomicFactLineResponseBody();
        body.setLineNo(text(line.getLineNo()));
        body.setFeeTypeCode(line.getComponentType());
        body.setPayerRole(line.getPayerRole());
        body.setPayerId(line.getPayerId());
        body.setPayeeRole(line.getPayeeRole());
        body.setPayeeId(line.getPayeeId());
        body.setAmount(text(line.getPostedAmount()));
        body.setDirection(line.getDirection());
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
