package com.paytech.platform.support.ext.rest.notificationmanagement;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementInputs;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationInputs;
import com.paytech.platform.support.api.notificationmanagement.request.ChangeNotificationStatusRequestBody;
import com.paytech.platform.support.api.notificationmanagement.request.CreateNotificationTemplateRequestBody;
import com.paytech.platform.support.api.notificationmanagement.request.PreviewNotificationContentRequestBody;
import com.paytech.platform.support.api.notificationmanagement.request.SaveNotificationContentRequestBody;
import com.paytech.platform.support.api.notificationmanagement.request.SaveNotificationRouteRequestBody;
import com.paytech.platform.support.api.notificationmanagement.response.AttemptItem;
import com.paytech.platform.support.api.notificationmanagement.response.DeliveryItem;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationMutationResponseBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationPreviewResponseBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationProviderResponseBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationRequestDetailResponseBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationRequestSummaryResponseBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationTemplateDetailResponseBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationTemplateSummaryResponseBody;
import com.paytech.platform.support.api.notificationmanagement.response.ProviderReferenceItem;
import com.paytech.platform.support.api.notificationmanagement.response.TemplateContentItem;
import com.paytech.platform.support.api.notificationmanagement.response.TemplateRouteItem;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationDeliveryStatus;
import com.paytech.platform.support.delivery.domain.NotificationAttemptItem;
import com.paytech.platform.support.delivery.domain.NotificationConfigurationChange;
import com.paytech.platform.support.delivery.domain.NotificationDeliveryItem;
import com.paytech.platform.support.delivery.domain.NotificationInput;
import com.paytech.platform.support.delivery.domain.NotificationPreview;
import com.paytech.platform.support.delivery.domain.NotificationProviderView;
import com.paytech.platform.support.delivery.domain.NotificationRequestDetailView;
import com.paytech.platform.support.delivery.domain.NotificationRequestFilter;
import com.paytech.platform.support.delivery.domain.NotificationRequestSummaryView;
import com.paytech.platform.support.delivery.domain.NotificationTemplateDetailView;
import com.paytech.platform.support.delivery.domain.NotificationTemplateSummary;
import com.paytech.platform.support.service.processor.notification.request.ChangeNotificationStatusRequest;
import com.paytech.platform.support.service.processor.notification.request.CreateNotificationTemplateRequest;
import com.paytech.platform.support.service.processor.notification.request.GetNotificationRequestRequest;
import com.paytech.platform.support.service.processor.notification.request.GetNotificationTemplateRequest;
import com.paytech.platform.support.service.processor.notification.request.ListNotificationProvidersRequest;
import com.paytech.platform.support.service.processor.notification.request.ListNotificationRequestsRequest;
import com.paytech.platform.support.service.processor.notification.request.ListNotificationTemplatesRequest;
import com.paytech.platform.support.service.processor.notification.request.PreviewNotificationContentRequest;
import com.paytech.platform.support.service.processor.notification.request.SaveNotificationContentRequest;
import com.paytech.platform.support.service.processor.notification.request.SaveNotificationRouteRequest;

/** 通知管理的协议解析与投影；不访问数据库，ID 按十进制字符串输出。 */
@Component
public class NotificationManagementConverter {

    /** 发送记录单次查询的最长时间跨度。 */
    static final Duration MAX_RECORD_RANGE = Duration.ofDays(31);

    /** 补齐分页，关键字通配符按文字处理。 */
    public ListNotificationTemplatesRequest toTemplatesRequest(String actor, String session, Integer page,
        Integer size, String keyword, String status) {
        return new ListNotificationTemplatesRequest(actor, session, ClientApiManagementInputs.paging(page, size),
            ClientApiManagementInputs.contains(keyword, 64, "keyword"),
            status == null ? null : code(NotificationConfigurationStatus.class, status, "status"));
    }

    /** 校验 Path 中的精确模板编码。 */
    public GetNotificationTemplateRequest toTemplateRequest(String actor, String session, String templateCode) {
        return new GetNotificationTemplateRequest(actor, session, ConfigurationInputs.code(templateCode, 64));
    }

    /** Body 已由入口校验；原因去除首尾空白。 */
    public CreateNotificationTemplateRequest toCreateRequest(String actor, String session,
        CreateNotificationTemplateRequestBody body) {
        return new CreateNotificationTemplateRequest(actor, session, body.getTemplateCode(),
            ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 模板、路由与账号启停共用；Path 编码与目标状态在此解析。 */
    public ChangeNotificationStatusRequest toStatusRequest(String actor, String session, String code,
        ChangeNotificationStatusRequestBody body) {
        return new ChangeNotificationStatusRequest(actor, session, ConfigurationInputs.code(code, 64),
            code(NotificationConfigurationStatus.class, body.getStatus(), "status"), body.getExpectedVersion(),
            ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 语言按规范 BCP 47 形式保存；标题与正文原样保留，由处理器按发送规则校验。 */
    public SaveNotificationContentRequest toContentRequest(String actor, String session, String templateCode,
        String locale, SaveNotificationContentRequestBody body) {
        return new SaveNotificationContentRequest(actor, session, ConfigurationInputs.code(templateCode, 64),
            NotificationInput.locale(locale), body.getSubjectTemplate(), body.getBodyTemplate(),
            body.getExpectedVersion(), ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 参数缺省视为空集合。 */
    public PreviewNotificationContentRequest toPreviewRequest(String actor, String session,
        PreviewNotificationContentRequestBody body) {
        return new PreviewNotificationContentRequest(actor, session, body.getSubjectTemplate(), body.getBodyTemplate(),
            body.getParameters() == null ? Map.of() : Map.copyOf(body.getParameters()));
    }

    /** 主备账号编码由 Body 注解校验，备用账号可空。 */
    public SaveNotificationRouteRequest toRouteRequest(String actor, String session, String templateCode,
        SaveNotificationRouteRequestBody body) {
        return new SaveNotificationRouteRequest(actor, session, ConfigurationInputs.code(templateCode, 64),
            body.getPrimaryProviderCode(), body.getFallbackProviderCode(), body.getExpectedVersion(),
            ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 补齐分页。 */
    public ListNotificationProvidersRequest toProvidersRequest(String actor, String session, Integer page,
        Integer size) {
        return new ListNotificationProvidersRequest(actor, session, ClientApiManagementInputs.paging(page, size));
    }

    /** 时间范围必填且不超过 31 天；文本条件为精确匹配。 */
    public ListNotificationRequestsRequest toRequestsRequest(String actor, String session, Integer page,
        Integer size, String createdFrom, String createdTo, String sourceSystem, String sourceEventKey,
        String templateCode, String deliveryStatus) {
        var filter = new NotificationRequestFilter();
        filter.setCreatedFrom(instant(createdFrom, "createdFrom"));
        filter.setCreatedTo(instant(createdTo, "createdTo"));
        if (!filter.getCreatedTo().isAfter(filter.getCreatedFrom())
            || Duration.between(filter.getCreatedFrom(), filter.getCreatedTo()).compareTo(MAX_RECORD_RANGE) > 0) {
            throw new InvalidParameterException("createdTo 必须晚于 createdFrom，且跨度不超过 31 天");
        }
        filter.setSourceSystem(sourceSystem == null ? null : ConfigurationInputs.text(sourceSystem, 64,
            "sourceSystem"));
        filter.setSourceEventKey(sourceEventKey == null ? null : ConfigurationInputs.text(sourceEventKey, 128,
            "sourceEventKey"));
        filter.setTemplateCode(templateCode == null ? null : ConfigurationInputs.code(templateCode, 64));
        filter.setDeliveryStatus(deliveryStatus == null ? null
            : code(NotificationDeliveryStatus.class, deliveryStatus, "deliveryStatus"));
        return new ListNotificationRequestsRequest(actor, session, ClientApiManagementInputs.paging(page, size),
            filter);
    }

    /** 请求 ID 必须为规范正整数；Path 变量不会为空。 */
    public GetNotificationRequestRequest toRequestDetailRequest(String actor, String session, String requestId) {
        return new GetNotificationRequestRequest(actor, session, ConfigurationInputs.id(requestId));
    }

    /** 列表项投影。 */
    public NotificationTemplateSummaryResponseBody toTemplateSummary(NotificationTemplateSummary item) {
        return new NotificationTemplateSummaryResponseBody(item.getTemplateCode(), item.getMedium().getCode(),
            item.getStatus().getCode(), item.getLocales(), item.isTransientTemplate(), codeOrNull(item.getRouteStatus()),
            item.getVersion(), item.getUpdateTime());
    }

    /** 详情投影；未配置路由时 route 为空。 */
    public NotificationTemplateDetailResponseBody toTemplateDetail(NotificationTemplateDetailView view) {
        var contents = view.getContents().stream().map(c -> new TemplateContentItem(c.getLocale(),
            c.getSubjectTemplate(), c.getBodyTemplate(), c.getVersion(), c.getUpdateTime())).toList();
        var route = view.getRoute() == null ? null : new TemplateRouteItem(view.getRoute().getPrimaryProviderCode(),
            view.getRoute().getFallbackProviderCode(), view.getRoute().getStatus().getCode(),
            view.getRoute().getVersion(), view.getRoute().getUpdateTime());
        return new NotificationTemplateDetailResponseBody(view.getTemplateCode(), view.getMedium().getCode(),
            view.getStatus().getCode(), view.isTransientTemplate(), view.getRequiredParameters(), view.getVersion(),
            view.getUpdateTime(), contents, route);
    }

    /** 写入结果只返回对象编码与新版本。 */
    public NotificationMutationResponseBody toMutation(NotificationConfigurationChange change) {
        return new NotificationMutationResponseBody(change.getCode(), change.getAfterVersion());
    }

    /** 预览结果投影。 */
    public NotificationPreviewResponseBody toPreview(NotificationPreview preview) {
        return new NotificationPreviewResponseBody(preview.getSubject(), preview.getBody(),
            preview.getMissingParameters());
    }

    /** 账号投影，不含发件地址、区域与凭据。 */
    public NotificationProviderResponseBody toProvider(NotificationProviderView view) {
        var references = view.getReferences().stream().map(r -> new ProviderReferenceItem(r.getTemplateCode(),
            r.getRole(), r.getRouteStatus().getCode())).toList();
        return new NotificationProviderResponseBody(view.getProviderCode(), view.getMedium().getCode(),
            view.getStatus().getCode(), view.isAdapterConfigured(), view.getVersion(), view.getUpdateTime(),
            references);
    }

    /** 记录列表投影；状态计数键为投递状态编码。 */
    public NotificationRequestSummaryResponseBody toRequestSummary(NotificationRequestSummaryView view) {
        Map<String, Integer> counts = view.getStatusCounts().entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().getCode(), Map.Entry::getValue));
        return new NotificationRequestSummaryResponseBody(Long.toString(view.getRequestId()), view.getSourceSystem(),
            view.getSourceEventKey(), view.getTemplateCode(), idOrNull(view.getTenantId()), view.getDefaultLocale(),
            view.getDeliveryCount(), counts, view.getCreateTime());
    }

    /** 记录详情投影。 */
    public NotificationRequestDetailResponseBody toRequestDetail(NotificationRequestDetailView view) {
        return new NotificationRequestDetailResponseBody(Long.toString(view.getRequestId()), view.getSourceSystem(),
            view.getSourceEventKey(), view.getTemplateCode(), idOrNull(view.getTenantId()), view.getDefaultLocale(),
            view.getParameterKeys(), Long.toString(view.getRouteId()), view.getCreateTime(),
            view.getDeliveries().stream().map(this::toDelivery).toList());
    }

    /** 投递投影。 */
    DeliveryItem toDelivery(NotificationDeliveryItem item) {
        return new DeliveryItem(Long.toString(item.getDeliveryId()), item.getLocale(), item.getParameterKeys(),
            item.getStatus().getCode(), item.getAttemptCount(), item.getNextRetryTime(), item.getUpdateTime(),
            item.getAttempts().stream().map(this::toAttempt).toList());
    }

    /** 尝试投影。 */
    AttemptItem toAttempt(NotificationAttemptItem item) {
        return new AttemptItem(Long.toString(item.getAttemptId()), item.getProviderCode(), item.getStatus().getCode(),
            item.getResponseCode(), codeOrNull(item.getResolutionSource()), item.getResolutionCode(),
            item.getStartedTime(), item.getFinishedTime(), item.getResolvedTime());
    }

    /** 按稳定编码解析闭合枚举，未知值返回 400。 */
    static <E extends Enum<E> & CodeEnum> E code(Class<E> type, String value, String field) {
        Map<String, E> byCode = Arrays.stream(type.getEnumConstants())
            .collect(Collectors.toMap(CodeEnum::getCode, Function.identity()));
        E result = value == null ? null : byCode.get(value);
        if (result == null) {
            throw new InvalidParameterException(field + " 不是支持的值");
        }
        return result;
    }

    /** ISO-8601 UTC 时间，必填。 */
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

    /** 可空枚举的编码。 */
    static String codeOrNull(CodeEnum value) {
        return value == null ? null : value.getCode();
    }

    /** 可空 ID 的十进制字符串。 */
    static String idOrNull(Long value) {
        return value == null ? null : Long.toString(value);
    }
}
