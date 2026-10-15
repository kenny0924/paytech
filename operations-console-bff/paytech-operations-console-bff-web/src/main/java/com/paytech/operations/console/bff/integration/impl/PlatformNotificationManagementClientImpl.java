package com.paytech.operations.console.bff.integration.impl;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.ClientApiContractConverter;
import com.paytech.operations.console.bff.integration.PlatformNotificationManagementClient;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.ChangeNotificationStatusRequestBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.CreateNotificationTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationMutationResponseBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationPreviewResponseBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationProviderSummary;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationRequestDetailResponseBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationRequestSummary;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationTemplateDetailResponseBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationTemplateSummary;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.PreviewNotificationContentRequestBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.SaveNotificationContentRequestBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.SaveNotificationRouteRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.platform.support.api.notificationmanagement.NotificationManagementApi;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import lombok.AllArgsConstructor;

/**
 * Platform 通知管理的 HTTP 转发，请求幂等归 Gateway
 * <li>本地模型与 Owner 合同字段一一对应；转换遇到未知字段或缺少基本类型字段即失败，等同逐字段投影并暴露合同漂移</li>
 * <li>写入结果核对模板或账号编码与请求一致；422 只保留本次请求体内的字段路径</li>
 */
@Component
@AllArgsConstructor
public class PlatformNotificationManagementClientImpl implements PlatformNotificationManagementClient {

    private static final JsonMapper STRICT = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

    private final NotificationManagementApi api;

    /** 模板分页，分页窗口与身份唯一性由公共检查保护。 */
    @Override
    public PagingResponseBody<NotificationTemplateSummary> templates(OperatorContext context, Integer page,
        Integer size, String keyword, String status) {
        var result = api.templates(context.accountId(), context.sessionId(), page, size, keyword, status);
        return ClientApiContractConverter.page(result, page, size, item -> convert(item, NotificationTemplateSummary.class),
            NotificationTemplateSummary::templateCode);
    }

    /** 模板详情；返回的编码必须与请求一致。 */
    @Override
    public NotificationTemplateDetailResponseBody template(OperatorContext context, String templateCode) {
        var mapped = convert(api.template(context.accountId(), context.sessionId(), templateCode),
            NotificationTemplateDetailResponseBody.class);
        ClientApiContractConverter.sameIdentity(templateCode, mapped.templateCode());
        return mapped;
    }

    /** 新建模板。 */
    @Override
    public NotificationMutationResponseBody createTemplate(OperatorContext context,
        CreateNotificationTemplateRequestBody request) {
        var mapped = write(request, () -> api.createTemplate(context.accountId(), context.sessionId(),
            convert(request, com.paytech.platform.support.api.notificationmanagement.request
                .CreateNotificationTemplateRequestBody.class)));
        ClientApiContractConverter.sameIdentity(request.templateCode(), mapped.code());
        return mapped;
    }

    /** 启停模板。 */
    @Override
    public NotificationMutationResponseBody changeTemplateStatus(OperatorContext context, String templateCode,
        ChangeNotificationStatusRequestBody request) {
        var mapped = write(request, () -> api.changeTemplateStatus(context.accountId(), context.sessionId(),
            templateCode, platformStatus(request)));
        ClientApiContractConverter.sameIdentity(templateCode, mapped.code());
        return mapped;
    }

    /** 保存语言内容；返回的语言为 Platform 规范化后的形式，不做编码核对。 */
    @Override
    public NotificationMutationResponseBody saveContent(OperatorContext context, String templateCode, String locale,
        SaveNotificationContentRequestBody request) {
        return write(request, () -> api.saveContent(context.accountId(), context.sessionId(), templateCode, locale,
            convert(request, com.paytech.platform.support.api.notificationmanagement.request
                .SaveNotificationContentRequestBody.class)));
    }

    /** 预览内容草稿。 */
    @Override
    public NotificationPreviewResponseBody preview(OperatorContext context,
        PreviewNotificationContentRequestBody request) {
        try {
            return convert(api.preview(context.accountId(), context.sessionId(), convert(request,
                com.paytech.platform.support.api.notificationmanagement.request
                    .PreviewNotificationContentRequestBody.class)), NotificationPreviewResponseBody.class);
        } catch (HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }

    /** 保存路由。 */
    @Override
    public NotificationMutationResponseBody saveRoute(OperatorContext context, String templateCode,
        SaveNotificationRouteRequestBody request) {
        var mapped = write(request, () -> api.saveRoute(context.accountId(), context.sessionId(), templateCode,
            convert(request, com.paytech.platform.support.api.notificationmanagement.request
                .SaveNotificationRouteRequestBody.class)));
        ClientApiContractConverter.sameIdentity(templateCode, mapped.code());
        return mapped;
    }

    /** 启停路由。 */
    @Override
    public NotificationMutationResponseBody changeRouteStatus(OperatorContext context, String templateCode,
        ChangeNotificationStatusRequestBody request) {
        var mapped = write(request, () -> api.changeRouteStatus(context.accountId(), context.sessionId(),
            templateCode, platformStatus(request)));
        ClientApiContractConverter.sameIdentity(templateCode, mapped.code());
        return mapped;
    }

    /** 发送账号分页。 */
    @Override
    public PagingResponseBody<NotificationProviderSummary> providers(OperatorContext context, Integer page,
        Integer size) {
        var result = api.providers(context.accountId(), context.sessionId(), page, size);
        return ClientApiContractConverter.page(result, page, size, item -> convert(item, NotificationProviderSummary.class),
            NotificationProviderSummary::providerCode);
    }

    /** 启停发送账号。 */
    @Override
    public NotificationMutationResponseBody changeProviderStatus(OperatorContext context, String providerCode,
        ChangeNotificationStatusRequestBody request) {
        var mapped = write(request, () -> api.changeProviderStatus(context.accountId(), context.sessionId(),
            providerCode, platformStatus(request)));
        ClientApiContractConverter.sameIdentity(providerCode, mapped.code());
        return mapped;
    }

    /** 发送记录分页。 */
    @Override
    public PagingResponseBody<NotificationRequestSummary> requests(OperatorContext context, Integer page,
        Integer size, String createdFrom, String createdTo, String sourceSystem, String sourceEventKey,
        String templateCode, String deliveryStatus) {
        var result = api.requests(context.accountId(), context.sessionId(), page, size, createdFrom, createdTo,
            sourceSystem, sourceEventKey, templateCode, deliveryStatus);
        return ClientApiContractConverter.page(result, page, size, item -> convert(item, NotificationRequestSummary.class),
            NotificationRequestSummary::requestId);
    }

    /** 发送记录详情；返回的请求 ID 必须与请求一致。 */
    @Override
    public NotificationRequestDetailResponseBody request(OperatorContext context, String requestId) {
        var mapped = convert(api.request(context.accountId(), context.sessionId(), requestId),
            NotificationRequestDetailResponseBody.class);
        ClientApiContractConverter.sameIdentity(requestId, mapped.requestId());
        return mapped;
    }

    /** 写入转发：422 字段路径投影到本次请求体。 */
    NotificationMutationResponseBody write(Object request,
        Supplier<com.paytech.platform.support.api.notificationmanagement.response.NotificationMutationResponseBody> call) {
        try {
            return convert(call.get(), NotificationMutationResponseBody.class);
        } catch (HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }

    /** 启停请求体。 */
    static com.paytech.platform.support.api.notificationmanagement.request.ChangeNotificationStatusRequestBody
        platformStatus(ChangeNotificationStatusRequestBody request) {
        return convert(request,
            com.paytech.platform.support.api.notificationmanagement.request.ChangeNotificationStatusRequestBody.class);
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
