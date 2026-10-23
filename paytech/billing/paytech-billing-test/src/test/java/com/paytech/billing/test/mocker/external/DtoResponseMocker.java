package com.paytech.billing.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;

import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/**
 * 按"业务标识"配置上游响应 DTO 或故障、记录调用的外部 Mocker 公共部分（自动 PAYMENT 事实的五个上游共用）
 * <li>单个配置每次调用都返回同一应答（重投场景下故障持续）；顺序配置逐次消费，耗尽后记为意外调用。未配置的标识同样记为意外调用。</li>
 * <li>只记录业务标识，不保存完整请求；需要断言请求字段的 Mocker 自行保存最小快照。</li>
 *
 * @param <K> 匹配用的业务标识
 * @param <R> 上游正式响应 DTO
 */
public abstract class DtoResponseMocker<K, R> implements Clear {
    /** Mock 响应的 Request ID，响应头与 Problem 体一致。 */
    public static final String REQUEST_ID = CurrencySpecMocker.REQUEST_ID;

    private final String upstream;

    private final Map<K, Deque<Object>> answers = new HashMap<>();

    private final Set<K> sequences = new HashSet<>();

    private final List<K> calls = new ArrayList<>();

    private final List<String> unexpected = new ArrayList<>();

    /** 以上游名称构造，用于失败消息。 */
    protected DtoResponseMocker(String upstream) {
        this.upstream = upstream;
    }

    /** 配置该标识每次调用都返回的响应 DTO。 */
    public synchronized void mock(K key, R response) {
        answers.put(Objects.requireNonNull(key), new ArrayDeque<>(List.of(Objects.requireNonNull(response))));
        sequences.remove(key);
    }

    /** 配置该标识每次调用都返回的 HTTP 故障。 */
    public synchronized void mockFault(K key, MockHttpFault fault) {
        answers.put(Objects.requireNonNull(key), new ArrayDeque<>(List.of(Objects.requireNonNull(fault))));
        sequences.remove(key);
    }

    /** 配置逐次消费的应答序列（响应 DTO 或 {@link MockHttpFault}），耗尽后记为意外调用。 */
    public synchronized void mockSequence(K key, Object... sequence) {
        answers.put(Objects.requireNonNull(key), new ArrayDeque<>(Arrays.asList(sequence)));
        sequences.add(key);
    }

    /**
     * 由 Mock Controller 调用：记录调用并返回已配置的应答
     *
     * @param operation 上游操作名，用于意外调用消息
     * @param key 本次请求的业务标识
     * @return 响应 DTO；故障抛出，未配置或序列耗尽记为意外调用
     */
    @SuppressWarnings("unchecked")
    protected synchronized R answer(String operation, K key) {
        calls.add(key);
        Deque<Object> configured = answers.get(key);
        Object answer = configured == null ? null : sequences.contains(key) ? configured.poll() : configured.peek();
        if (answer == null) {
            throw unexpectedCall(operation + " " + key);
        }
        if (answer instanceof MockHttpFault fault) {
            throw fault;
        }
        return (R)answer;
    }

    /** 记录一次不在配置范围内的调用，返回 500 Problem 故障。 */
    public synchronized MockHttpFault unexpectedCall(String operation) {
        unexpected.add(operation);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
    }

    /** 该标识被调用的次数。 */
    public synchronized int calls(K key) {
        return (int)calls.stream().filter(key::equals).count();
    }

    /** 本例没有调用该上游。 */
    public synchronized void assertNoCalls() {
        assertEquals(List.of(), calls, "不应调用 " + upstream);
    }

    /** 全局 AfterEach：不允许出现未配置的调用。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(List.of(), unexpected, "存在未配置的 " + upstream + " 调用");
    }

    /** 全局 BeforeEach：上一例的配置与记录已清除。 */
    public synchronized void assertEmpty() {
        assertAll(upstream + " Mock 应处于空基线", () -> assertTrue(answers.isEmpty()), () -> assertTrue(calls.isEmpty()),
            () -> assertTrue(unexpected.isEmpty()));
    }

    /** 每例清除配置与调用记录。 */
    @Override
    public synchronized void clear() {
        answers.clear();
        sequences.clear();
        calls.clear();
        unexpected.clear();
    }

    /** 第一方 Problem 故障：标题为该状态的标准短语，Request ID 与 Mock 响应头一致。 */
    public static MockHttpFault problem(HttpStatus status, String code) {
        return MockHttpFault.json(status.value(), new ApiProblemResponse(ApiProblemProfile.TYPE, status.getReasonPhrase(),
            "合成故障", code, REQUEST_ID, "2026-10-22T00:00:00.000000Z", null));
    }
}
