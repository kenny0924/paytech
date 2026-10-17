package com.paytech.foundation.messaging.core.codec;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Set;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventPublishException;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 信封 Schema v1 与 UTF-8 JSON 之间的转换（design/08 §2）。
 * <li>字段顺序固定；{@code occurredAt} 为 6 位小数的 UTC 时间；{@code correlationId} 为空时省略。</li>
 * <li>序列化后超过 {@value #MAX_ENVELOPE_BYTES} 字节拒绝发布；读取时同样限制。</li>
 * <li>读取时严格校验字段集合、类型与 {@code payloadHash}，任何不符都视为非法信封。</li>
 *
 * @author Claude
 */
public final class DomainEventCodec {

    /** 信封大小上限 64 KiB。 */
    public static final int MAX_ENVELOPE_BYTES = 64 * 1024;

    private static final Set<String> REQUIRED_FIELDS = Set.of("eventId", "eventType", "schemaVersion", "aggregateType",
        "aggregateId", "aggregateVersion", "occurredAt", "sourceService", "instanceId", "payloadHash", "payload");

    private static final Set<String> OPTIONAL_FIELDS = Set.of("correlationId");

    private static final DateTimeFormatter OCCURRED_AT = new DateTimeFormatterBuilder()
        .appendPattern("uuuu-MM-dd'T'HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 6, 6, true)
        .appendLiteral('Z')
        .toFormatter()
        .withZone(ZoneOffset.UTC);

    private final JsonMapper mapper = JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
        .build();

    /**
     * 序列化信封。
     *
     * @param event 信封
     * @return UTF-8 JSON
     * @throws DomainEventPublishException 超过大小上限
     */
    public byte[] encode(DomainEvent event) {
        ObjectNode root = mapper.createObjectNode();
        root.put("eventId", event.eventId());
        root.put("eventType", event.eventType());
        root.put("schemaVersion", event.schemaVersion());
        root.put("aggregateType", event.aggregateType());
        root.put("aggregateId", event.aggregateId());
        root.put("aggregateVersion", event.aggregateVersion());
        root.put("occurredAt", OCCURRED_AT.format(event.occurredAt()));
        root.put("sourceService", event.sourceService());
        root.put("instanceId", event.instanceId());
        if (event.correlationId() != null) {
            root.put("correlationId", event.correlationId());
        }
        root.put("payloadHash", event.payloadHash());
        root.set("payload", event.payload());
        byte[] bytes = mapper.writeValueAsBytes(root);
        if (bytes.length > MAX_ENVELOPE_BYTES) {
            throw new DomainEventPublishException("领域事件信封超过 64 KiB 上限: " + event.eventType() + ", 大小 "
                + bytes.length + " 字节");
        }
        return bytes;
    }

    /**
     * 解析并校验收到的信封。
     *
     * @param body 消息体
     * @return 已校验的信封
     * @throws InvalidDomainEventException 大小、JSON、字段集合、类型、格式或哈希不合法
     */
    public DomainEvent decode(byte[] body) {
        if (body == null || body.length == 0 || body.length > MAX_ENVELOPE_BYTES) {
            throw invalid("envelope size is outside 1.." + MAX_ENVELOPE_BYTES + " bytes", null);
        }
        JsonNode root = readTree(body);
        requireFieldSet(root);
        DomainEvent event = build(root);
        if (!event.payloadHash().equals(text(root, "payloadHash"))) {
            throw invalid("payloadHash does not match payload", null);
        }
        return event;
    }

    private JsonNode readTree(byte[] body) {
        try {
            JsonNode root = mapper.readTree(body);
            if (root == null || !root.isObject()) {
                throw invalid("envelope must be a JSON object", null);
            }
            return root;
        } catch (JacksonException exception) {
            throw invalid("envelope is not valid JSON", exception);
        }
    }

    private static void requireFieldSet(JsonNode root) {
        for (String field : REQUIRED_FIELDS) {
            if (!root.has(field)) {
                throw invalid("envelope field is missing: " + field, null);
            }
        }
        for (String field : root.propertyNames()) {
            if (!REQUIRED_FIELDS.contains(field) && !OPTIONAL_FIELDS.contains(field)) {
                throw invalid("envelope field is not allowed: " + field, null);
            }
        }
    }

    private static DomainEvent build(JsonNode root) {
        try {
            return DomainEvent.builder()
                .eventId(text(root, "eventId"))
                .eventType(text(root, "eventType"))
                .schemaVersion(intValue(root, "schemaVersion"))
                .aggregate(text(root, "aggregateType"), text(root, "aggregateId"))
                .aggregateVersion(longValue(root, "aggregateVersion"))
                .occurredAt(instant(root))
                .sourceService(text(root, "sourceService"))
                .instanceId(text(root, "instanceId"))
                .correlationId(root.has("correlationId") ? text(root, "correlationId") : null)
                .payload(root.get("payload"))
                .build();
        } catch (IllegalArgumentException exception) {
            throw invalid("envelope field is invalid: " + exception.getMessage(), exception);
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isString()) {
            throw invalid("envelope field must be a string: " + field, null);
        }
        return value.asString();
    }

    private static int intValue(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isInt()) {
            throw invalid("envelope field must be an integer: " + field, null);
        }
        return value.intValue();
    }

    private static long longValue(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw invalid("envelope field must be an integer: " + field, null);
        }
        return value.longValue();
    }

    private static Instant instant(JsonNode root) {
        String text = text(root, "occurredAt");
        try {
            Instant value = Instant.parse(text);
            if (!OCCURRED_AT.format(value).equals(text)) {
                throw invalid("occurredAt must be UTC with six fractional digits", null);
            }
            return value;
        } catch (java.time.format.DateTimeParseException exception) {
            throw invalid("occurredAt is not an ISO-8601 instant", exception);
        }
    }

    private static InvalidDomainEventException invalid(String message, Throwable cause) {
        return new InvalidDomainEventException(message, cause);
    }
}
