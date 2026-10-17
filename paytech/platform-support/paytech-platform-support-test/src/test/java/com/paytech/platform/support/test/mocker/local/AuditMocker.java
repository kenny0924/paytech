package com.paytech.platform.support.test.mocker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.dal.AuditTestMapper;
import com.paytech.platform.support.test.dal.AuditTestMapper.Event;

import lombok.RequiredArgsConstructor;

/** 审计查询入口的本地前置事件；审计表由 {@link PlatformDataCleaner} 整表回收。 */
@TestComponent
@RequiredArgsConstructor
public class AuditMocker {

    private final AuditTestMapper mapper;
    private final AtomicInteger sequence = new AtomicInteger();

    /** 插入一条平台审计事件并返回标识。 */
    public long event(String actorId, String action, String resourceType, String resourceId, String result,
        String requestId, Instant occurred) {
        return event("PLATFORM", actorId, action, resourceType, resourceId, result, requestId, occurred);
    }

    /** 插入指定资源系统的审计事件。 */
    public long event(String system, String actorId, String action, String resourceType, String resourceId,
        String result, String requestId, Instant occurred) {
        long id = OperatorBuilder.nextId();
        assertEquals(1, mapper.insertEvent(new Event(id, 1L, sequence.incrementAndGet(), hash(id), actorId, action,
            system, resourceType, resourceId, result, requestId, occurred)));
        return id;
    }

    /** 为事件追加属性。 */
    public void attribute(long eventId, int number, String key, String text, String token, String masked,
        String classification, Instant now) {
        assertEquals(1, mapper.insertAttribute(OperatorBuilder.nextId(), eventId, number, key, text, token, masked,
            hash(eventId + number), classification, now));
    }

    private static String hash(long seed) {
        return HexFormat.of().formatHex(java.nio.ByteBuffer.allocate(32).putLong(seed).putLong(System.nanoTime())
            .array());
    }
}
