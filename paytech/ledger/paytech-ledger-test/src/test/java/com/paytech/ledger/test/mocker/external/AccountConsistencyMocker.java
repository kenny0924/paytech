package com.paytech.ledger.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.api.response.BalanceConsistencyResponseBody;
import com.paytech.account.api.response.OwnerBalanceCountResponseBody;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/**
 * account 账簿余额一致性接口 Mock（关账步骤 2、3）：只配置响应 DTO 与故障，记录被查询的账簿；未配置的查询记为意外
 * <li>{@link #mockCaughtUpByDefault()} 让任意账簿返回“尚未处理任何事实、无非零账户”，供只关心 ledger 本库步骤的关账用例使用。</li>
 */
@TestComponent
public class AccountConsistencyMocker implements Clear {
    /** 故障响应的 Request ID。 */
    public static final String REQUEST_ID = "account-test";

    private final Map<String, Object> answers = new HashMap<>();
    private final List<String> calls = new ArrayList<>();
    private boolean caughtUpByDefault;
    private int unexpected;

    /** 任意未单独配置的账簿返回“尚未处理任何事实、无非零账户”。 */
    public synchronized void mockCaughtUpByDefault() {
        caughtUpByDefault = true;
    }

    /** 配置账簿的已处理最大 factId 与非零渠道 / 银行账户数。 */
    public synchronized void mockConsistency(long ledgerBookId, Long maxAppliedFactId, long nonZeroChannelAccounts,
        long nonZeroCashAccounts) {
        answers.put(Long.toString(ledgerBookId), body(Long.toString(ledgerBookId), maxAppliedFactId,
            nonZeroChannelAccounts, nonZeroCashAccounts));
    }

    /** 配置账簿的读取故障。 */
    public synchronized void mockFailure(long ledgerBookId, MockHttpFault fault) {
        answers.put(Long.toString(ledgerBookId), fault);
    }

    /** 503 Problem。 */
    public static MockHttpFault unavailable() {
        return MockHttpFault.json(HttpStatus.SERVICE_UNAVAILABLE.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
            "Service Unavailable", "合成故障", "SERVICE_UNAVAILABLE", REQUEST_ID, "2026-10-18T00:00:00.000000Z", null));
    }

    /** 记录并返回已配置的响应或故障。 */
    public synchronized BalanceConsistencyResponseBody answer(String bookId) {
        calls.add(bookId);
        Object configured = answers.get(bookId);
        if (configured instanceof MockHttpFault fault) {
            throw fault;
        }
        if (configured == null && caughtUpByDefault) {
            return body(bookId, null, 0, 0);
        }
        if (configured == null) {
            unexpected++;
            throw MockHttpFault.json(HttpStatus.INTERNAL_SERVER_ERROR.value(), new ApiProblemResponse(
                ApiProblemProfile.TYPE, "Internal Server Error", "未配置的 consistency 查询", "INTERNAL_ERROR", REQUEST_ID,
                "2026-10-18T00:00:00.000000Z", null));
        }
        return (BalanceConsistencyResponseBody)configured;
    }

    /** 记录一次不在本 Mock 范围内的 account 调用。 */
    public synchronized MockHttpFault unexpectedCall(String operation) {
        unexpected++;
        calls.add(operation);
        return MockHttpFault.json(HttpStatus.INTERNAL_SERVER_ERROR.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
            "Internal Server Error", "ledger 不应调用 " + operation, "INTERNAL_ERROR", REQUEST_ID,
            "2026-10-18T00:00:00.000000Z", null));
    }

    /** 被查询的账簿。 */
    public synchronized List<String> calls() {
        return List.copyOf(calls);
    }

    /** 全局 AfterEach：不允许出现未配置的查询。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的 account consistency 查询");
    }

    /** 每例清除配置与调用记录。 */
    @Override
    public synchronized void clear() {
        answers.clear();
        calls.clear();
        caughtUpByDefault = false;
        unexpected = 0;
    }

    /** 构造响应 DTO：固定列出四个 Owner 类型。 */
    static BalanceConsistencyResponseBody body(String bookId, Long maxAppliedFactId, long channel, long cash) {
        BalanceConsistencyResponseBody body = new BalanceConsistencyResponseBody();
        body.setLedgerBookId(bookId);
        body.setBookEntityType("OPERATING_ENTITY");
        body.setBookEntityId("AE001");
        body.setCurrency("USD");
        body.setAppliedFactCount(maxAppliedFactId == null ? "0" : "1");
        body.setMaxAppliedFactId(maxAppliedFactId == null ? null : maxAppliedFactId.toString());
        body.setNonZeroAccountCounts(List.of(count(OwnerType.CLIENT, 0), count(OwnerType.LOCAL_PARTNER, 0),
            count(OwnerType.CHANNEL_ACCOUNT, channel), count(OwnerType.CASH_ACCOUNT, cash)));
        return body;
    }

    /** 一个 Owner 类型的计数。 */
    static OwnerBalanceCountResponseBody count(OwnerType ownerType, long count) {
        OwnerBalanceCountResponseBody body = new OwnerBalanceCountResponseBody();
        body.setOwnerType(ownerType.getCode());
        body.setCount(Long.toString(count));
        return body;
    }
}
