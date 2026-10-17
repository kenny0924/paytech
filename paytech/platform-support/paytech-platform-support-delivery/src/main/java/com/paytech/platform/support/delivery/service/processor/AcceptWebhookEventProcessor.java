package com.paytech.platform.support.delivery.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.enums.WebhookEventType;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookDelivery;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookEvent;
import com.paytech.platform.support.delivery.core.enums.WebhookDeliveryStatus;
import com.paytech.platform.support.delivery.domain.AcceptWebhookEventContext;
import com.paytech.platform.support.delivery.domain.WebhookEnvelope;
import com.paytech.platform.support.delivery.domainservice.repository.WebhookEndpointRepository;
import com.paytech.platform.support.delivery.domainservice.repository.WebhookEventRepository;
import com.paytech.platform.support.delivery.service.WebhookAcceptanceService;
import com.paytech.platform.support.delivery.service.processor.request.AcceptWebhookEventRequest;

import lombok.RequiredArgsConstructor;

/**
 * 模块公开入口：受理冻结事件并按当前 ACTIVE 订阅生成投递
 * <li>同一 (租户, eventId, 来源系统) 内容完全相同视为已受理；任一不同 Fail-Closed 返回并发冲突</li>
 * <li>只登记补偿任务，不同步发送；没有订阅也受理并保存事件</li>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "webhook.enabled", havingValue = "true")
public class AcceptWebhookEventProcessor extends AbstractProcessor<AcceptWebhookEventRequest, Void> {

    private final Clock clock;
    private final IdGenerator ids;
    private final WebhookEventRepository events;
    private final WebhookAcceptanceService service;
    private final WebhookEndpointRepository endpoints;

    @Override
    protected Void processRequest(AcceptWebhookEventRequest request) {
        // 校验信封与摘要
        var envelope = WebhookEnvelope.parse(request.getEnvelope());
        checkEnvelope(envelope, request);

        // 原事件比较
        var existing = events.findSource(request.getTenantId(), envelope.getEventId(), request.getSourceSystem());
        if (existing != null) {
            requireSameEvent(existing, envelope, request);
            return null;
        }

        // 保存事件与投递
        try {
            service.accept(buildContext(envelope, request));
        } catch (DuplicateKeyException concurrent) {
            requireSameEvent(events.findSource(request.getTenantId(), envelope.getEventId(),
                request.getSourceSystem()), envelope, request);
        }
        return null;
    }

    /** 摘要必须与 Platform 重新计算的一致；事件类型必须是可订阅的 V1 类型。 */
    void checkEnvelope(WebhookEnvelope envelope, AcceptWebhookEventRequest request) {
        if (!envelope.getSourcePayloadHash().equals(request.getSourcePayloadHash())
            || !envelope.getPayloadHash().equals(request.getPayloadHash())) {
            throw new InvalidParameterException("sourcePayloadHash 或 payloadHash 与 envelope 不一致");
        }
        var type = WebhookEventType.fromCode(envelope.getEventType());
        if (type == null || !type.isSubscribable()) {
            throw new InvalidParameterException("envelope.eventType 不是 V1 公开事件类型");
        }
    }

    /** 同一来源身份的五项不可变内容必须完全一致。 */
    void requireSameEvent(WebhookEvent existing, WebhookEnvelope envelope, AcceptWebhookEventRequest request) {
        if (existing == null) {
            throw new ErrorException("Webhook 事件唯一冲突后未读到原事件");
        }
        boolean same = existing.getEventType().equals(envelope.getEventType())
            && existing.getPayloadSchemaVersion() == envelope.getSchemaVersion()
            && existing.getSourceVersion() == request.getSourceVersion()
            && existing.getSourcePayloadHash().equals(envelope.getSourcePayloadHash())
            && existing.getPayloadHash().equals(envelope.getPayloadHash());
        if (!same) {
            throw new ConcurrentRequestException("该 eventId 已以不同内容受理: " + envelope.getEventId());
        }
    }

    /** 事件与当前订阅该类型的 ACTIVE 端点的投递。 */
    AcceptWebhookEventContext buildContext(WebhookEnvelope envelope, AcceptWebhookEventRequest request) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var event = new WebhookEvent();
        event.setEventId(ids.nextId());
        event.setTenantId(request.getTenantId());
        event.setSourceSystem(request.getSourceSystem());
        event.setSourceEventKey(envelope.getEventId());
        event.setEventType(envelope.getEventType());
        event.setPayloadSchemaVersion(envelope.getSchemaVersion());
        event.setSourceVersion(request.getSourceVersion());
        event.setSourcePayloadHash(envelope.getSourcePayloadHash());
        event.setPayloadHash(envelope.getPayloadHash());
        event.setPayload(envelope.canonicalJson());
        event.setCreateTime(now);
        List<WebhookDelivery> deliveries = endpoints.findTenant(request.getTenantId()).stream()
            .filter(endpoint -> endpoint.accepts(envelope.getEventType()))
            .map(endpoint -> newDelivery(event, endpoint.getEndpointId(), now)).toList();
        return new AcceptWebhookEventContext(event, deliveries);
    }

    /** 原始投递：CREATED、尚未尝试。 */
    WebhookDelivery newDelivery(WebhookEvent event, long endpointId, Instant now) {
        var delivery = new WebhookDelivery();
        delivery.setDeliveryId(ids.nextId());
        delivery.setWebhookEventId(event.getEventId());
        delivery.setTenantId(event.getTenantId());
        delivery.setEndpointId(endpointId);
        delivery.setEventType(event.getEventType());
        delivery.setStatus(WebhookDeliveryStatus.CREATED);
        delivery.setVersion(1);
        delivery.setCreateTime(now);
        delivery.setUpdateTime(now);
        return delivery;
    }
}
