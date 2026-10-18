package com.paytech.platform.support.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Amazon SQS ReceiveMessage / DeleteMessage 的响应配置与调用记录（AWS JSON 1.0 协议）。
 *
 * <p>
 * 按队列 URL 依次返回用例显式配置的接收结果；未配置的接收或序列耗尽记为意外调用并失败。删除默认成功并记录凭据，
 * 可按队列配置失败。只模拟单次调用响应，不模拟队列可见性或重新投递。
 */
@TestComponent
public class SqsMocker implements Clear {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Map<String, Deque<Reply>> receives = new HashMap<>();
    private final Map<String, Reply> deleteFailures = new HashMap<>();
    private final List<Received> requests = new ArrayList<>();
    private final List<String> deleted = new ArrayList<>();
    private int sequence;
    private int unexpected;

    /** 下一次接收返回这些消息体；删除凭据为 rh-<序号>。 */
    public synchronized SqsMocker messages(String queueUrl, String... bodies) {
        var messages = new ArrayList<Map<String, String>>();
        for (String body : bodies) {
            var message = new LinkedHashMap<String, String>();
            message.put("MessageId", "sqs-message-" + (++sequence));
            message.put("ReceiptHandle", "rh-" + sequence);
            message.put("MD5OfBody", md5(body));
            message.put("Body", body);
            messages.add(message);
        }
        return receive(queueUrl, new Reply(200, null, JSON.writeValueAsString(Map.of("Messages", messages))));
    }

    /** 下一次接收返回 SQS 错误；errorType 写入 __type 与 x-amzn-query-error。 */
    public synchronized SqsMocker failReceive(String queueUrl, int status, String errorType) {
        return receive(queueUrl, error(status, errorType));
    }

    /** 该队列之后的删除都返回 SQS 错误。 */
    public synchronized SqsMocker failDelete(String queueUrl, int status, String errorType) {
        deleteFailures.put(queueUrl, error(status, errorType));
        return this;
    }

    /** 追加该队列的下一次接收结果。 */
    synchronized SqsMocker receive(String queueUrl, Reply reply) {
        receives.computeIfAbsent(queueUrl, key -> new ArrayDeque<>()).add(reply);
        return this;
    }

    /** 由 Mock Controller 调用：按 X-Amz-Target 分派，记录请求并返回配置；未配置即意外调用。 */
    public synchronized Reply next(String target, Map<String, String> headers, String body) {
        JsonNode request = JSON.readTree(body);
        String queueUrl = request.path("QueueUrl").asString();
        requests.add(new Received(target, headers, request));
        if ("AmazonSQS.DeleteMessage".equals(target)) {
            Reply failure = deleteFailures.get(queueUrl);
            if (failure != null) {
                return failure;
            }
            deleted.add(request.path("ReceiptHandle").asString());
            return new Reply(200, null, "{}");
        }
        Deque<Reply> queue = "AmazonSQS.ReceiveMessage".equals(target) ? receives.get(queueUrl) : null;
        Reply reply = queue == null ? null : queue.poll();
        if (reply == null) {
            unexpected++;
            throw new IllegalStateException("未配置的 SQS 请求：" + target);
        }
        return reply;
    }

    /** 本例收到的全部请求。 */
    public synchronized List<Received> requests() {
        return List.copyOf(requests);
    }

    /** 本例成功删除的消息凭据，按删除先后。 */
    public synchronized List<String> deleted() {
        return List.copyOf(deleted);
    }

    /** 每例开始前确认上例已清理。 */
    public synchronized void assertEmpty() {
        assertEquals(0, receives.size() + deleteFailures.size() + requests.size() + deleted.size() + unexpected,
            "SQS Mock 未清理");
    }

    /** 每例结束检查没有未配置的 SQS 请求，且配置的接收结果都已被取走。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的 SQS 请求");
        assertEquals(0, receives.values().stream().mapToInt(Deque::size).sum(), "存在未被取走的 SQS 接收配置");
    }

    /** 清除全部响应配置与调用记录。 */
    @Override
    public synchronized void clear() {
        receives.clear();
        deleteFailures.clear();
        requests.clear();
        deleted.clear();
        sequence = 0;
        unexpected = 0;
    }

    /** JSON 协议错误响应。 */
    static Reply error(int status, String errorType) {
        return new Reply(status, errorType, JSON.writeValueAsString(Map.of("__type", errorType, "message", "rejected")));
    }

    /** SDK 会校验消息体的 MD5。 */
    static String md5(String body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * 一次响应。
     *
     * @param status HTTP 状态
     * @param errorType SQS 错误类型；成功时为 null
     * @param body 响应体 JSON
     */
    public record Reply(int status, String errorType, String body) {
    }

    /**
     * SQS 收到的一次请求。
     *
     * @param target X-Amz-Target，如 AmazonSQS.ReceiveMessage
     * @param headers Header，名称小写
     * @param body 已解析的 JSON 请求体
     */
    public record Received(String target, Map<String, String> headers, JsonNode body) {
    }
}
