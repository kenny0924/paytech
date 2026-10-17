package com.paytech.platform.support.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.*;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Amazon SES v2 SendEmail 的响应配置与请求记录：按收件人依次返回用例显式配置的响应。
 *
 * <p>
 * 未配置的收件人或序列耗尽记为意外调用并失败。只模拟单次 SendEmail 响应，不模拟 SES 账号或投递状态。
 */
@TestComponent
public class SesMocker implements Clear {

    /** SES v2 SendEmail 的 REST 路径（不含 Mock 前缀）。 */
    public static final String SEND_EMAIL_PATH = "/v2/email/outbound-emails";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Map<String, Deque<Reply>> replies = new HashMap<>();
    private final List<Received> requests = new ArrayList<>();
    private int unexpected;

    /** 受理并返回 MessageId。 */
    public synchronized SesMocker accept(String email, String messageId) {
        return reply(email, new Reply(200, null, "{\"MessageId\":\"" + messageId + "\"}", 0, false));
    }

    /** 200 但缺少 MessageId，适配器应视为结果未知。 */
    public synchronized SesMocker acceptWithoutMessageId(String email) {
        return reply(email, new Reply(200, null, "{}", 0, false));
    }

    /** 延迟后受理，用于触发客户端读超时。 */
    public synchronized SesMocker acceptAfter(String email, String messageId, long delayMs) {
        return reply(email, new Reply(200, null, "{\"MessageId\":\"" + messageId + "\"}", delayMs, false));
    }

    /**
     * 返回 SES 错误。
     *
     * @param status HTTP 状态
     * @param errorType SES 错误类型，写入 x-amzn-ErrorType 与响应体 __type
     * @param message 错误文字，用于验证适配器不保存或记录它
     */
    public synchronized SesMocker fail(String email, int status, String errorType, String message) {
        String body = JSON.writeValueAsString(Map.of("__type", errorType, "message", message));
        return reply(email, new Reply(status, errorType, body, 0, false));
    }

    /** 不返回响应、直接断开连接。 */
    public synchronized SesMocker disconnect(String email) {
        return reply(email, new Reply(0, null, "", 0, true));
    }

    /** 追加该收件人的下一条响应。 */
    synchronized SesMocker reply(String email, Reply reply) {
        replies.computeIfAbsent(email, key -> new ArrayDeque<>()).add(reply);
        return this;
    }

    /** 由 Mock Controller 调用：解析并记录请求，按收件人取出下一条配置；未配置即意外调用。 */
    public synchronized Reply next(String method, String path, Map<String, String> headers, String body) {
        var received = new Received(method, path, headers, JSON.readTree(body));
        requests.add(received);
        Deque<Reply> queue = SEND_EMAIL_PATH.equals(path) ? replies.get(received.recipient()) : null;
        Reply reply = queue == null ? null : queue.poll();
        if (reply == null) {
            unexpected++;
            throw new IllegalStateException("未配置的 SES 请求：" + method + " " + path);
        }
        return reply;
    }

    /** 本例收到的全部请求。 */
    public synchronized List<Received> requests() {
        return List.copyOf(requests);
    }

    /** 每例开始前确认上例已清理。 */
    public synchronized void assertEmpty() {
        assertEquals(0, replies.size() + requests.size() + unexpected, "SES Mock 未清理");
    }

    /** 每例结束检查没有未配置的 SES 请求。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的 SES 请求");
    }

    /** 清除全部响应配置与请求记录。 */
    @Override
    public synchronized void clear() {
        replies.clear();
        requests.clear();
        unexpected = 0;
    }

    /**
     * 一次响应。
     *
     * @param status HTTP 状态
     * @param errorType SES 错误类型；成功时为 null
     * @param body 响应体 JSON
     * @param delayMs 返回前等待的毫秒数
     * @param disconnect 是否直接断开连接
     */
    public record Reply(int status, String errorType, String body, long delayMs, boolean disconnect) {
    }

    /**
     * SES 收到的一次请求。
     *
     * @param method HTTP 方法
     * @param path 路径，不含 Mock 前缀
     * @param headers Header，名称小写
     * @param body 已解析的 JSON 请求体
     */
    public record Received(String method, String path, Map<String, String> headers, JsonNode body) {

        /** 唯一收件人；请求缺少收件人时返回 null。 */
        public String recipient() {
            var to = body.path("Destination").path("ToAddresses");
            return to.size() == 1 ? to.get(0).asString() : null;
        }

        /** 发件人。 */
        public String from() {
            return body.path("FromEmailAddress").asString();
        }

        /** 纯文本主题。 */
        public String subject() {
            return body.path("Content").path("Simple").path("Subject").path("Data").asString();
        }

        /** 纯文本正文。 */
        public String text() {
            return body.path("Content").path("Simple").path("Body").path("Text").path("Data").asString();
        }

        /** 诊断输出不打印收件人、正文或签名。 */
        @Override
        public String toString() {
            return "Received[" + method + " " + path + "]";
        }
    }
}
