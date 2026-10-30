package com.paytech.tax.ext.mq;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventRejectedException;
import com.paytech.tax.core.constant.TaxCodes;
import com.paytech.tax.domain.registration.OrderPaymentTaxFact;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * acquiring OrderPaymentSucceeded Schema v1 信封到 {@link OrderPaymentTaxFact} 的校验与转换（technique 11.1）
 * <li>信封结构与 payloadHash 已由 paytech-messaging 解码器校验；这里校验来源服务 acquiring、事件类型、Schema 版本 1、聚合类型 ORDER 且
 * 聚合 ID 等于 orderId，并只读取 tax 使用的字段：订单、币种、支付成功时间与税务组。</li>
 * <li>税务组可空新增：taxCalculationId 缺失或为 null 时 taxStatus、taxScopeKey、taxAmount、taxComponents 必须同时缺失或为 null（存量订单）；
 * 给出时 taxStatus、taxScopeKey 必填，组件逐项校验格式。</li>
 * <li>来源载荷 Hash 为 Payload 的 RFC 8785 规范 bytes 的 SHA-256 小写十六进制（与 billing 同一口径）。</li>
 * <li>任何不合法抛出 {@link DomainEventRejectedException}（直接进入 DLQ），消息不回显内容。</li>
 */
public final class OrderPaymentSucceededEventReader {
    /** 事件类型。 */
    static final String EVENT_TYPE = "OrderPaymentSucceeded";

    /** 接受的 Payload Schema 版本。 */
    static final int SCHEMA_VERSION = 1;

    /** 聚合类型。 */
    static final String AGGREGATE_TYPE = "ORDER";

    private static final Pattern ID = Pattern.compile("[1-9][0-9]{0,18}");

    private static final Pattern AMOUNT = Pattern.compile("(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?");

    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    private static final Pattern COUNTRY = Pattern.compile("[A-Z]{2}");

    private static final List<String> TAX_DETAIL_FIELDS = List.of("taxStatus", "taxScopeKey", "taxAmount", "taxComponents");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 纯静态转换。 */
    private OrderPaymentSucceededEventReader() {
    }

    /**
     * 校验信封并读取 tax 登记事实
     *
     * @param event 已由 paytech-messaging 解码的信封
     * @return 登记事实
     * @throws DomainEventRejectedException 信封身份或 Payload 不合法
     */
    public static OrderPaymentTaxFact read(DomainEvent event) {
        try {
            JsonNode payload = event.payload();
            checkEnvelope(event, payload);
            return toFact(event, payload);
        } catch (DomainEventRejectedException rejected) {
            throw rejected;
        } catch (RuntimeException malformed) {
            throw invalid(event, malformed);
        }
    }

    /** 来源服务、事件类型、Schema 版本与聚合身份。 */
    static void checkEnvelope(DomainEvent event, JsonNode payload) {
        boolean valid = TaxCodes.SOURCE_SYSTEM_ACQUIRING.equals(event.sourceService())
            && EVENT_TYPE.equals(event.eventType()) && event.schemaVersion() == SCHEMA_VERSION
            && AGGREGATE_TYPE.equals(event.aggregateType()) && event.aggregateVersion() > 0
            && event.aggregateId().equals(matched(payload, "orderId", ID));
        if (!valid) {
            throw invalid(event, null);
        }
    }

    /** 订单、币种、支付时间与税务组。 */
    static OrderPaymentTaxFact toFact(DomainEvent event, JsonNode payload) {
        String calculationId = optional(payload, "taxCalculationId", ID);
        if (calculationId == null) {
            requireNoTaxDetail(payload);
        }
        String taxAmount = optional(payload, "taxAmount", AMOUNT);
        return new OrderPaymentTaxFact(event.eventId(), payloadHash(payload), Long.parseLong(matched(payload, "orderId", ID)),
            matched(payload, "currency", CURRENCY), Instant.parse(text(payload, "paymentSucceededTime")),
            calculationId == null ? null : Long.parseLong(calculationId),
            calculationId == null ? null : matched(payload, "taxStatus", CODE),
            calculationId == null ? null : matched(payload, "taxScopeKey", CODE),
            taxAmount == null ? null : new BigDecimal(taxAmount), components(payload.path("taxComponents")));
    }

    /** 存量订单：除 taxCalculationId 外的税务字段也必须缺失或为 null。 */
    static void requireNoTaxDetail(JsonNode payload) {
        boolean present = TAX_DETAIL_FIELDS.stream().map(payload::path)
            .anyMatch(value -> !value.isMissingNode() && !value.isNull());
        if (present) {
            throw new IllegalArgumentException("tax fields without taxCalculationId");
        }
    }

    /** 税务组件数组：缺失或 null 为空列表。 */
    static List<OrderPaymentTaxFact.Component> components(JsonNode array) {
        if (array.isMissingNode() || array.isNull()) {
            return List.of();
        }
        if (!array.isArray()) {
            throw new IllegalArgumentException("malformed taxComponents");
        }
        List<OrderPaymentTaxFact.Component> components = new ArrayList<>();
        for (JsonNode item : array) {
            if (!item.isObject()) {
                throw new IllegalArgumentException("malformed taxComponents item");
            }
            String registrationId = optional(item, "registrationId", ID);
            components.add(new OrderPaymentTaxFact.Component(matched(item, "jurisdictionCode", COUNTRY),
                registrationId == null ? null : Long.parseLong(registrationId),
                Long.parseLong(matched(item, "taxSubjectPartyId", ID)), new BigDecimal(matched(item, "taxAmount", AMOUNT))));
        }
        return List.copyOf(components);
    }

    /** 必填字符串字段。 */
    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isString()) {
            throw new IllegalArgumentException("missing " + field);
        }
        return value.asString();
    }

    /** 必填且符合格式的字符串字段。 */
    static String matched(JsonNode node, String field, Pattern pattern) {
        String value = text(node, field);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("malformed " + field);
        }
        return value;
    }

    /** 可空字符串字段：缺失或 null 为空，给出时必须符合格式。 */
    static String optional(JsonNode node, String field, Pattern pattern) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return matched(node, field, pattern);
    }

    /** Payload 的 RFC 8785 规范 bytes 的 SHA-256 小写十六进制。 */
    static String payloadHash(JsonNode payload) {
        byte[] canonical = Rfc8785JsonCanonicalizer.canonicalizeObject(JSON.writeValueAsBytes(payload));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException missing) {
            throw new ErrorException("运行环境缺少 SHA-256", missing);
        }
    }

    /** 不回显消息内容的拒绝。 */
    static DomainEventRejectedException invalid(DomainEvent event, Throwable cause) {
        return new DomainEventRejectedException("订单支付成功事件不合法: " + event.eventId(), cause);
    }
}
