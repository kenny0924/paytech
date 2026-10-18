package com.paytech.foundation.messaging.auto.sequenced;

import java.time.Duration;
import java.time.Instant;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.messaging.api.AggregateKey;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventPublisher;
import com.paytech.foundation.messaging.api.DomainEventPublishException;
import com.paytech.foundation.messaging.test.support.TestRun;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 测试发布 Handler：按四段式事件键发布一条信封，发布失败按可重试处理（与 Payment / Compliance 的发布 Handler 同一合同）。
 */
@TestComponent
public final class SequencedPublishHandler implements CompensationEventHandler {

    /** 补偿事件类型。 */
    public static final String EVENT_TYPE = "TEST_SEQUENCED_PUBLISH";

    private final DomainEventPublisher publisher;
    private final SequencedPublishProbe probe;

    /**
     * 创建 Handler。
     *
     * @param publisher 真实发布端
     * @param probe 观察器
     */
    public SequencedPublishHandler(DomainEventPublisher publisher, SequencedPublishProbe probe) {
        this.publisher = publisher;
        this.probe = probe;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(10), 5, Duration.ofMillis(100), Duration.ofSeconds(1),
            2.0D, 0.0D);
    }

    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        probe.enter(event.eventKey());
        try {
            Thread.sleep(probe.delayOf(event.eventKey()));
            AggregateKey.ParsedEventKey key = AggregateKey.parseEventKey(event.eventKey());
            ObjectNode payload = JsonNodeFactory.instance.objectNode();
            payload.put("note", event.eventKey());
            publisher.publish(DomainEvent.builder().eventType(key.eventType()).aggregate(key.aggregateKey())
                .aggregateVersion(key.aggregateVersion()).occurredAt(Instant.parse("2026-10-17T10:00:00.123456Z"))
                .sourceService(TestRun.SEQUENCED_PRODUCER).instanceId("main").payload(payload).build());
            return CompensationHandlerResult.success();
        } catch (DomainEventPublishException unpublished) {
            return CompensationHandlerResult.retryableFailure("TEST_UNPUBLISHED", "test event publication requires retry");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return CompensationHandlerResult.retryableFailure("TEST_INTERRUPTED", "test handler interrupted");
        } finally {
            probe.exit(event.eventKey());
        }
    }
}
