package com.paytech.channel.connector.test.mocker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;

/**
 * 外部渠道 HTTP 的响应 DTO 配置与请求记录：按 “方法 + 路径” 依次返回用例显式配置的响应（2026-10-20 随渠道协议自 Platform 迁入）
 * <li>未配置的请求记为意外调用并失败，用于证明未发出类失败返回前没有发出渠道请求。</li>
 * <li>只模拟单次响应，不模拟渠道状态机。</li>
 */
@TestComponent
public class ChannelMocker implements Clear {

    private final Map<String, Deque<Reply>> replies = new LinkedHashMap<>();
    private final List<Received> requests = new ArrayList<>();
    private int unexpected;

    /**
     * 返回 JSON 响应
     *
     * @param method HTTP 方法
     * @param path 渠道路径（不含 Mock 前缀，已编码）
     * @param status HTTP 状态
     * @param json 响应体
     * @return 当前 Mocker，便于链式配置
     */
    public synchronized ChannelMocker respond(String method, String path, int status, String json) {
        return reply(method, path, new Reply(status, Map.of("Content-Type", "application/json"), json, 0, false));
    }

    /**
     * 返回指定响应
     *
     * @param method HTTP 方法
     * @param path 渠道路径（不含 Mock 前缀，已编码）
     * @param reply 响应配置
     * @return 当前 Mocker，便于链式配置
     */
    public synchronized ChannelMocker reply(String method, String path, Reply reply) {
        replies.computeIfAbsent(method + " " + path, key -> new ArrayDeque<>()).add(reply);
        return this;
    }

    /**
     * 由 Mock Controller 调用：记录请求并取出下一条配置；未配置即意外调用
     *
     * @param request 渠道收到的请求
     * @return 配置的响应
     */
    public synchronized Reply next(Received request) {
        requests.add(request);
        Deque<Reply> queue = replies.get(request.method() + " " + request.path());
        Reply reply = queue == null ? null : queue.poll();
        if (reply == null) {
            unexpected++;
            throw new IllegalStateException("未配置的渠道请求：" + request.method() + " " + request.path());
        }
        return reply;
    }

    /** 本例收到的全部请求。 */
    public synchronized List<Received> requests() {
        return List.copyOf(requests);
    }

    /** 唯一一次请求。 */
    public synchronized Received only() {
        assertEquals(1, requests.size(), "渠道请求次数");
        return requests.getFirst();
    }

    /** 每例必须从完全空的 Mock 状态开始。 */
    public synchronized void assertEmpty() {
        assertTrue(replies.isEmpty() && requests.isEmpty() && unexpected == 0, "渠道 Mock 未从空状态开始");
    }

    /** 每例结束检查没有未配置的渠道请求。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的渠道请求");
    }

    /** 由 Foundation 的 AfterEach 统一回收响应配置与请求记录。 */
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
     * @param headers 响应 Header
     * @param body 响应体文本，可为空串
     * @param delayMs 返回前等待的毫秒数，用于超时场景
     * @param disconnect 是否直接断开连接
     */
    public record Reply(int status, Map<String, String> headers, String body, long delayMs, boolean disconnect) {

        /** 延迟返回的 JSON 响应。 */
        public static Reply delayed(int status, String json, long delayMs) {
            return new Reply(status, Map.of("Content-Type", "application/json"), json, delayMs, false);
        }

        /** 不返回响应、直接断开。 */
        public static Reply disconnected() {
            return new Reply(0, Map.of(), "", 0, true);
        }
    }

    /**
     * 渠道收到的一次请求。
     *
     * @param method HTTP 方法
     * @param path 原始（已编码）路径，不含 Mock 前缀
     * @param query 原始查询串，没有为 null
     * @param headers Header，名称小写
     * @param body 请求体文本
     */
    public record Received(String method, String path, String query, Map<String, String> headers, String body) {
    }
}
