package com.paytech.platform.support.delivery.service.impl;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationAttempt;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationDelivery;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationProvider;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationRequest;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationTemplate;
import com.paytech.platform.support.delivery.core.enums.NotificationDeliveryStatus;
import com.paytech.platform.support.delivery.domain.NotificationAttemptItem;
import com.paytech.platform.support.delivery.domain.NotificationDeliveryItem;
import com.paytech.platform.support.delivery.domain.NotificationInput;
import com.paytech.platform.support.delivery.domain.NotificationRequestDetailView;
import com.paytech.platform.support.delivery.domain.NotificationRequestFilter;
import com.paytech.platform.support.delivery.domain.NotificationRequestSummaryView;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationAttemptRepository;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationDeliveryRepository;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationProviderRepository;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationRequestRepository;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationTemplateRepository;
import com.paytech.platform.support.delivery.service.NotificationRecordQueryService;

import lombok.RequiredArgsConstructor;

/** 运营查询发送记录：只读本库，不返回邮箱、Vault Token、参数值和供应商流水号。 */
@Service
@RequiredArgsConstructor
public class NotificationRecordQueryServiceImpl implements NotificationRecordQueryService {

    private final NotificationRequestRepository requests;
    private final NotificationAttemptRepository attempts;
    private final NotificationTemplateRepository templates;
    private final NotificationProviderRepository providers;
    private final NotificationDeliveryRepository deliveries;

    /** 先解析模板编码，再分页读取请求及其投递状态计数。 */
    @Override
    public PagingResponseBody<NotificationRequestSummaryView> requests(NotificationRequestFilter filter,
        PagingRequestBody paging) {
        if (filter.getTemplateCode() != null) {
            NotificationTemplate template = templates.findCode(filter.getTemplateCode());
            if (template == null) {
                return new PagingResponseBody<>(List.of(), paging.page(), paging.size(), 0);
            }
            filter.setTemplateId(template.getTemplateId());
        }
        var rows = requests.page(filter, paging.offset(), paging.size());
        var requestIds = rows.stream().map(NotificationRequest::getRequestId).toList();
        Map<Long, List<NotificationDelivery>> byRequest = deliveries.findRequests(requestIds).stream()
            .collect(Collectors.groupingBy(NotificationDelivery::getRequestId));
        var templateCodes = templateCodes(rows);
        var items = rows.stream().map(r -> summary(r, templateCodes.get(r.getNotificationTemplateId()),
            byRequest.getOrDefault(r.getRequestId(), List.of()))).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), requests.count(filter));
    }

    /** 列表项：只给出各投递状态的数量。 */
    NotificationRequestSummaryView summary(NotificationRequest request, String templateCode,
        List<NotificationDelivery> items) {
        var counts = new EnumMap<NotificationDeliveryStatus, Integer>(NotificationDeliveryStatus.class);
        items.forEach(d -> counts.merge(d.getStatus(), 1, Integer::sum));
        return new NotificationRequestSummaryView(request.getRequestId(), request.getSourceSystem(),
            request.getSourceEventKey(), templateCode, request.getTenantId(), request.getDefaultLocale(), items.size(),
            counts, request.getCreateTime());
    }

    /** 请求、投递与尝试；账号以编码表示。 */
    @Override
    public NotificationRequestDetailView request(long requestId) {
        NotificationRequest request = requests.find(requestId);
        if (request == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        var deliveryRows = deliveries.findRequests(List.of(requestId));
        var attemptRows = attempts.findDeliveries(deliveryRows.stream().map(NotificationDelivery::getDeliveryId)
            .toList());
        Map<Long, String> providerCodes = providers.findIds(attemptRows.stream().map(NotificationAttempt::getProviderId)
            .distinct().toList()).stream()
            .collect(Collectors.toMap(NotificationProvider::getProviderId, NotificationProvider::getProviderCode));
        Map<Long, List<NotificationAttempt>> byDelivery = attemptRows.stream()
            .collect(Collectors.groupingBy(NotificationAttempt::getDeliveryId));
        var items = deliveryRows.stream().map(d -> deliveryItem(d,
            byDelivery.getOrDefault(d.getDeliveryId(), List.of()), providerCodes)).toList();
        return new NotificationRequestDetailView(request.getRequestId(), request.getSourceSystem(),
            request.getSourceEventKey(), templateCodes(List.of(request)).get(request.getNotificationTemplateId()),
            request.getTenantId(), request.getDefaultLocale(),
            NotificationInput.parameterKeys(request.getDefaultParameters()), request.getNotificationRouteId(),
            request.getCreateTime(), items);
    }

    /** 投递与其尝试；不含收件邮箱 Token 与参数值。 */
    NotificationDeliveryItem deliveryItem(NotificationDelivery delivery, List<NotificationAttempt> items,
        Map<Long, String> providerCodes) {
        var attemptItems = items.stream().map(a -> new NotificationAttemptItem(a.getAttemptId(),
            providerCodes.get(a.getProviderId()), a.getStatus(), a.getResponseCode(), a.getResolutionSource(),
            a.getResolutionCode(), a.getStartedTime(), a.getFinishedTime(), a.getResolvedTime())).toList();
        return new NotificationDeliveryItem(delivery.getDeliveryId(), delivery.getLocale(),
            NotificationInput.parameterKeys(delivery.getParameters()), delivery.getStatus(),
            delivery.getAttemptCount(), delivery.getNextRetryTime(), delivery.getUpdateTime(), attemptItems);
    }

    /** 请求引用的模板身份到编码。 */
    Map<Long, String> templateCodes(List<NotificationRequest> rows) {
        return templates.findIds(rows.stream().map(NotificationRequest::getNotificationTemplateId).distinct()
            .toList()).stream()
            .collect(Collectors.toMap(NotificationTemplate::getTemplateId, NotificationTemplate::getTemplateCode));
    }
}
