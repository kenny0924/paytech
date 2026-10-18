package com.paytech.foundation.messaging.auto.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventPublishException;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.codec.InvalidDomainEventException;
import com.paytech.foundation.messaging.core.codec.PayloadHasher;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 信封 Schema v1 的序列化与严格读取。
 *
 * @see DomainEventCodec#encode(DomainEvent)
 * @see DomainEventCodec#decode(byte[])
 */
class DomainEventCodecAutoTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final DomainEventCodec codec = new DomainEventCodec();

    @Test
    void test_normalFlow_roundTripAndFieldOrder() {
        DomainEvent event = event().correlationId("req-1").build();

        byte[] body = codec.encode(event);

        String json = new String(body, StandardCharsets.UTF_8);
        assertTrue(json.startsWith("{\"eventId\":\"LEDGER_BOOK:123456:EconomicFactRecorded:8871\",\"eventType\":"
            + "\"EconomicFactRecorded\",\"schemaVersion\":1,\"aggregateType\":\"LEDGER_BOOK\",\"aggregateId\":\"123456\","
            + "\"aggregateVersion\":8871,\"occurredAt\":\"2026-10-17T10:00:00.123000Z\",\"sourceService\":\"ledger\","
            + "\"instanceId\":\"main\",\"correlationId\":\"req-1\",\"payloadHash\":\"sha256:"), json);
        DomainEvent decoded = codec.decode(body);
        assertEquals(PayloadHasher.hash(event.payload()), decoded.payloadHash());
        assertEquals(event().correlationId("req-1").payloadHash(decoded.payloadHash()).build(), decoded);
    }

    @Test
    void test_invalidParameter_mismatchedPresetHashRejectedOnEncode() {
        DomainEvent event = event().payloadHash("sha256:" + "0".repeat(64)).build();

        assertThrows(IllegalArgumentException.class, () -> codec.encode(event));
    }

    @Test
    void test_normalFlow_correlationIdOmittedWhenAbsent() {
        String json = new String(codec.encode(event().build()), StandardCharsets.UTF_8);

        assertTrue(!json.contains("correlationId"), json);
    }

    @Test
    void test_edgeCase_sizeLimit() {
        ObjectNode payload = MAPPER.createObjectNode().put("note", "x".repeat(DomainEventCodec.MAX_ENVELOPE_BYTES));

        assertThrows(DomainEventPublishException.class, () -> codec.encode(event().payload(payload).build()));
        assertThrows(InvalidDomainEventException.class,
            () -> codec.decode(new byte[DomainEventCodec.MAX_ENVELOPE_BYTES + 1]));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tamperedEnvelopes")
    void test_invalidParameter(String scenario, UnaryOperator<ObjectNode> mutation) {
        ObjectNode envelope = (ObjectNode) MAPPER.readTree(codec.encode(event().build()));

        byte[] tampered = MAPPER.writeValueAsBytes(mutation.apply(envelope));

        assertThrows(InvalidDomainEventException.class, () -> codec.decode(tampered), scenario);
    }

    @Test
    void test_invalidParameter_notJson() {
        assertThrows(InvalidDomainEventException.class,
            () -> codec.decode("not json".getBytes(StandardCharsets.UTF_8)));
        assertThrows(InvalidDomainEventException.class, () -> codec.decode("[]".getBytes(StandardCharsets.UTF_8)));
        assertThrows(InvalidDomainEventException.class, () -> codec.decode(
            "{\"eventId\":\"a\",\"eventId\":\"b\"}".getBytes(StandardCharsets.UTF_8)));
    }

    static Stream<Arguments> tamperedEnvelopes() {
        return Stream.of(
            Arguments.of("Payload 被篡改", (UnaryOperator<ObjectNode>) node -> {
                ((ObjectNode) node.get("payload")).put("amount", "99.00");
                return node;
            }),
            Arguments.of("哈希被篡改", (UnaryOperator<ObjectNode>) node -> node.put("payloadHash", "sha256:00")),
            Arguments.of("缺少字段", (UnaryOperator<ObjectNode>) node -> {
                node.remove("aggregateVersion");
                return node;
            }),
            Arguments.of("多余字段", (UnaryOperator<ObjectNode>) node -> node.put("extra", "x")),
            Arguments.of("版本是字符串", (UnaryOperator<ObjectNode>) node -> node.put("aggregateVersion", "8871")),
            Arguments.of("时间非六位小数", (UnaryOperator<ObjectNode>) node -> node.put("occurredAt",
                "2026-10-17T10:00:00.123Z")),
            Arguments.of("时间带时区偏移", (UnaryOperator<ObjectNode>) node -> node.put("occurredAt",
                "2026-10-17T14:00:00.123000+04:00")),
            Arguments.of("服务名非法", (UnaryOperator<ObjectNode>) node -> node.put("sourceService", "Ledger")));
    }

    private static DomainEvent.Builder event() {
        ObjectNode payload = MAPPER.createObjectNode().put("journalId", "9001").put("amount", "10.00");
        return DomainEvent.builder().eventType("EconomicFactRecorded").aggregate("LEDGER_BOOK", "123456")
            .aggregateVersion(8871).occurredAt(Instant.parse("2026-10-17T10:00:00.123Z")).sourceService("ledger")
            .instanceId("main").payload(payload);
    }
}
