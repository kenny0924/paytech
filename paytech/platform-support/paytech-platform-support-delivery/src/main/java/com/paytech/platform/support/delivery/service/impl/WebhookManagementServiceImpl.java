package com.paytech.platform.support.delivery.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.delivery.core.configuration.WebhookProperties;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookDelivery;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookEndpoint;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookSigningSecret;
import com.paytech.platform.support.delivery.core.enums.WebhookAuditField;
import com.paytech.platform.support.delivery.core.enums.WebhookDeliveryStatus;
import com.paytech.platform.support.delivery.core.enums.WebhookEndpointStatus;
import com.paytech.platform.support.delivery.core.enums.WebhookSecretStatus;
import com.paytech.platform.support.delivery.domain.ChangeWebhookEndpointStatusCommand;
import com.paytech.platform.support.delivery.domain.CreateWebhookEndpointCommand;
import com.paytech.platform.support.delivery.domain.ReplayWebhookDeliveryCommand;
import com.paytech.platform.support.delivery.domain.RotateWebhookSecretCommand;
import com.paytech.platform.support.delivery.domain.RotateWebhookSecretResult;
import com.paytech.platform.support.delivery.domain.UpdateWebhookEndpointCommand;
import com.paytech.platform.support.delivery.domain.WebhookConfigurationChange;
import com.paytech.platform.support.delivery.domain.WebhookEventTypes;
import com.paytech.platform.support.delivery.domain.WebhookFieldChange;
import com.paytech.platform.support.delivery.domain.WebhookSigningKeys;
import com.paytech.platform.support.delivery.domain.WebhookSigningTarget;
import com.paytech.platform.support.delivery.domainservice.repository.WebhookDeliveryRepository;
import com.paytech.platform.support.delivery.domainservice.repository.WebhookEndpointRepository;
import com.paytech.platform.support.delivery.domainservice.repository.WebhookSigningSecretRepository;
import com.paytech.platform.support.delivery.ext.eventhandler.WebhookDeliveryHandler;
import com.paytech.platform.support.delivery.service.WebhookManagementService;

import lombok.RequiredArgsConstructor;

/** 参与调用方事务；规则检查与写入都只针对本库，写入由版本条件与唯一键最终保护。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "webhook.enabled", havingValue = "true")
public class WebhookManagementServiceImpl implements WebhookManagementService {

    private final IdGenerator ids;
    private final WebhookProperties properties;
    private final CompensationEventRegistrar events;
    private final WebhookDeliveryRepository deliveries;
    private final WebhookEndpointRepository endpoints;
    private final WebhookSigningSecretRepository secrets;

    /** 端点数达到上限时拒绝；端点与首个密钥一起插入。 */
    @Override
    public WebhookConfigurationChange createEndpoint(CreateWebhookEndpointCommand command) {
        int limit = properties.getMaximumEndpointsPerClient();
        if (endpoints.findTenant(command.getTenantId()).size() >= limit) {
            throw new BizCheckFailException("每个 Client 最多配置 " + limit + " 个 Webhook 端点");
        }
        var endpoint = new WebhookEndpoint();
        endpoint.setEndpointId(ids.nextId());
        endpoint.setTenantId(command.getTenantId());
        endpoint.setUrl(command.getUrl());
        endpoint.setEventTypes(WebhookEventTypes.format(command.getEventTypes()));
        endpoint.setStatus(WebhookEndpointStatus.ACTIVE);
        endpoint.setVersion(1);
        endpoint.setCreateTime(command.getNow());
        endpoint.setUpdateTime(command.getNow());
        endpoints.insert(endpoint);
        secrets.insert(newSecret(endpoint.getEndpointId(), 1, command.getSecretVaultToken(), command.getNow()));
        return new WebhookConfigurationChange(endpoint.getEndpointId(), 0, 1, List.of(
            new WebhookFieldChange(WebhookAuditField.ENDPOINT_URL, null, command.getUrl()),
            new WebhookFieldChange(WebhookAuditField.EVENT_TYPES, null, String.join(",", command.getEventTypes())),
            new WebhookFieldChange(WebhookAuditField.STATUS, null, WebhookEndpointStatus.ACTIVE.getCode()),
            new WebhookFieldChange(WebhookAuditField.SIGNING_ROTATION, null, "1")));
    }

    /** 只记录实际变化的字段；地址与订阅都没变时拒绝。 */
    @Override
    public WebhookConfigurationChange updateEndpoint(UpdateWebhookEndpointCommand command) {
        var endpoint = requireVersion(command.getEndpointId(), command.getExpectedVersion());
        String beforeUrl = endpoint.getUrl();
        List<String> beforeTypes = WebhookEventTypes.parse(endpoint.getEventTypes());
        var changes = new ArrayList<WebhookFieldChange>();
        if (!beforeUrl.equals(command.getUrl())) {
            changes.add(new WebhookFieldChange(WebhookAuditField.ENDPOINT_URL, beforeUrl, command.getUrl()));
        }
        if (!beforeTypes.equals(command.getEventTypes())) {
            changes.add(new WebhookFieldChange(WebhookAuditField.EVENT_TYPES, String.join(",", beforeTypes),
                String.join(",", command.getEventTypes())));
        }
        if (changes.isEmpty()) {
            throw new BizCheckFailException("Webhook 端点地址与订阅没有变化");
        }
        int before = endpoint.getVersion();
        endpoints.update(endpoint, command.getUrl(), WebhookEventTypes.format(command.getEventTypes()),
            command.getNow());
        return new WebhookConfigurationChange(endpoint.getEndpointId(), before, endpoint.getVersion(), changes);
    }

    /** 状态与当前相同时拒绝。 */
    @Override
    public WebhookConfigurationChange changeStatus(ChangeWebhookEndpointStatusCommand command) {
        var endpoint = requireVersion(command.getEndpointId(), command.getExpectedVersion());
        var before = endpoint.getStatus();
        if (before == command.getStatus()) {
            throw new BizCheckFailException("Webhook 端点状态没有变化");
        }
        int beforeVersion = endpoint.getVersion();
        endpoints.changeStatus(endpoint, command.getStatus(), command.getNow());
        return new WebhookConfigurationChange(endpoint.getEndpointId(), beforeVersion, endpoint.getVersion(),
            List.of(new WebhookFieldChange(WebhookAuditField.STATUS, before.getCode(), command.getStatus().getCode())));
    }

    /** 任何时刻最多两把密钥参与签名：只有刚被替换的那把获得宽限，更早的立即截止。 */
    @Override
    public RotateWebhookSecretResult rotate(RotateWebhookSecretCommand command) {
        requireEndpoint(command.getEndpointId());
        Instant now = command.getNow();
        var all = secrets.findEndpoint(command.getEndpointId());
        WebhookSigningSecret active = null;
        for (var secret : all) {
            if (secret.getStatus() == WebhookSecretStatus.ACTIVE) {
                active = secret;
            } else if (secret.canSignAt(now)) {
                secrets.expire(secret, now);
            }
        }
        if (active == null) {
            throw new ConcurrentRequestException("Webhook 签名密钥已被轮换，请刷新后重试");
        }
        secrets.retire(active, command.getPreviousValidUntil(), now);
        int rotationNo = all.stream().mapToInt(WebhookSigningSecret::getRotationNo).max().orElse(0) + 1;
        secrets.insert(newSecret(command.getEndpointId(), rotationNo, command.getSecretVaultToken(), now));
        var change = new WebhookConfigurationChange(command.getEndpointId(), active.getRotationNo(), rotationNo,
            List.of(new WebhookFieldChange(WebhookAuditField.SIGNING_ROTATION,
                Integer.toString(active.getRotationNo()), Integer.toString(rotationNo))));
        Instant previousValidUntil = command.getPreviousValidUntil().isAfter(now) ? command.getPreviousValidUntil()
            : null;
        return new RotateWebhookSecretResult(change, rotationNo, previousValidUntil);
    }

    /** 原投递必须已结束，端点仍启用且仍订阅该事件类型；新投递与补偿任务同一事务登记。 */
    @Override
    public long replay(ReplayWebhookDeliveryCommand command) {
        var original = deliveries.find(command.getDeliveryId());
        if (original == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        if (!original.getStatus().isTerminal()) {
            throw new BizCheckFailException("Webhook 投递尚未结束，不能重新投递: " + original.getStatus().getCode());
        }
        var endpoint = requireEndpoint(original.getEndpointId());
        if (!endpoint.accepts(original.getEventType())) {
            throw new BizCheckFailException("Webhook 端点未启用或已不再订阅该事件类型，不能重新投递");
        }
        var replay = new WebhookDelivery();
        replay.setDeliveryId(ids.nextId());
        replay.setWebhookEventId(original.getWebhookEventId());
        replay.setTenantId(original.getTenantId());
        replay.setEndpointId(original.getEndpointId());
        replay.setEventType(original.getEventType());
        replay.setStatus(WebhookDeliveryStatus.CREATED);
        replay.setReplayOfDeliveryId(original.getDeliveryId());
        replay.setRequestedBy(command.getRequestedBy());
        replay.setVersion(1);
        replay.setCreateTime(command.getNow());
        replay.setUpdateTime(command.getNow());
        deliveries.insert(replay);
        events.register(WebhookDeliveryHandler.buildEvent(replay.getDeliveryId()));
        return replay.getDeliveryId();
    }

    /** ACTIVE 在前、宽限密钥在后。 */
    @Override
    public WebhookSigningTarget signingTarget(long endpointId, Instant now) {
        var endpoint = requireEndpoint(endpointId);
        var all = secrets.findEndpoint(endpointId);
        var tokens = new ArrayList<String>();
        tokens.add(WebhookSigningKeys.active(all).getSecretVaultToken());
        var grace = WebhookSigningKeys.grace(all, now);
        if (grace != null) {
            tokens.add(grace.getSecretVaultToken());
        }
        return new WebhookSigningTarget(endpoint.getUrl(), List.copyOf(tokens));
    }

    /** 端点所属租户。 */
    @Override
    public long endpointTenant(long endpointId) {
        return requireEndpoint(endpointId).getTenantId();
    }

    /** 端点不存在返回 NOT_FOUND。 */
    WebhookEndpoint requireEndpoint(long endpointId) {
        var endpoint = endpoints.find(endpointId);
        if (endpoint == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        return endpoint;
    }

    /** 端点存在且版本与调用方看到的一致。 */
    WebhookEndpoint requireVersion(long endpointId, int expectedVersion) {
        var endpoint = requireEndpoint(endpointId);
        if (!Objects.equals(endpoint.getVersion(), expectedVersion)) {
            throw new ConcurrentRequestException("Webhook 端点已被修改，请刷新后重试");
        }
        return endpoint;
    }

    /** 新的 ACTIVE 密钥。 */
    WebhookSigningSecret newSecret(long endpointId, int rotationNo, String token, Instant now) {
        var secret = new WebhookSigningSecret();
        secret.setSigningSecretId(ids.nextId());
        secret.setEndpointId(endpointId);
        secret.setRotationNo(rotationNo);
        secret.setSecretVaultToken(token);
        secret.setStatus(WebhookSecretStatus.ACTIVE);
        secret.setSigningSlot(1);
        secret.setVersion(1);
        secret.setCreateTime(now);
        secret.setUpdateTime(now);
        return secret;
    }
}
