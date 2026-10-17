package com.paytech.platform.support.test.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;
import com.paytech.platform.support.api.enums.WebhookEventType;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 以来源系统身份构造内部事件请求体：合法 Envelope 加按 RFC 8785 计算的两个摘要
 * <li>摘要按 Commerce 合同在测试侧独立计算，负例可覆盖任意字段或摘要</li>
 */
public final class WebhookEventBuilder {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ObjectNode envelope = JSON.createObjectNode();
    private long clientId = WebhookFixture.CLIENT_ID;
    private long sourceVersion = 5;
    private String sourcePayloadHash;
    private String payloadHash;

    /** 一个 order.paid 事件，data.object 带订单号与版本。 */
    public static WebhookEventBuilder orderPaid(String eventId) {
        var builder = new WebhookEventBuilder();
        builder.envelope.put("eventId", eventId);
        builder.envelope.put("eventType", WebhookEventType.ORDER_PAID.getCode());
        builder.envelope.put("schemaVersion", "1");
        builder.envelope.put("createdAt", "2026-10-15T10:05:15Z");
        builder.envelope.putObject("data").putObject("object").put("orderNo", "ord_" + eventId)
            .put("orderStatus", "PAID").put("totalAmount", "42.90").put("version", 5);
        return builder;
    }

    /** 修改事件类型。 */
    public WebhookEventBuilder eventType(WebhookEventType eventType) {
        envelope.put("eventType", eventType.getCode());
        return this;
    }

    /** 修改 Client。 */
    public WebhookEventBuilder clientId(long value) {
        clientId = value;
        return this;
    }

    /** 修改来源版本。 */
    public WebhookEventBuilder sourceVersion(long value) {
        sourceVersion = value;
        return this;
    }

    /** 修改 data.object 中的一个字符串字段。 */
    public WebhookEventBuilder objectField(String name, String value) {
        ((ObjectNode) envelope.get("data").get("object")).put(name, value);
        return this;
    }

    /** 在 Envelope 顶层增加一个字段（负例）。 */
    public WebhookEventBuilder extraTopLevel(String name, String value) {
        envelope.put(name, value);
        return this;
    }

    /** 覆盖信封摘要（负例）。 */
    public WebhookEventBuilder payloadHash(String value) {
        payloadHash = value;
        return this;
    }

    /** 请求体；未覆盖的摘要按当前 Envelope 计算。 */
    public ObjectNode build() {
        var body = JSON.createObjectNode();
        body.put("clientId", Long.toString(clientId));
        body.put("sourceVersion", sourceVersion);
        body.put("sourcePayloadHash", sourcePayloadHash != null ? sourcePayloadHash
            : sha256(canonical(envelope.get("data").get("object").toString())));
        body.put("payloadHash", payloadHash != null ? payloadHash : sha256(canonicalEnvelope()));
        body.set("envelope", envelope.deepCopy());
        return body;
    }

    /** Envelope 的规范字节，即 Client 应收到的 Body。 */
    public byte[] canonicalEnvelope() {
        return canonical(envelope.toString());
    }

    /** 公开 eventId。 */
    public String eventId() {
        return envelope.get("eventId").stringValue();
    }

    /** RFC 8785 规范字节。 */
    static byte[] canonical(String json) {
        return Rfc8785JsonCanonicalizer.canonicalizeObject(json.getBytes(StandardCharsets.UTF_8));
    }

    /** SHA-256 小写十六进制。 */
    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }
}
