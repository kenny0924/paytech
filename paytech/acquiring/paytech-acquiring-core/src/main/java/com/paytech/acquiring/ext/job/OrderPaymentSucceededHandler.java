package com.paytech.acquiring.ext.job;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.acquiring.core.configuration.AcquiringEventProperties;
import com.paytech.acquiring.domain.order.OrderPaymentEventSource;
import com.paytech.acquiring.domain.order.OrderPaymentFacts;
import com.paytech.acquiring.domain.order.OrderPaymentSucceededPayload;
import com.paytech.acquiring.domainservice.service.order.OrderPaymentEventService;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.messaging.api.AggregateKey;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventPublisher;

import lombok.AllArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 订单进入 PAID 提交后发布 OrderPaymentSucceeded
 * <li>eventKey 为 {@code AggregateKey.eventKey}：{@code ORDER:<orderId>:OrderPaymentSucceeded:<orderVersion>}；payloadJson 只保存
 * acquiring 没有持久化来源的支付事实，订单冻结商业字段与 Intent 在执行时回读。</li>
 * <li>经 {@link DomainEventPublisher} 发布到 acquiring.events；发布前后崩溃只会重复发布同一 eventId，消费方按 eventId 与成功尝试幂等。</li>
 * <li>任务元数据或支付事实缺失、订单或冻结上下文不存在、支付投影的成功尝试与登记不符为终态失败，保留记录供人工处理；Broker 未确认、
 * 未路由或连接失败可重试。</li>
 * <li>日志与错误摘要不记录载荷内容。</li>
 */
@Component
@AllArgsConstructor
public class OrderPaymentSucceededHandler implements CompensationEventHandler {
    /** 补偿事件类型。 */
    public static final String EVENT_TYPE = "ORDER_PAYMENT_SUCCEEDED";

    /** 无业务子类型的占位值。 */
    static final String NO_SUB_EVENT_TYPE = "-";

    private static final String ID = "[1-9][0-9]{0,18}";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final DomainEventPublisher domainEventPublisher;
    private final AcquiringEventProperties eventProperties;
    private final OrderPaymentEventService orderPaymentEventService;

    /**
     * 纯工厂：只能与订单进入 PAID 在同一本地事务登记
     *
     * @param orderId 订单 ID
     * @param orderVersion 推进 PAID 后的订单版本
     * @param facts 事件携带的支付事实
     * @return eventKey 为四段式事件键、payloadJson 为支付事实的登记请求
     */
    public static CompensationEventRequest buildEvent(long orderId, long orderVersion, OrderPaymentFacts facts) {
        if (orderId <= 0 || orderVersion <= 0) {
            throw new IllegalArgumentException("Invalid order payment identity");
        }
        String eventKey = AggregateKey.of(OrderPaymentSucceededPayload.AGGREGATE_TYPE, Long.toString(orderId))
            .eventKey(OrderPaymentSucceededPayload.EVENT_TYPE, orderVersion);
        ObjectNode payload = JSON.createObjectNode();
        payload.put("paymentAttemptId", Long.toString(facts.getPaymentAttemptId()));
        payload.put("capturedAmount", facts.getCapturedAmount().toPlainString());
        payload.put("paymentSucceededTime", facts.getPaymentSucceededTime().toString());
        payload.put("paymentMethodCode", facts.getPaymentMethodCode());
        payload.put("channelId", facts.getChannelId());
        payload.put("channelAccountId", facts.getChannelAccountId());
        payload.put("midId", facts.getMidId());
        payload.put("channelTransactionRef", facts.getChannelTransactionRef());
        payload.put("channelTransactionRefId", facts.getChannelTransactionRefId());
        payload.put("channelReferenceType", facts.getChannelReferenceType());
        payload.put("cardBrand", facts.getCardBrand());
        payload.put("fundingType", facts.getFundingType());
        payload.put("cardLevel", facts.getCardLevel());
        return new CompensationEventRequest(eventKey, EVENT_TYPE, NO_SUB_EVENT_TYPE, payload);
    }

    /** 补偿事件类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 每次尝试 20 秒，最多 12 次，5 秒起指数退避至 30 分钟。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(20), 12, Duration.ofSeconds(5), Duration.ofMinutes(30),
            2, 0.2);
    }

    /**
     * 回读订单事实并发布订单支付成功事件
     * <li>必须在事务外执行；元数据、支付事实或本库事实不满足发布条件为终态失败，发布失败可重试。</li>
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompensationHandlerResult.terminalFailure("ORDER_PAYMENT_TRANSACTION_ACTIVE",
                "Order payment event must publish outside a transaction");
        }
        AggregateKey.ParsedEventKey key = parse(event);
        OrderPaymentFacts facts = key == null ? null : facts(event.payloadJson());
        if (facts == null) {
            return CompensationHandlerResult.terminalFailure("ORDER_PAYMENT_EVENT_INVALID",
                "Order payment event is malformed");
        }
        long orderId = Long.parseLong(key.aggregateKey().aggregateId());
        OrderPaymentEventSource source = orderPaymentEventService.loadEventSource(orderId);
        if (!source.publishable(facts.getPaymentAttemptId())) {
            return CompensationHandlerResult.terminalFailure("ORDER_PAYMENT_NOT_PUBLISHABLE",
                "Order context or successful attempt does not match the registered payment");
        }
        try {
            domainEventPublisher.publish(envelope(orderId, key.aggregateVersion(), source, facts));
        } catch (RuntimeException unavailable) {
            // 不把 Broker 返回信息写入任务错误摘要。
            return CompensationHandlerResult.retryableFailure("ORDER_PAYMENT_UNPUBLISHED",
                "Order payment publication requires retry");
        }
        return CompensationHandlerResult.success();
    }

    /** 解析并核对事件键：聚合类型、事件类型与十进制订单 ID；不符合返回空。 */
    static AggregateKey.ParsedEventKey parse(CompensationEvent event) {
        if (event.payloadJson() == null || !NO_SUB_EVENT_TYPE.equals(event.subEventType())) {
            return null;
        }
        try {
            AggregateKey.ParsedEventKey key = AggregateKey.parseEventKey(event.eventKey());
            boolean expected = OrderPaymentSucceededPayload.AGGREGATE_TYPE.equals(key.aggregateKey().aggregateType())
                && OrderPaymentSucceededPayload.EVENT_TYPE.equals(key.eventType())
                && key.aggregateKey().aggregateId().matches(ID);
            return expected ? key : null;
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /**
     * 从 payloadJson 还原登记的支付事实
     * <li>成功尝试、已捕获金额、成功时间、支付方式、渠道、渠道账户、MID 与外部交易号必填；渠道交易引用 ID、类型与卡属性可空。</li>
     *
     * @return 支付事实；缺失或格式不符时返回空
     */
    static OrderPaymentFacts facts(String payloadJson) {
        try {
            JsonNode node = JSON.readTree(payloadJson);
            return new OrderPaymentFacts(Long.parseLong(required(node, "paymentAttemptId", ID)),
                new BigDecimal(required(node, "capturedAmount", "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?")),
                Instant.parse(required(node, "paymentSucceededTime", ".+")), required(node, "paymentMethodCode", ".+"),
                required(node, "channelId", ID), required(node, "channelAccountId", ID), required(node, "midId", ID),
                required(node, "channelTransactionRef", ".+"), optional(node, "channelTransactionRefId"),
                optional(node, "channelReferenceType"), optional(node, "cardBrand"), optional(node, "fundingType"),
                optional(node, "cardLevel"));
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    /** 必填且符合格式的字符串字段；不符合抛出，由调用方转为终态失败。 */
    static String required(JsonNode node, String field, String pattern) {
        String value = optional(node, field);
        if (value == null || !value.matches(pattern)) {
            throw new IllegalArgumentException("Order payment fact is missing");
        }
        return value;
    }

    /** 可空字符串字段：缺失或 JSON null 为空，其他非字符串类型抛出。 */
    static String optional(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw new IllegalArgumentException("Order payment fact is malformed");
        }
        return value.asString();
    }

    /** OrderPaymentSucceeded Schema v1 信封；eventId 默认取四段式事件键，补偿重试不变；occurredAt 为支付成功时间。 */
    DomainEvent envelope(long orderId, long orderVersion, OrderPaymentEventSource source,
        OrderPaymentFacts facts) {
        return DomainEvent.builder().eventType(OrderPaymentSucceededPayload.EVENT_TYPE)
            .schemaVersion(OrderPaymentSucceededPayload.SCHEMA_VERSION)
            .aggregate(OrderPaymentSucceededPayload.AGGREGATE_TYPE, Long.toString(orderId))
            .aggregateVersion(orderVersion).occurredAt(facts.getPaymentSucceededTime())
            .sourceService(eventProperties.getSourceService()).instanceId(eventProperties.getInstanceId())
            .payload(OrderPaymentSucceededPayload.json(source, facts)).build();
    }
}
