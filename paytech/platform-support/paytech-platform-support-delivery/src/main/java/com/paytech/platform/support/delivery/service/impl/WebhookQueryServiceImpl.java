package com.paytech.platform.support.delivery.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookAttempt;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookEndpoint;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookEndpointTenantCount;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookSigningSecret;
import com.paytech.platform.support.delivery.domain.WebhookDeliveryDetailView;
import com.paytech.platform.support.delivery.domain.WebhookDeliveryFilter;
import com.paytech.platform.support.delivery.domain.WebhookDeliverySummaryView;
import com.paytech.platform.support.delivery.domain.WebhookEndpointView;
import com.paytech.platform.support.delivery.domain.WebhookEventTypes;
import com.paytech.platform.support.delivery.domain.WebhookSigningKeys;
import com.paytech.platform.support.delivery.domainservice.repository.WebhookAttemptRepository;
import com.paytech.platform.support.delivery.domainservice.repository.WebhookDeliveryRepository;
import com.paytech.platform.support.delivery.domainservice.repository.WebhookEndpointRepository;
import com.paytech.platform.support.delivery.domainservice.repository.WebhookEventRepository;
import com.paytech.platform.support.delivery.domainservice.repository.WebhookSigningSecretRepository;
import com.paytech.platform.support.delivery.service.WebhookQueryService;

import lombok.RequiredArgsConstructor;

/** 只读查询；参与调用方的只读事务，组合结果不含密钥值或 Vault Token。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "webhook.enabled", havingValue = "true")
public class WebhookQueryServiceImpl implements WebhookQueryService {

    private final WebhookEventRepository webhookEvents;
    private final WebhookAttemptRepository attempts;
    private final WebhookDeliveryRepository deliveries;
    private final WebhookEndpointRepository endpoints;
    private final WebhookSigningSecretRepository secrets;

    /** 端点按创建顺序，附当前 ACTIVE 密钥序号与宽限截止时间。 */
    @Override
    public List<WebhookEndpointView> endpoints(long tenantId, Instant now) {
        var tenantEndpoints = endpoints.findTenant(tenantId);
        var signing = secrets.findSigning(tenantEndpoints.stream().map(WebhookEndpoint::getEndpointId).toList(), now)
            .stream().collect(Collectors.groupingBy(WebhookSigningSecret::getEndpointId));
        return tenantEndpoints.stream()
            .map(endpoint -> view(endpoint, signing.getOrDefault(endpoint.getEndpointId(), List.of()), now)).toList();
    }

    /** 空列表不查询。 */
    @Override
    public Map<Long, Integer> endpointCounts(List<Long> tenantIds) {
        if (tenantIds.isEmpty()) {
            return Map.of();
        }
        return endpoints.countTenants(tenantIds).stream().collect(
            Collectors.toMap(WebhookEndpointTenantCount::getTenantId, WebhookEndpointTenantCount::getEndpointCount));
    }

    /** 数据库分页与总数使用同一条件。 */
    @Override
    public PagingResponseBody<WebhookDeliverySummaryView> deliveries(WebhookDeliveryFilter filter,
        PagingRequestBody paging) {
        return new PagingResponseBody<>(deliveries.page(filter, paging.offset(), paging.size()), paging.page(),
            paging.size(), deliveries.count(filter));
    }

    /** 投递、事件与尝试；密钥只返回序号。 */
    @Override
    public WebhookDeliveryDetailView delivery(long deliveryId) {
        var delivery = deliveries.find(deliveryId);
        if (delivery == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        var event = webhookEvents.find(delivery.getWebhookEventId());
        if (event == null) {
            throw new ErrorException("Webhook 投递的事件缺失");
        }
        Map<Long, Integer> rotations = secrets.findEndpoint(delivery.getEndpointId()).stream()
            .collect(Collectors.toMap(WebhookSigningSecret::getSigningSecretId, WebhookSigningSecret::getRotationNo));
        var attemptItems = attempts.findDelivery(deliveryId).stream().map(attempt -> attempt(attempt, rotations::get))
            .toList();
        return new WebhookDeliveryDetailView(delivery.getDeliveryId(), delivery.getEndpointId(), delivery.getStatus(),
            delivery.getAttemptCount(), delivery.getLastAttemptTime(), delivery.getReplayOfDeliveryId(),
            delivery.getRequestedBy(), delivery.getCreateTime(), event.getSourceEventKey(), event.getEventType(),
            event.getPayloadSchemaVersion(), event.getSourceSystem(), event.getPayload(), attemptItems);
    }

    /** 端点视图；签名密钥按读取时刻区分 ACTIVE 与宽限。 */
    static WebhookEndpointView view(WebhookEndpoint endpoint, List<WebhookSigningSecret> signing, Instant now) {
        var active = WebhookSigningKeys.active(signing);
        var grace = WebhookSigningKeys.grace(signing, now);
        return new WebhookEndpointView(endpoint.getEndpointId(), endpoint.getUrl(),
            WebhookEventTypes.parse(endpoint.getEventTypes()), endpoint.getStatus(), endpoint.getVersion(),
            endpoint.getCreateTime(), endpoint.getUpdateTime(), active.getRotationNo(), active.getCreateTime(),
            grace == null ? null : grace.getValidUntil());
    }

    /** 尝试视图；密钥 ID 映射为序号。 */
    static WebhookDeliveryDetailView.Attempt attempt(WebhookAttempt attempt, Function<Long, Integer> rotation) {
        Integer active = rotation.apply(attempt.getSigningSecretId());
        if (active == null) {
            throw new ErrorException("Webhook 投递尝试引用的签名密钥缺失");
        }
        return new WebhookDeliveryDetailView.Attempt(attempt.getAttemptNo(), attempt.getRequestUrl(), active,
            attempt.getGraceSecretId() == null ? null : rotation.apply(attempt.getGraceSecretId()), attempt.getStatus(),
            attempt.getHttpStatus(), attempt.getFailureCode(), attempt.getDurationMs(), attempt.getStartedTime(),
            attempt.getFinishedTime());
    }
}
