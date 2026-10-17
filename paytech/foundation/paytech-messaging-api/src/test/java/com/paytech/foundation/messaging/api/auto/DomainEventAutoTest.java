package com.paytech.foundation.messaging.api.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.foundation.messaging.api.DomainEvent;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 信封构造：字段校验、默认事件 ID、规范 Payload 哈希与不可变性。
 *
 * @see DomainEvent.Builder#build()
 * @see DomainEvent#payloadHashOf(tools.jackson.databind.JsonNode)
 */
class DomainEventAutoTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** {"amount":"10.00","currency":"USD","factId":"7001"} 的 SHA-256，独立于实现用 shasum 计算。 */
    private static final String EXPECTED_HASH = "sha256:ee6b441d8a40b4a46c1ebd6e10f6be8f5629ecf79f30f4b3a22baf3aeef65da5";

    @Test
    void test_normalFlow() {
        DomainEvent event = template().build();

        assertEquals("ECONOMIC_FACT:7001:EconomicFactRecorded:3", event.eventId());
        assertEquals(event.eventId(), event.eventKey());
        assertEquals("EconomicFactRecorded", event.eventType());
        assertEquals(1, event.schemaVersion());
        assertEquals("ECONOMIC_FACT", event.aggregateType());
        assertEquals("7001", event.aggregateId());
        assertEquals(3, event.aggregateVersion());
        assertEquals("billing", event.sourceService());
        assertEquals("main", event.instanceId());
        assertNull(event.correlationId());
        assertTrue(event.payloadHash().matches("sha256:[0-9a-f]{64}"), event.payloadHash());
        assertEquals("10.00", event.payload().get("amount").asString());
    }

    @Test
    void test_normalFlow_payloadHashIsCanonical() {
        ObjectNode reordered = MAPPER.createObjectNode();
        reordered.put("currency", "USD");
        reordered.put("amount", "10.00");
        reordered.put("factId", "7001");

        DomainEvent first = template().build();
        DomainEvent second = template().payload(reordered).build();

        assertEquals(first.payloadHash(), second.payloadHash());
        assertEquals(EXPECTED_HASH, first.payloadHash());
        assertNotEquals(first.payloadHash(), template().payload(payload("11.00")).build().payloadHash());
    }

    @Test
    void test_normalFlow_explicitEventIdAndCorrelation() {
        DomainEvent event = template().eventId("01JABCDEF0123456789").correlationId("req-123").schemaVersion(2).build();

        assertEquals("01JABCDEF0123456789", event.eventId());
        assertEquals("ECONOMIC_FACT:7001:EconomicFactRecorded:3", event.eventKey());
        assertEquals("req-123", event.correlationId());
        assertEquals(2, event.schemaVersion());
    }

    @Test
    void test_edgeCase_payloadIsCopied() {
        ObjectNode payload = payload("10.00");
        DomainEvent event = template().payload(payload).build();

        payload.put("amount", "99.00");
        event.payload().put("amount", "77.00");

        assertEquals("10.00", event.payload().get("amount").asString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBuilders")
    void test_invalidParameter(String scenario, UnaryOperator<DomainEvent.Builder> mutation) {
        assertThrows(IllegalArgumentException.class, () -> mutation.apply(template()).build(), scenario);
    }

    static Stream<Arguments> invalidBuilders() {
        return Stream.of(
            Arguments.of("缺少事件类型", (UnaryOperator<DomainEvent.Builder>) builder -> builder.eventType(null)),
            Arguments.of("事件类型小写", (UnaryOperator<DomainEvent.Builder>) builder -> builder.eventType("fact")),
            Arguments.of("Schema 版本为 0", (UnaryOperator<DomainEvent.Builder>) builder -> builder.schemaVersion(0)),
            Arguments.of("缺少聚合", (UnaryOperator<DomainEvent.Builder>) builder -> builder.aggregate(null)),
            Arguments.of("版本为负", (UnaryOperator<DomainEvent.Builder>) builder -> builder.aggregateVersion(-1)),
            Arguments.of("缺少发生时间", (UnaryOperator<DomainEvent.Builder>) builder -> builder.occurredAt(null)),
            Arguments.of("发布服务名非法", (UnaryOperator<DomainEvent.Builder>) builder -> builder.sourceService("Billing")),
            Arguments.of("实例 ID 非法", (UnaryOperator<DomainEvent.Builder>) builder -> builder.instanceId("MAIN")),
            Arguments.of("事件 ID 非法", (UnaryOperator<DomainEvent.Builder>) builder -> builder.eventId("id with space")),
            Arguments.of("关联 ID 非法", (UnaryOperator<DomainEvent.Builder>) builder -> builder.correlationId("a/b")),
            Arguments.of("Payload 为空", (UnaryOperator<DomainEvent.Builder>) builder -> builder.payload(null)),
            Arguments.of("Payload 不是对象",
                (UnaryOperator<DomainEvent.Builder>) builder -> builder.payload(MAPPER.createArrayNode())),
            Arguments.of("Payload 含小数",
                (UnaryOperator<DomainEvent.Builder>) builder -> builder.payload(payload("10.00").put("rate", 1.5))),
            Arguments.of("Payload 含负数",
                (UnaryOperator<DomainEvent.Builder>) builder -> builder.payload(payload("10.00").put("delta", -1))));
    }

    private static DomainEvent.Builder template() {
        return DomainEvent.builder()
            .eventType("EconomicFactRecorded")
            .aggregate("ECONOMIC_FACT", "7001")
            .aggregateVersion(3)
            .occurredAt(Instant.parse("2026-10-17T10:00:00.123456Z"))
            .sourceService("billing")
            .instanceId("main")
            .payload(payload("10.00"));
    }

    private static ObjectNode payload(String amount) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("factId", "7001");
        payload.put("amount", amount);
        payload.put("currency", "USD");
        return payload;
    }
}
