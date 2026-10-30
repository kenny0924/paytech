package com.paytech.payout.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.platform.support.api.notification.request.SubmitNotificationRequestBody;
import com.paytech.platform.support.api.notification.response.SubmitNotificationResponseBody;

/**
 * Platform 通知（technique 8.3，提供方 DTO）的 Mock：按模板编码按顺序返回预设结果（投递数或 HTTP 故障）
 * <li>调用记录保存请求快照（去重键、模板、租户收件人、参数），参数只有非敏感值。</li>
 * <li>未配置的模板或顺序耗尽记为意外调用。</li>
 */
@TestComponent
public class PlatformNotificationMocker implements Clear {
    /** 期望的调用服务名。 */
    public static final String CALLER = "payout-service";

    private final Map<String, ArrayDeque<Object>> outcomes = new HashMap<>();

    private final List<SubmitNotificationRequestBody> calls = new ArrayList<>();

    private final List<String> unexpected = new ArrayList<>();

    /** 为模板按顺序追加"受理，投递数 n"。 */
    public synchronized void accept(String templateCode, int deliveryCount) {
        SubmitNotificationResponseBody body = new SubmitNotificationResponseBody(
            deliveryCount == 0 ? null : "9000000000001", deliveryCount, deliveryCount);
        outcomes.computeIfAbsent(templateCode, key -> new ArrayDeque<>()).add(body);
    }

    /** 为模板按顺序追加一个 HTTP 故障。 */
    public synchronized void fail(String templateCode, MockHttpFault fault) {
        outcomes.computeIfAbsent(templateCode, key -> new ArrayDeque<>()).add(fault);
    }

    /** Mock Controller 调用。 */
    public synchronized SubmitNotificationResponseBody submit(String caller, SubmitNotificationRequestBody body) {
        calls.add(body);
        ArrayDeque<Object> queue = outcomes.get(body.getTemplateCode());
        Object outcome = queue == null ? null : queue.poll();
        if (!CALLER.equals(caller) || outcome == null) {
            unexpected.add(body.getTemplateCode());
            throw new AssertionError("Platform 通知未配置或已耗尽: " + body.getTemplateCode());
        }
        if (outcome instanceof MockHttpFault fault) {
            throw fault;
        }
        return (SubmitNotificationResponseBody)outcome;
    }

    /** 本例调用记录。 */
    public synchronized List<SubmitNotificationRequestBody> calls() {
        return List.copyOf(calls);
    }

    /** 每例开始时必须为空基线。 */
    public synchronized void assertEmpty() {
        assertTrue(outcomes.isEmpty() && calls.isEmpty() && unexpected.isEmpty(), "Platform Mock 应处于空基线");
    }

    /** 意外调用检查。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertTrue(unexpected.isEmpty(), "存在意外 Platform 通知：" + unexpected);
    }

    /** 每例结束清理。 */
    @Override
    public synchronized void clear() {
        outcomes.clear();
        calls.clear();
        unexpected.clear();
    }
}
