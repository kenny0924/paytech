package com.paytech.platform.support.ext.rest.webhookmanagement;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementInputs;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationInputs;
import com.paytech.platform.support.api.webhookmanagement.request.ChangeWebhookEndpointStatusRequestBody;
import com.paytech.platform.support.api.webhookmanagement.request.ReplayWebhookDeliveryRequestBody;
import com.paytech.platform.support.api.webhookmanagement.request.RotateWebhookSecretRequestBody;
import com.paytech.platform.support.api.webhookmanagement.request.SaveWebhookEndpointRequestBody;
import com.paytech.platform.support.api.webhookmanagement.response.CreateWebhookEndpointResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.RotateWebhookSecretResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookDeliveryDetailResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookDeliverySummaryResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookEndpointMutationResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookEndpointResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookTestResponseBody;
import com.paytech.platform.support.delivery.core.enums.WebhookDeliveryStatus;
import com.paytech.platform.support.delivery.core.enums.WebhookEndpointStatus;
import com.paytech.platform.support.delivery.domain.WebhookAttemptOutcome;
import com.paytech.platform.support.delivery.domain.WebhookConfigurationChange;
import com.paytech.platform.support.delivery.domain.WebhookDeliveryDetailView;
import com.paytech.platform.support.delivery.domain.WebhookDeliverySummaryView;
import com.paytech.platform.support.delivery.domain.WebhookEndpointView;
import com.paytech.platform.support.delivery.domain.WebhookEventTypes;
import com.paytech.platform.support.service.processor.webhook.request.ChangeWebhookEndpointStatusRequest;
import com.paytech.platform.support.service.processor.webhook.request.CreateWebhookEndpointRequest;
import com.paytech.platform.support.service.processor.webhook.request.GetWebhookDeliveryRequest;
import com.paytech.platform.support.service.processor.webhook.request.ListWebhookDeliveriesRequest;
import com.paytech.platform.support.service.processor.webhook.request.ListWebhookEndpointsRequest;
import com.paytech.platform.support.service.processor.webhook.request.ReplayWebhookDeliveryRequest;
import com.paytech.platform.support.service.processor.webhook.request.RotateWebhookSecretRequest;
import com.paytech.platform.support.service.processor.webhook.request.TestWebhookEndpointRequest;
import com.paytech.platform.support.service.processor.webhook.request.UpdateWebhookEndpointRequest;
import com.paytech.platform.support.service.processor.webhook.response.CreateWebhookEndpointResponse;
import com.paytech.platform.support.service.processor.webhook.response.RotateWebhookSecretResponse;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Webhook 管理的协议解析与投影；不访问数据库，ID 按十进制字符串输出。 */
@Component
public class WebhookManagementConverter {

    /** 投递列表单次查询的最长时间跨度。 */
    static final Duration MAX_DELIVERY_RANGE = Duration.ofDays(31);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 校验 Path 中的 Client ID。 */
    public ListWebhookEndpointsRequest toEndpointsRequest(String actor, String session, String clientId) {
        return new ListWebhookEndpointsRequest(actor, session, ClientApiManagementInputs.id(clientId));
    }

    /** 订阅列表在此校验；地址语法与 DNS 由处理器在事务外校验。 */
    public CreateWebhookEndpointRequest toCreateRequest(String actor, String session, String clientId,
        SaveWebhookEndpointRequestBody body) {
        return new CreateWebhookEndpointRequest(actor, session, ClientApiManagementInputs.id(clientId), body.getUrl(),
            WebhookEventTypes.validate(body.getEventTypes()), ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 修改必须携带当前版本。 */
    public UpdateWebhookEndpointRequest toUpdateRequest(String actor, String session, String endpointId,
        SaveWebhookEndpointRequestBody body) {
        if (body.getExpectedVersion() <= 0) {
            throw new InvalidParameterException("expectedVersion 必须为当前版本");
        }
        return new UpdateWebhookEndpointRequest(actor, session, ClientApiManagementInputs.id(endpointId),
            body.getUrl(), WebhookEventTypes.validate(body.getEventTypes()), body.getExpectedVersion(),
            ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 目标状态在此解析。 */
    public ChangeWebhookEndpointStatusRequest toStatusRequest(String actor, String session, String endpointId,
        ChangeWebhookEndpointStatusRequestBody body) {
        return new ChangeWebhookEndpointStatusRequest(actor, session, ClientApiManagementInputs.id(endpointId),
            code(WebhookEndpointStatus.class, body.getStatus(), "status"), body.getExpectedVersion(),
            ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 轮换请求。 */
    public RotateWebhookSecretRequest toRotateRequest(String actor, String session, String endpointId,
        RotateWebhookSecretRequestBody body) {
        return new RotateWebhookSecretRequest(actor, session, ClientApiManagementInputs.id(endpointId),
            body.isImmediate(), ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 测试请求。 */
    public TestWebhookEndpointRequest toTestRequest(String actor, String session, String endpointId) {
        return new TestWebhookEndpointRequest(actor, session, ClientApiManagementInputs.id(endpointId));
    }

    /** 时间范围必填且不超过 31 天；其余条件可选、精确匹配。 */
    public ListWebhookDeliveriesRequest toDeliveriesRequest(String actor, String session, String clientId,
        String createdFrom, String createdTo, Integer page, Integer size, String endpointId, String eventType,
        String status, String eventId) {
        Instant from = instant(createdFrom, "createdFrom");
        Instant to = instant(createdTo, "createdTo");
        if (!to.isAfter(from) || Duration.between(from, to).compareTo(MAX_DELIVERY_RANGE) > 0) {
            throw new InvalidParameterException("createdTo 必须晚于 createdFrom，且跨度不超过 31 天");
        }
        if (eventId != null && !eventId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")) {
            throw new InvalidParameterException("eventId 格式不合法");
        }
        if (eventType != null && eventType.length() > 64) {
            throw new InvalidParameterException("eventType 超长");
        }
        return new ListWebhookDeliveriesRequest(actor, session, ClientApiManagementInputs.id(clientId),
            ClientApiManagementInputs.paging(page, size), from, to,
            endpointId == null ? null : ClientApiManagementInputs.id(endpointId), eventType,
            status == null ? null : code(WebhookDeliveryStatus.class, status, "status"), eventId);
    }

    /** 详情请求。 */
    public GetWebhookDeliveryRequest toDeliveryRequest(String actor, String session, String deliveryId) {
        return new GetWebhookDeliveryRequest(actor, session, ClientApiManagementInputs.id(deliveryId));
    }

    /** Replay 请求。 */
    public ReplayWebhookDeliveryRequest toReplayRequest(String actor, String session, String deliveryId,
        ReplayWebhookDeliveryRequestBody body) {
        return new ReplayWebhookDeliveryRequest(actor, session, ClientApiManagementInputs.id(deliveryId),
            ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 端点列表项。 */
    public WebhookEndpointResponseBody toEndpoint(WebhookEndpointView view) {
        var body = new WebhookEndpointResponseBody();
        body.setEndpointId(Long.toString(view.getEndpointId()));
        body.setUrl(view.getUrl());
        body.setEventTypes(view.getEventTypes());
        body.setStatus(view.getStatus().getCode());
        body.setVersion(view.getVersion());
        body.setCreateTime(text(view.getCreateTime()));
        body.setUpdateTime(text(view.getUpdateTime()));
        var secret = new WebhookEndpointResponseBody.SigningSecretItem();
        secret.setRotationNo(view.getRotationNo());
        secret.setCreateTime(text(view.getSecretCreateTime()));
        secret.setPreviousValidUntil(text(view.getPreviousValidUntil()));
        body.setSigningSecret(secret);
        return body;
    }

    /** 新建结果。 */
    public CreateWebhookEndpointResponseBody toCreated(CreateWebhookEndpointResponse response) {
        return new CreateWebhookEndpointResponseBody(Long.toString(response.getEndpointId()), response.getVersion(),
            response.getSigningSecret());
    }

    /** 修改结果。 */
    public WebhookEndpointMutationResponseBody toMutation(WebhookConfigurationChange change) {
        return new WebhookEndpointMutationResponseBody(Long.toString(change.getEndpointId()), change.getAfterVersion());
    }

    /** 轮换结果。 */
    public RotateWebhookSecretResponseBody toRotated(RotateWebhookSecretResponse response) {
        return new RotateWebhookSecretResponseBody(response.getRotationNo(), response.getSigningSecret(),
            text(response.getPreviousValidUntil()));
    }

    /** 测试结果。 */
    public WebhookTestResponseBody toTest(WebhookAttemptOutcome outcome) {
        return new WebhookTestResponseBody(outcome.isSucceeded() ? "SUCCEEDED" : "FAILED", outcome.getHttpStatus(),
            outcome.getFailureCode() == null ? null : outcome.getFailureCode().getCode(), outcome.getDurationMs());
    }

    /** 投递列表项。 */
    public WebhookDeliverySummaryResponseBody toSummary(WebhookDeliverySummaryView view) {
        var body = new WebhookDeliverySummaryResponseBody();
        body.setDeliveryId(Long.toString(view.getDeliveryId()));
        body.setEventId(view.getEventId());
        body.setEventType(view.getEventType());
        body.setEndpointId(Long.toString(view.getEndpointId()));
        body.setStatus(view.getStatus().getCode());
        body.setAttemptCount(view.getAttemptCount());
        body.setLastAttemptTime(text(view.getLastAttemptTime()));
        body.setReplayOfDeliveryId(id(view.getReplayOfDeliveryId()));
        body.setCreateTime(text(view.getCreateTime()));
        return body;
    }

    /** 投递详情。 */
    public WebhookDeliveryDetailResponseBody toDetail(WebhookDeliveryDetailView view) {
        var body = new WebhookDeliveryDetailResponseBody();
        body.setDeliveryId(Long.toString(view.getDeliveryId()));
        body.setEventId(view.getEventId());
        body.setEventType(view.getEventType());
        body.setEndpointId(Long.toString(view.getEndpointId()));
        body.setStatus(view.getStatus().getCode());
        body.setAttemptCount(view.getAttemptCount());
        body.setLastAttemptTime(text(view.getLastAttemptTime()));
        body.setReplayOfDeliveryId(id(view.getReplayOfDeliveryId()));
        body.setCreateTime(text(view.getCreateTime()));
        body.setRequestedBy(id(view.getRequestedBy()));
        var event = new WebhookDeliveryDetailResponseBody.EventItem();
        event.setEventId(view.getEventId());
        event.setEventType(view.getEventType());
        event.setSchemaVersion(view.getSchemaVersion());
        event.setSourceSystem(view.getSourceSystem());
        event.setEnvelope(envelope(view.getEnvelope()));
        body.setEvent(event);
        body.setAttempts(view.getAttempts().stream().map(WebhookManagementConverter::attempt).toList());
        return body;
    }

    /** 尝试项。 */
    static WebhookDeliveryDetailResponseBody.AttemptItem attempt(WebhookDeliveryDetailView.Attempt attempt) {
        var item = new WebhookDeliveryDetailResponseBody.AttemptItem();
        item.setAttemptNo(attempt.getAttemptNo());
        item.setRequestUrl(attempt.getRequestUrl());
        item.setRotationNo(attempt.getRotationNo());
        item.setGraceRotationNo(attempt.getGraceRotationNo());
        item.setStatus(attempt.getStatus().getCode());
        item.setHttpStatus(attempt.getHttpStatus());
        item.setFailureCode(attempt.getFailureCode() == null ? null : attempt.getFailureCode().getCode());
        item.setDurationMs(attempt.getDurationMs());
        item.setStartedTime(text(attempt.getStartedTime()));
        item.setFinishedTime(text(attempt.getFinishedTime()));
        return item;
    }

    /** 冻结 Envelope 原样输出为 JSON 对象。 */
    static tools.jackson.databind.JsonNode envelope(String json) {
        try {
            return JSON.readTree(json);
        } catch (JacksonException malformed) {
            throw new com.paytech.foundation.common.error.ErrorException("Webhook 冻结事件不是合法 JSON", malformed);
        }
    }

    /** 闭合代码解析，未知值返回 400。 */
    static <E extends Enum<E> & CodeEnum> E code(Class<E> type, String value, String field) {
        for (E constant : type.getEnumConstants()) {
            if (constant.getCode().equals(value)) {
                return constant;
            }
        }
        throw new InvalidParameterException(field + " 不是支持的值");
    }

    /** ISO-8601 UTC 时间。 */
    static Instant instant(String value, String field) {
        if (value == null) {
            throw new InvalidParameterException(field + " 必填");
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new InvalidParameterException(field + " 必须为 ISO-8601 UTC 时间");
        }
    }

    /** 可空时间按 ISO-8601 输出。 */
    static String text(Instant value) {
        return value == null ? null : value.toString();
    }

    /** 可空 ID 按十进制字符串输出。 */
    static String id(Long value) {
        return value == null ? null : Long.toString(value);
    }
}
