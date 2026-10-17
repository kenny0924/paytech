package com.paytech.platform.support.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;

/**
 * Client Webhook 接收端的响应配置与请求记录：按接收端名称依次返回用例显式配置的响应
 * <li>未配置的接收端或序列耗尽记为意外调用并失败；只模拟单次 HTTP 响应，不模拟 Client 业务</li>
 */
@TestComponent
public class WebhookReceiverMocker implements Clear {

    private final Map<String, Deque<Reply>> replies = new HashMap<>();
    private final List<Received> requests = new ArrayList<>();
    private int unexpected;

    /** 追加一次指定状态码的响应。 */
    public synchronized WebhookReceiverMocker reply(String receiver, int status) {
        return add(receiver, new Reply(status, 0, false));
    }

    /** 追加一次延迟后返回 200 的响应，用于触发发送超时。 */
    public synchronized WebhookReceiverMocker replyAfter(String receiver, long delayMs) {
        return add(receiver, new Reply(200, delayMs, false));
    }

    /** 追加一次不返回响应、直接断开的连接。 */
    public synchronized WebhookReceiverMocker disconnect(String receiver) {
        return add(receiver, new Reply(0, 0, true));
    }

    /** 由 Mock Controller 调用：记录请求，取出下一条配置；未配置即意外调用。 */
    public synchronized Reply next(Received received) {
        requests.add(received);
        Deque<Reply> queue = replies.get(received.receiver());
        Reply reply = queue == null ? null : queue.poll();
        if (reply == null) {
            unexpected++;
            throw new IllegalStateException("未配置的 Webhook 接收端请求：" + received.receiver());
        }
        return reply;
    }

    /** 本例某个接收端收到的全部请求。 */
    public synchronized List<Received> requests(String receiver) {
        return requests.stream().filter(request -> request.receiver().equals(receiver)).toList();
    }

    /** 本例收到的请求总数。 */
    public synchronized int count() {
        return requests.size();
    }

    /** 清理前检查意外调用。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "Webhook 接收端收到未配置的请求");
    }

    /** 每例清理响应配置与请求记录。 */
    @Override
    public synchronized void clear() {
        replies.clear();
        requests.clear();
        unexpected = 0;
    }

    /** 追加配置。 */
    WebhookReceiverMocker add(String receiver, Reply reply) {
        replies.computeIfAbsent(receiver, key -> new ArrayDeque<>()).add(reply);
        return this;
    }

    /** 一次响应配置。 */
    public record Reply(int status, long delayMs, boolean disconnect) {
    }

    /** 一次收到的请求：只保留验证所需的 Header 与原始 Body。 */
    public record Received(String receiver, String method, String contentType, String userAgent, String signature,
        byte[] body) {
    }
}
