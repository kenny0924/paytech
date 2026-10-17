package com.paytech.platform.support.test.checker;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.platform.support.test.dal.AuditTestMapper;

import lombok.RequiredArgsConstructor;

/** 从真实审计表读取异步写入的事件；只读断言，不推进业务。 */
@TestComponent
@RequiredArgsConstructor
public class AuditChecker {

    private final AuditTestMapper mapper;

    /** 有界等待指定请求的唯一审计事件，并检查动作、结果、操作者与资源。 */
    public Map<String, Object> assertEvent(String requestId, String action, String result, String actorId,
        String resourceType, String resourceId) {
        List<Map<String, Object>> events = await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(20))
            .until(() -> mapper.eventsByRequest(requestId), rows -> !rows.isEmpty());
        assertEquals(1, events.size(), "同一请求只应产生一条审计事件");
        Map<String, Object> event = events.getFirst();
        assertAll(() -> assertEquals(action, event.get("action_code"), "审计动作"),
            () -> assertEquals(result, event.get("result"), "审计结果"),
            () -> assertEquals(actorId, event.get("actor_id"), "审计操作者"),
            () -> assertEquals(resourceType, event.get("resource_type"), "审计资源类型"),
            () -> assertEquals(resourceId, event.get("resource_id"), "审计资源"));
        return event;
    }

    /** 请求不应产生审计事件；调用前须已等待审计队列清空。 */
    public void assertNoEvent(String requestId) {
        assertTrue(mapper.eventsByRequest(requestId).isEmpty(), "不应产生审计事件");
    }

    /** 审计属性中不得出现秘密原文。 */
    public void assertNoSecret(Map<String, Object> event, String secret) {
        assertFalse(String.valueOf(event).contains(secret), "审计事件包含秘密原文");
        long eventId = ((Number) event.get("audit_event_id")).longValue();
        assertFalse(String.valueOf(mapper.attributes(eventId)).contains(secret), "审计属性包含秘密原文");
    }
}
