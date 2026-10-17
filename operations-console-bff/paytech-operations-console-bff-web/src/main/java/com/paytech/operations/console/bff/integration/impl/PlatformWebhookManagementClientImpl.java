package com.paytech.operations.console.bff.integration.impl;

import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.ClientApiContractConverter;
import com.paytech.operations.console.bff.integration.PlatformWebhookManagementClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.ChangeWebhookEndpointStatusRequestBody;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.CreateWebhookEndpointResponseBody;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.ReplayWebhookDeliveryRequestBody;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.ReplayWebhookDeliveryResponseBody;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.RotateWebhookSecretRequestBody;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.RotateWebhookSecretResponseBody;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.SaveWebhookEndpointRequestBody;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.WebhookClientSummary;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.WebhookDeliveryDetailResponseBody;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.WebhookDeliverySummary;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.WebhookEndpointMutationResponseBody;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.WebhookEndpointResponseBody;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.WebhookTestResponseBody;
import com.paytech.platform.support.api.webhookmanagement.WebhookManagementApi;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import lombok.AllArgsConstructor;

/**
 * Platform Webhook 管理的 HTTP 转发，请求幂等归 Gateway
 * <li>本地模型与 Owner 合同字段一一对应；转换遇到未知字段或缺少基本类型字段即失败，等同逐字段投影并暴露合同漂移</li>
 * <li>修改、启停、详情核对端点或投递 ID 与请求一致；422 只保留本次请求体内的字段路径</li>
 * <li>密钥明文只在本次转发的内存中，不记录、不缓存</li>
 */
@Component
@AllArgsConstructor
public class PlatformWebhookManagementClientImpl implements PlatformWebhookManagementClient {

    private static final JsonMapper STRICT = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

    /** Platform 公共分页支持的最大每页条数。 */
    private static final int MAX_PAGE_SIZE = 200;

    private final WebhookManagementApi api;

    /** Client 搜索，分页窗口与 Client ID 唯一性由本地检查保护。 */
    @Override
    public PagingResponseBody<WebhookClientSummary> clients(OperatorContext context, String keyword, Integer page,
        Integer size) {
        var result = api.clients(context.accountId(), context.sessionId(), keyword, page, size);
        return page(result, page, size, item -> convert(item, WebhookClientSummary.class),
            WebhookClientSummary::clientId);
    }

    /** Client 的全部端点；端点 ID 不得重复。 */
    @Override
    public List<WebhookEndpointResponseBody> endpoints(OperatorContext context, String clientId) {
        var result = api.endpoints(context.accountId(), context.sessionId(), clientId);
        if (result == null) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        var items = result.stream().map(item -> convert(item, WebhookEndpointResponseBody.class)).toList();
        var identities = new HashSet<String>();
        if (!items.stream().allMatch(item -> identities.add(item.endpointId()))) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        return items;
    }

    /** 新建端点；密钥明文原样返回浏览器。 */
    @Override
    public CreateWebhookEndpointResponseBody createEndpoint(OperatorContext context, String clientId,
        SaveWebhookEndpointRequestBody request) {
        return write(request, () -> api.createEndpoint(context.accountId(), context.sessionId(), clientId,
            platformSave(request)).getBody(), CreateWebhookEndpointResponseBody.class);
    }

    /** 修改端点；返回的端点 ID 必须与请求一致。 */
    @Override
    public WebhookEndpointMutationResponseBody updateEndpoint(OperatorContext context, String endpointId,
        SaveWebhookEndpointRequestBody request) {
        var mapped = write(request, () -> api.updateEndpoint(context.accountId(), context.sessionId(), endpointId,
            platformSave(request)), WebhookEndpointMutationResponseBody.class);
        ClientApiContractConverter.sameIdentity(endpointId, mapped.endpointId());
        return mapped;
    }

    /** 启停端点；返回的端点 ID 必须与请求一致。 */
    @Override
    public WebhookEndpointMutationResponseBody changeEndpointStatus(OperatorContext context, String endpointId,
        ChangeWebhookEndpointStatusRequestBody request) {
        var mapped = write(request, () -> api.changeEndpointStatus(context.accountId(), context.sessionId(), endpointId,
            convert(request, com.paytech.platform.support.api.webhookmanagement.request
                .ChangeWebhookEndpointStatusRequestBody.class)), WebhookEndpointMutationResponseBody.class);
        ClientApiContractConverter.sameIdentity(endpointId, mapped.endpointId());
        return mapped;
    }

    /** 轮换签名密钥；新密钥明文原样返回浏览器。 */
    @Override
    public RotateWebhookSecretResponseBody rotateSecret(OperatorContext context, String endpointId,
        RotateWebhookSecretRequestBody request) {
        return write(request, () -> api.rotateSecret(context.accountId(), context.sessionId(), endpointId,
            convert(request, com.paytech.platform.support.api.webhookmanagement.request
                .RotateWebhookSecretRequestBody.class)).getBody(), RotateWebhookSecretResponseBody.class);
    }

    /** 发送测试事件，无请求体。 */
    @Override
    public WebhookTestResponseBody testEndpoint(OperatorContext context, String endpointId) {
        return convert(api.testEndpoint(context.accountId(), context.sessionId(), endpointId),
            WebhookTestResponseBody.class);
    }

    /** 投递分页，分页窗口与投递 ID 唯一性由本地检查保护。 */
    @Override
    public PagingResponseBody<WebhookDeliverySummary> deliveries(OperatorContext context, String clientId,
        String createdFrom, String createdTo, Integer page, Integer size, String endpointId, String eventType,
        String status, String eventId) {
        var result = api.deliveries(context.accountId(), context.sessionId(), clientId, createdFrom, createdTo, page,
            size, endpointId, eventType, status, eventId);
        return page(result, page, size, item -> convert(item, WebhookDeliverySummary.class),
            WebhookDeliverySummary::deliveryId);
    }

    /** 投递详情；返回的投递 ID 必须与请求一致。 */
    @Override
    public WebhookDeliveryDetailResponseBody delivery(OperatorContext context, String deliveryId) {
        var mapped = convert(api.delivery(context.accountId(), context.sessionId(), deliveryId),
            WebhookDeliveryDetailResponseBody.class);
        ClientApiContractConverter.sameIdentity(deliveryId, mapped.deliveryId());
        return mapped;
    }

    /** Replay 已结束的投递。 */
    @Override
    public ReplayWebhookDeliveryResponseBody replayDelivery(OperatorContext context, String deliveryId,
        ReplayWebhookDeliveryRequestBody request) {
        return write(request, () -> api.replayDelivery(context.accountId(), context.sessionId(), deliveryId,
            convert(request, com.paytech.platform.support.api.webhookmanagement.request
                .ReplayWebhookDeliveryRequestBody.class)).getBody(), ReplayWebhookDeliveryResponseBody.class);
    }

    /** 写入转发：422 字段路径投影到本次请求体。 */
    static <T> T write(Object request, Supplier<Object> call, Class<T> type) {
        try {
            return convert(call.get(), type);
        } catch (HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }

    /** 新建与修改共用的端点请求体。 */
    static com.paytech.platform.support.api.webhookmanagement.request.SaveWebhookEndpointRequestBody
        platformSave(SaveWebhookEndpointRequestBody request) {
        return convert(request,
            com.paytech.platform.support.api.webhookmanagement.request.SaveWebhookEndpointRequestBody.class);
    }

    /** 分页不改上游总数，检查窗口及当前页内身份唯一；每页上限沿用 Platform 公共分页的 200。 */
    static <S, T> PagingResponseBody<T> page(PagingResponseBody<S> value, Integer page, Integer size,
        Function<S, T> converter, Function<T, String> identity) {
        int p = page == null ? 1 : page;
        int s = size == null ? 10 : size;
        if (value == null || value.items() == null || value.page() != p || value.size() != s || p <= 0 || s <= 0
            || s > MAX_PAGE_SIZE || value.total() < 0) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        long remaining = Math.max(0L, value.total() - ((long) p - 1) * s);
        if (value.items().size() != Math.min((long) s, remaining)) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        var items = value.items().stream().map(converter).toList();
        var identities = new HashSet<String>();
        if (!items.stream().allMatch(item -> identities.add(identity.apply(item)))) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        return new PagingResponseBody<>(items, p, s, value.total());
    }

    /** 严格字段对应的模型转换；上游缺少必需字段或多出字段时视为无效响应。 */
    static <T> T convert(Object value, Class<T> type) {
        if (value == null) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        try {
            return STRICT.convertValue(value, type);
        } catch (JacksonException | IllegalArgumentException invalid) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, null, invalid);
        }
    }
}
