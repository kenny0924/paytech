package com.paytech.acquiring.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.platform.support.api.webhook.request.SubmitWebhookEventRequestBody;

import tools.jackson.databind.JsonNode;

/**
 * Platform Webhook 受理接口的响应配置与调用记录：按 eventId 配置顺序响应，耗尽后按公共默认受理（204）处理。
 *
 * <p>
 * 公共默认受理由 MockTestBase 每例显式开启，使支付成功、取消和退款测试不被新任务打断；未开启默认且无配置时记为意外调用。
 */
@TestComponent
public class PlatformWebhookMocker implements Clear {
    /** Problem 响应 Header 与 Body 共用的 Request ID。 */
    public static final String ERROR_REQUEST_ID = "platform-mock-request";

    private final List<Submission> submissions = new ArrayList<>();
    private final Map<String, Deque<Reply>> replies = new HashMap<>();
    private boolean acceptByDefault;
    private int unexpected;

    /** 未单独配置的事件一律受理；每例全局初始化调用。 */
    public synchronized void acceptByDefault() {
        acceptByDefault = true;
    }

    /** 按调用顺序配置指定事件的响应，耗尽后回到公共默认。 */
    public synchronized void replies(String eventId, Reply... ordered) {
        replies.computeIfAbsent(eventId, key -> new ArrayDeque<>()).addAll(List.of(ordered));
    }

    /** 204 受理。 */
    public static Reply accepted() {
        return new Reply(null);
    }

    /** 合规的第一方 Problem 错误。 */
    public static Reply problem(HttpStatus status, String code) {
        return problem(status, code, null);
    }

    /** 带字段错误的合规 Problem，用于 422 字段校验失败。 */
    public static Reply problem(HttpStatus status, String code, List<ProblemFieldError> errors) {
        // 标题取该状态码的标准短语（422 存在两个同码常量，以解析结果为准）。
        var title = HttpStatus.valueOf(status.value()).getReasonPhrase();
        var problem = new ApiProblemResponse("about:blank", title, "Platform Mock 错误", code,
            ERROR_REQUEST_ID, "2026-10-17T00:00:00.000000Z", errors);
        return new Reply(MockHttpFault.json(status.value(), problem));
    }

    /** 错误体不是 Problem 的 HTTP 错误。 */
    public static Reply raw(HttpStatus status, String body) {
        return new Reply(MockHttpFault.raw(status.value(), body));
    }

    /** 不发送响应直接断开连接。 */
    public static Reply disconnect() {
        return new Reply(MockHttpFault.disconnect());
    }

    /** HTTP Mock 实际调用此处：记录请求并取出本次响应。 */
    public synchronized Reply next(String callerService, SubmitWebhookEventRequestBody body) {
        var submission = new Submission(callerService, body.getClientId(), body.getSourceVersion(),
            body.getSourcePayloadHash(), body.getPayloadHash(), body.getEnvelope().deepCopy());
        submissions.add(submission);
        Deque<Reply> ordered = replies.get(submission.eventId());
        if (ordered != null && !ordered.isEmpty()) {
            return ordered.poll();
        }
        if (!acceptByDefault) {
            unexpected++;
            throw new AssertionError("未配置的 Platform Webhook 提交");
        }
        return accepted();
    }

    /** 指定事件收到的全部提交，按调用顺序。 */
    public synchronized List<Submission> submissions(String eventId) {
        return submissions.stream().filter(submission -> eventId.equals(submission.eventId())).toList();
    }

    /** 每例结束检查没有未配置的提交。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的 Platform Webhook 提交");
    }

    /** 每例清理响应配置、调用与意外调用记录，并关闭公共默认受理。 */
    @Override
    public synchronized void clear() {
        submissions.clear();
        replies.clear();
        acceptByDefault = false;
        unexpected = 0;
    }

    /**
     * 一次提交的记录，只含合成测试值与冻结 Envelope。
     *
     * @param callerService X-Caller-Service Header
     * @param clientId 事件归属 Client
     * @param sourceVersion 来源版本
     * @param sourcePayloadHash data.object 摘要
     * @param payloadHash Envelope 摘要
     * @param envelope 提交的 Envelope
     */
    public record Submission(String callerService, String clientId, long sourceVersion, String sourcePayloadHash,
        String payloadHash, JsonNode envelope) {
        /** Envelope 中的事件 ID。 */
        public String eventId() {
            return envelope.path("eventId").asString("");
        }
    }

    /**
     * 一次受理响应
     *
     * @param fault HTTP 故障；为空表示 204 受理
     */
    public record Reply(MockHttpFault fault) {
        /** 是否为 HTTP 故障。 */
        public boolean isFault() {
            return fault != null;
        }
    }
}
