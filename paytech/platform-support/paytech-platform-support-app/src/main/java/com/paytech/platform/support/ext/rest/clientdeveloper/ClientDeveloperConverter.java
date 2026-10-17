package com.paytech.platform.support.ext.rest.clientdeveloper;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ApiScope;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientApiCredential;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementInputs;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientApiCredentialResult;
import com.paytech.platform.support.api.clientapicredential.response.ClientApiCredentialResponseBody;
import com.paytech.platform.support.api.clientapicredential.response.ClientApiCredentialSecretResponseBody;
import com.paytech.platform.support.api.clientapicredential.response.ClientApiScopesResponseBody;
import com.paytech.platform.support.api.clientwebhook.response.ClientWebhookDeliveryDetailResponseBody;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import com.paytech.platform.support.delivery.domain.WebhookDeliveryDetailView;

/** 工作区自助接口的协议解析与投影；不访问数据库，ID 按十进制字符串输出。 */
@Component
public class ClientDeveloperConverter {

    /** 投递查询默认与最大时间跨度。 */
    static final Duration DEFAULT_DELIVERY_RANGE = Duration.ofDays(30);

    static final Duration MAX_DELIVERY_RANGE = Duration.ofDays(31);

    private static final tools.jackson.databind.json.JsonMapper JSON = tools.jackson.databind.json.JsonMapper.builder().build();

    /** Gateway 透传的可信账号或会话编号；格式不符视为会话失效。 */
    public long session(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException overflow) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        }
    }

    /** 路径中的对象 ID；格式不符与不存在一样返回 404，不透露存在性。 */
    public long pathId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException overflow) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
    }

    /** 可选查询 ID；格式不符返回 400。 */
    public Long optionalId(String value) {
        return value == null || value.isBlank() ? null : ClientApiManagementInputs.id(value);
    }

    /** 凭证状态筛选：ACTIVE / REVOKED / EXPIRED 或空。 */
    public String credentialStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!List.of("ACTIVE", "REVOKED", "EXPIRED").contains(value)) {
            throw new InvalidParameterException("status 只能为 ACTIVE、REVOKED 或 EXPIRED");
        }
        return value;
    }

    /** 投递时间范围：都为空取最近 30 天；给出时必须同时给出且跨度不超过 31 天。 */
    public Instant[] deliveryRange(String createdFrom, String createdTo, Instant now) {
        if (createdFrom == null && createdTo == null) {
            return new Instant[] {now.minus(DEFAULT_DELIVERY_RANGE), now.plusSeconds(1)};
        }
        Instant from = instant(createdFrom, "createdFrom");
        Instant to = instant(createdTo, "createdTo");
        if (!to.isAfter(from) || Duration.between(from, to).compareTo(MAX_DELIVERY_RANGE) > 0) {
            throw new InvalidParameterException("createdTo 必须晚于 createdFrom，且跨度不超过 31 天");
        }
        return new Instant[] {from, to};
    }

    /** 凭证摘要；状态按查询时刻推导。 */
    public ClientApiCredentialResponseBody toCredential(ClientApiCredential credential, Instant asOf) {
        return new ClientApiCredentialResponseBody(Long.toString(credential.getCredentialId()),
            credential.getCredentialName(), credential.getKeyPrefix(), credential.effectiveStatus(asOf).getCode(),
            credential.getScopeCodes(), credential.getCreateTime().toString(), credential.getExpiresTime().toString(),
            credential.getRotatedFromId() == null ? null : Long.toString(credential.getRotatedFromId()),
            credential.getVersion());
    }

    /** 创建或轮换结果，完整密钥只在此出现一次。 */
    public ClientApiCredentialSecretResponseBody toSecret(ClientApiCredentialResult result) {
        return new ClientApiCredentialSecretResponseBody(toCredential(result.credential(), result.asOf()),
            result.secret());
    }

    /** Scope 目录项。 */
    public ClientApiScopesResponseBody toScopes(List<ApiScope> scopes) {
        return new ClientApiScopesResponseBody(scopes.stream().map(scope -> new ClientApiScopesResponseBody.Item(
            scope.getScopeCode(), scope.getDisplayName(), scope.getDescription())).toList());
    }

    /** 客户可见的投递详情：去掉运营账号与内部系统名。 */
    public ClientWebhookDeliveryDetailResponseBody toDeliveryDetail(WebhookDeliveryDetailView view) {
        var event = new ClientWebhookDeliveryDetailResponseBody.EventItem(view.getEventId(), view.getEventType(),
            view.getSchemaVersion(), envelope(view.getEnvelope()));
        var attempts = view.getAttempts().stream().map(attempt -> new ClientWebhookDeliveryDetailResponseBody.AttemptItem(
            attempt.getAttemptNo(), attempt.getRequestUrl(), attempt.getRotationNo(), attempt.getStatus().getCode(),
            attempt.getHttpStatus(), attempt.getFailureCode() == null ? null : attempt.getFailureCode().getCode(),
            attempt.getDurationMs(), text(attempt.getStartedTime()), text(attempt.getFinishedTime()))).toList();
        return new ClientWebhookDeliveryDetailResponseBody(Long.toString(view.getDeliveryId()),
            Long.toString(view.getEndpointId()), view.getStatus().getCode(), view.getAttemptCount(),
            text(view.getLastAttemptTime()),
            view.getReplayOfDeliveryId() == null ? null : Long.toString(view.getReplayOfDeliveryId()),
            text(view.getCreateTime()), event, attempts);
    }

    /** 冻结 Envelope 原样输出为 JSON 对象。 */
    static tools.jackson.databind.JsonNode envelope(String json) {
        try {
            return JSON.readTree(json);
        } catch (tools.jackson.core.JacksonException malformed) {
            throw new com.paytech.foundation.common.error.ErrorException("Webhook 冻结事件不是合法 JSON", malformed);
        }
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
}
