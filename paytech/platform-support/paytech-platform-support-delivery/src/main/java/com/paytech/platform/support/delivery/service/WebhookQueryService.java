package com.paytech.platform.support.delivery.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.delivery.domain.WebhookDeliveryDetailView;
import com.paytech.platform.support.delivery.domain.WebhookDeliveryFilter;
import com.paytech.platform.support.delivery.domain.WebhookDeliverySummaryView;
import com.paytech.platform.support.delivery.domain.WebhookEndpointView;

/** 运营查询；不返回密钥值或 Vault Token。 */
public interface WebhookQueryService {

    /** 租户的全部端点及当前签名密钥概况。 */
    List<WebhookEndpointView> endpoints(long tenantId, Instant now);

    /**
     * 按租户统计端点数量（含已停用）
     *
     * @param tenantIds CLIENT 租户 ID，可为空列表
     * @return 租户 ID 到端点数量；没有端点的租户不在结果中
     */
    Map<Long, Integer> endpointCounts(List<Long> tenantIds);

    /** 投递分页。 */
    PagingResponseBody<WebhookDeliverySummaryView> deliveries(WebhookDeliveryFilter filter, PagingRequestBody paging);

    /** 投递详情；不存在返回 NOT_FOUND。 */
    WebhookDeliveryDetailView delivery(long deliveryId);
}
