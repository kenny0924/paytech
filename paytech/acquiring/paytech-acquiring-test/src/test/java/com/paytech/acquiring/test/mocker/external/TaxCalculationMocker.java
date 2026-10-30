package com.paytech.acquiring.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.acquiring.test.builder.external.TaxCalculationResponseBuilder;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.request.CalculateTaxRequestBody;
import com.paytech.tax.api.response.TaxCalculationResponseBody;

/**
 * tax 计算的响应 DTO：按计算阶段配置响应构造器或 HTTP 故障，记录每次请求
 * <li>同一订单 + revision 重复请求返回同一计算 ID，与 tax 的同身份同内容语义一致。</li>
 * <li>本阶段没有配置时记为意外调用并让测试失败，不默认成功。</li>
 */
@TestComponent
public class TaxCalculationMocker implements Clear {
    /** 计算 ID 起点，与其他 Mock 的样例 ID 不重叠；每次运行单调递增，保证本库唯一键不冲突。 */
    private static final AtomicLong IDS = new AtomicLong(9_300_000_000L);

    private final Map<TaxCalculationStage, TaxCalculationResponseBuilder> replies = new EnumMap<>(
        TaxCalculationStage.class);
    private final Map<String, Long> calculationIds = new HashMap<>();
    private final Map<Long, TaxCalculationResponseBody> stored = new HashMap<>();
    private final List<CalculateTaxRequestBody> requests = new ArrayList<>();
    private final List<String> reads = new ArrayList<>();
    private MockHttpFault failure;
    private int unexpected;

    /** 配置某一阶段的响应；后配置覆盖先配置。 */
    public synchronized TaxCalculationMocker mock(TaxCalculationStage stage, TaxCalculationResponseBuilder reply) {
        replies.put(stage, reply);
        return this;
    }

    /** 两个阶段都返回 NOT_MANAGED（税额 0），供与税务无关的建单用例作为类级默认。 */
    public synchronized TaxCalculationMocker notManaged() {
        mock(TaxCalculationStage.PROVISIONAL, TaxCalculationResponseBuilder.notManaged());
        return mock(TaxCalculationStage.FINAL, TaxCalculationResponseBuilder.notManaged());
    }

    /** 以真实 HTTP 故障响应全部请求，例如 503 Problem、409 冲突或断连。 */
    public synchronized void mockFailure(MockHttpFault value) {
        failure = value;
    }

    /** 配置按 ID 读取时返回的已保存计算（前置事实直接写入本库、没有经过计算请求的场景）。 */
    public synchronized void mockStored(long calculationId, TaxCalculationResponseBody body) {
        stored.put(calculationId, body);
    }

    /** HTTP Mock 实际调用此处：记录请求，返回本阶段的响应或故障；返回的计算同时可按 ID 读取。 */
    public TaxCalculationResponseBody answer(CalculateTaxRequestBody request) {
        TaxCalculationResponseBuilder reply;
        long calculationId;
        synchronized (this) {
            requests.add(request);
            if (failure != null) {
                throw failure;
            }
            reply = replies.get(TaxCalculationStage.valueOf(request.getStage()));
            if (reply == null) {
                unexpected++;
                throw new AssertionError("未配置的 tax 计算阶段: " + request.getStage());
            }
            calculationId = calculationIds.computeIfAbsent(
                request.getSourceObjectId() + "#" + request.getSourceRevision(), key -> IDS.incrementAndGet());
        }
        var body = reply.build(request, calculationId);
        synchronized (this) {
            stored.put(calculationId, body);
        }
        return body;
    }

    /** 按 ID 读取：返回已计算或已配置的计算；没有时记为意外调用。 */
    public synchronized TaxCalculationResponseBody answerGet(String calculationId) {
        reads.add(calculationId);
        var body = stored.get(Long.valueOf(calculationId));
        if (body == null) {
            unexpected++;
            throw new AssertionError("未配置的 tax 计算读取: " + calculationId);
        }
        return body;
    }

    /** 本例按 ID 读取计算的次数。 */
    public synchronized List<String> reads() {
        return List.copyOf(reads);
    }

    /** 本例收到的全部计算请求，按调用顺序。 */
    public synchronized List<CalculateTaxRequestBody> requests() {
        return List.copyOf(requests);
    }

    /** 断言本例的计算调用次数；零次表示未调用 tax。 */
    public synchronized void assertCalls(int count) {
        assertEquals(count, requests.size(), "tax 计算调用次数");
    }

    /** 每例结束检查没有未配置的计算请求。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的 tax 计算请求");
    }

    /** 每例清理响应配置、故障、调用与意外调用记录。 */
    @Override
    public synchronized void clear() {
        replies.clear();
        calculationIds.clear();
        stored.clear();
        requests.clear();
        reads.clear();
        failure = null;
        unexpected = 0;
    }
}
