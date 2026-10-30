package com.paytech.billing.ext.mq;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

import com.paytech.billing.core.enums.BillingCodes;
import com.paytech.billing.core.enums.OrderTaxStatus;
import com.paytech.billing.domain.OrderCommercialContext;
import com.paytech.billing.domain.OrderPaymentSucceededFact;
import com.paytech.billing.domain.OrderTaxCalculation;
import com.paytech.billing.domain.OrderTaxComponent;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventRejectedException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * acquiring OrderPaymentSucceeded Schema v1 信封到 {@link OrderPaymentSucceededFact} 的校验与转换（技术方案 4.3 S13）
 * <li>信封结构与 payloadHash 已由 paytech-messaging 解码器校验；这里校验来源服务 acquiring、事件类型、Schema 版本 1、聚合类型 ORDER 且
 * 聚合 ID 等于 orderId，以及 billing 使用的必填字段格式。acquiring-api 不发布载荷类，按 4.3 字段表本地解析。</li>
 * <li>可空字段为 JSON null 或缺失时为空；给出时必须符合格式。</li>
 * <li>税务组（技术方案 11.1，schema 1 可空新增）：taxCalculationId 缺失时 taxStatus、taxScopeKey、taxAmount、taxComponents 必须同时缺失，
 * 表示存量订单；给出时 taxStatus 必填，组件逐项校验格式。旧字段 financeTaxSnapshotId 继续读取。</li>
 * <li>来源载荷 Hash 为 Payload 的 RFC 8785 规范 bytes 的 SHA-256 小写十六进制（同键重投时比对）。</li>
 * <li>任何不合法都抛出 {@link DomainEventRejectedException}（直接进入 DLQ），异常消息不回显消息内容。</li>
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

    private static final Pattern ENTITY_NO = Pattern.compile("[A-Z]{2}[0-9]{3}");

    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    private static final Pattern COUNTRY = Pattern.compile("[A-Z]{2}");

    private static final Pattern MCC = Pattern.compile("[0-9]{4}");

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    private static final Pattern REFERENCE = Pattern.compile("[\\x21-\\x7E]{1,128}");

    /** 只在 taxCalculationId 给出时允许出现的税务字段。 */
    private static final List<String> TAX_DETAIL_FIELDS = List.of("taxStatus", "taxScopeKey", "taxAmount", "taxComponents");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 纯静态转换。 */
    private OrderPaymentSucceededEventReader() {
    }

    /**
     * 校验信封并读取订单支付成功事实
     *
     * @param event 已由 paytech-messaging 解码的信封
     * @return 订单支付成功事实
     * @throws DomainEventRejectedException 信封身份或 Payload 不合法
     */
    public static OrderPaymentSucceededFact read(DomainEvent event) {
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
        boolean valid = OrderPaymentSucceededFact.SOURCE_SYSTEM.equals(event.sourceService())
            && EVENT_TYPE.equals(event.eventType()) && event.schemaVersion() == SCHEMA_VERSION
            && AGGREGATE_TYPE.equals(event.aggregateType()) && event.aggregateVersion() > 0
            && event.aggregateId().equals(matched(payload, "orderId", ID));
        if (!valid) {
            throw invalid(event, null);
        }
    }

    /** 支付组字段与订单组。 */
    static OrderPaymentSucceededFact toFact(DomainEvent event, JsonNode payload) {
        return OrderPaymentSucceededFact.builder().eventId(event.eventId()).aggregateVersion(event.aggregateVersion())
            .payloadHash(payloadHash(payload)).order(toOrder(payload))
            .paymentIntentId(id(payload, "paymentIntentId")).paymentAttemptId(id(payload, "paymentAttemptId"))
            .capturedAmount(amount(payload, "capturedAmount"))
            .paymentSucceededTime(Instant.parse(text(payload, "paymentSucceededTime")))
            .paymentMethodCode(matched(payload, "paymentMethodCode", CODE)).channelId(id(payload, "channelId"))
            .channelAccountId(id(payload, "channelAccountId")).midId(id(payload, "midId"))
            .channelTransactionRef(optional(payload, "channelTransactionRef", REFERENCE))
            .channelTransactionRefId(optionalId(payload, "channelTransactionRefId"))
            .channelReferenceType(optional(payload, "channelReferenceType", CODE))
            .cardBrand(optional(payload, "cardBrand", CODE)).fundingType(optional(payload, "fundingType", CODE))
            .cardLevel(optional(payload, "cardLevel", CODE)).build();
    }

    /** 订单组：订单、Client、OE、金额、币种、商业时点、Client 合同与条款版本、可选 Partner 五项、税务快照、tax 计算与消费者国家。 */
    static OrderCommercialContext toOrder(JsonNode payload) {
        return OrderCommercialContext.builder().orderId(id(payload, "orderId"))
            .orderContextId(id(payload, "orderContextId")).clientId(id(payload, "clientId"))
            .entityNo(matched(payload, "entityNo", ENTITY_NO)).localPartnerId(optionalId(payload, "localPartnerId"))
            .industryCode(optional(payload, "industryCode", REFERENCE)).mccCode(optional(payload, "mccCode", MCC))
            .orderAmount(amount(payload, "orderAmount")).currency(matched(payload, "currency", CURRENCY))
            .commercialContextTime(Instant.parse(text(payload, "commercialContextTime")))
            .clientAgreementId(id(payload, "clientAgreementId"))
            .clientPricingVersionId(id(payload, "clientPricingVersionId"))
            .partnerAgreementId(optionalId(payload, "partnerAgreementId"))
            .partnerAgreementOperatingEntityId(optionalId(payload, "partnerAgreementOperatingEntityId"))
            .partnerShareVersionId(optionalId(payload, "partnerShareVersionId"))
            .platformChargeVersionId(optionalId(payload, "platformChargeVersionId"))
            .platformServicePartyId(optionalId(payload, "platformServicePartyId"))
            .financeTaxSnapshotId(optionalId(payload, "financeTaxSnapshotId")).taxCalculation(taxCalculation(payload))
            .consumerCountryCode(optional(payload, "consumerCountryCode", COUNTRY)).build();
    }

    /** 税务组：缺 taxCalculationId 为空且其余税务字段必须缺失；给出时状态必填、组件逐项读取。 */
    static OrderTaxCalculation taxCalculation(JsonNode payload) {
        Long calculationId = optionalId(payload, "taxCalculationId");
        if (calculationId == null) {
            boolean detailPresent = TAX_DETAIL_FIELDS.stream().map(payload::path)
                .anyMatch(value -> !value.isMissingNode() && !value.isNull());
            if (detailPresent) {
                throw new IllegalArgumentException("tax fields without taxCalculationId");
            }
            return null;
        }
        String amount = optional(payload, "taxAmount", AMOUNT);
        return OrderTaxCalculation.builder().taxCalculationId(calculationId)
            .taxStatus(BillingCodes.parse(OrderTaxStatus.class, "taxStatus", matched(payload, "taxStatus", CODE)))
            .taxScopeKey(optional(payload, "taxScopeKey", CODE)).taxAmount(amount == null ? null : new BigDecimal(amount))
            .components(taxComponents(payload.path("taxComponents"))).build();
    }

    /** 税务组件数组：缺失或 JSON null 为空列表；每项为对象，未知字段忽略。 */
    static List<OrderTaxComponent> taxComponents(JsonNode array) {
        if (array.isMissingNode() || array.isNull()) {
            return List.of();
        }
        if (!array.isArray()) {
            throw new IllegalArgumentException("malformed taxComponents");
        }
        List<OrderTaxComponent> components = new ArrayList<>();
        for (JsonNode item : array) {
            if (!item.isObject()) {
                throw new IllegalArgumentException("malformed taxComponents item");
            }
            components.add(OrderTaxComponent.builder().jurisdictionCode(matched(item, "jurisdictionCode", REFERENCE))
                .taxType(optional(item, "taxType", CODE))
                .registrationId(optionalId(item, "registrationId")).taxSubjectPartyId(id(item, "taxSubjectPartyId"))
                .taxAmount(amount(item, "taxAmount")).build());
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

    /** 可空字符串字段：缺失或 JSON null 为空，给出时必须符合格式。 */
    static String optional(JsonNode node, String field, Pattern pattern) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return matched(node, field, pattern);
    }

    /** 必填正 BIGINT 字符串。 */
    static long id(JsonNode node, String field) {
        return Long.parseLong(matched(node, field, ID));
    }

    /** 可空正 BIGINT 字符串。 */
    static Long optionalId(JsonNode node, String field) {
        String value = optional(node, field, ID);
        return value == null ? null : Long.parseLong(value);
    }

    /** 必填非负十进制金额字符串，不接受科学计数法。 */
    static BigDecimal amount(JsonNode node, String field) {
        return new BigDecimal(matched(node, field, AMOUNT));
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
