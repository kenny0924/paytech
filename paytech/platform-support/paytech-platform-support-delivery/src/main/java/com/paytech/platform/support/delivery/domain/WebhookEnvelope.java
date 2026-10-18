package com.paytech.platform.support.delivery.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Set;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.jsoncanonicalization.api.JsonCanonicalizationException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;

import lombok.Getter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 已校验的公开 Webhook Envelope 及其 RFC 8785 规范字节
 * <li>只允许 eventId、eventType、schemaVersion、createdAt、data 五个顶层键，data 只允许 object</li>
 * <li>规范字节就是发给 Client 的 Body；摘要均为 SHA-256 小写十六进制</li>
 */
@Getter
public final class WebhookEnvelope {

    /** 规范化 Envelope 的字节上限。 */
    public static final int MAX_CANONICAL_BYTES = 4096;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> TOP_LEVEL_KEYS = Set.of("eventId", "eventType", "schemaVersion", "createdAt",
        "data");
    private static final String EVENT_ID = "[A-Za-z0-9][A-Za-z0-9_-]{0,127}";
    private static final String CREATED_AT = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z";

    /** 公开 eventId。 */
    private final String eventId;

    /** 公开事件类型。 */
    private final String eventType;

    /** schemaVersion 的整数值。 */
    private final int schemaVersion;

    /** Envelope 规范字节。 */
    private final byte[] canonicalBytes;

    /** Envelope 规范字节摘要。 */
    private final String payloadHash;

    /** data.object 规范字节摘要。 */
    private final String sourcePayloadHash;

    /** 由 parse 构造，字段均已校验。 */
    private WebhookEnvelope(String eventId, String eventType, int schemaVersion, byte[] canonicalBytes,
        String sourcePayloadHash) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.canonicalBytes = canonicalBytes;
        this.payloadHash = sha256Hex(canonicalBytes);
        this.sourcePayloadHash = sourcePayloadHash;
    }

    /**
     * 校验来源 Envelope 结构并规范化
     *
     * @param envelope 来源提交的 JSON
     * @return 已校验的 Envelope
     * @throws InvalidParameterException 结构、字段或 JSON Object Profile 不符合，或超过 4096 字节
     */
    public static WebhookEnvelope parse(JsonNode envelope) {
        if (envelope == null || !envelope.isObject() || !keys(envelope).equals(TOP_LEVEL_KEYS)) {
            throw new InvalidParameterException("envelope 只能包含 eventId、eventType、schemaVersion、createdAt、data");
        }
        JsonNode data = envelope.get("data");
        if (!data.isObject() || !keys(data).equals(Set.of("object")) || !data.get("object").isObject()) {
            throw new InvalidParameterException("envelope.data 只能包含对象类型的 object");
        }
        String eventId = text(envelope, "eventId", EVENT_ID);
        String eventType = text(envelope, "eventType", "[a-z][a-z_]{0,30}\\.[a-z][a-z_]{0,30}");
        int schemaVersion = Integer.parseInt(text(envelope, "schemaVersion", "[1-9][0-9]{0,8}"));
        requireCreatedAt(text(envelope, "createdAt", CREATED_AT));
        byte[] canonical = canonicalize(envelope);
        if (canonical.length > MAX_CANONICAL_BYTES) {
            throw new InvalidParameterException("envelope 规范化后超过 4096 字节");
        }
        String objectHash = sha256Hex(canonicalize(data.get("object")));
        return new WebhookEnvelope(eventId, eventType, schemaVersion, canonical, objectHash);
    }

    /**
     * 重新规范化已保存的 Envelope JSON
     * <li>MySQL JSON 不保留属性顺序；发送前必须用本方法得到原规范字节并与 payload_hash 比较</li>
     *
     * @param storedJson 数据库中的 Envelope
     * @return 规范字节
     */
    public static byte[] canonicalize(String storedJson) {
        try {
            return Rfc8785JsonCanonicalizer.canonicalizeObject(storedJson.getBytes(StandardCharsets.UTF_8));
        } catch (JsonCanonicalizationException invalid) {
            throw new ErrorException("已保存的 Webhook Envelope 无法规范化", invalid);
        }
    }

    /** 规范字节的 UTF-8 文本，用于保存。 */
    public String canonicalJson() {
        return new String(canonicalBytes, StandardCharsets.UTF_8);
    }

    /** SHA-256 小写十六进制。 */
    public static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException missing) {
            throw new ErrorException("运行环境缺少 SHA-256", missing);
        }
    }

    /** 来源 JSON 节点按 JSON Object Profile 规范化。 */
    static byte[] canonicalize(JsonNode node) {
        try {
            return Rfc8785JsonCanonicalizer.canonicalizeObject(JSON.writeValueAsBytes(node));
        } catch (JacksonException | JsonCanonicalizationException invalid) {
            throw new InvalidParameterException("envelope 不符合 JSON Object Profile");
        }
    }

    /** 读取对象的全部属性名。 */
    static Set<String> keys(JsonNode node) {
        var keys = new HashSet<String>();
        node.propertyNames().forEach(keys::add);
        return keys;
    }

    /** 读取必须为字符串且匹配格式的字段。 */
    static String text(JsonNode envelope, String field, String pattern) {
        JsonNode value = envelope.get(field);
        if (!value.isString() || !value.stringValue().matches(pattern)) {
            throw new InvalidParameterException("envelope." + field + " 格式不合法");
        }
        return value.stringValue();
    }

    /** createdAt 必须是可解析的 UTC 时刻。 */
    static void requireCreatedAt(String createdAt) {
        try {
            Instant.parse(createdAt);
        } catch (DateTimeParseException invalid) {
            throw new InvalidParameterException("envelope.createdAt 不是合法的 UTC 时间");
        }
    }
}
