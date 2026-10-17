package com.paytech.platform.support.delivery.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookEndpoint;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookEndpointTenantCount;
import com.paytech.platform.support.delivery.core.enums.WebhookEndpointStatus;

/** Webhook 端点单表 SQL 绑定。 */
public interface WebhookEndpointMapper {

    /** 读取端点，缺失返回空。 */
    WebhookEndpoint find(@Param("id") long id);

    /** 按创建顺序读取租户的全部端点。 */
    List<WebhookEndpoint> findTenant(@Param("tenantId") long tenantId);

    /** 按租户统计端点数量；tenantIds 不能为空。 */
    List<WebhookEndpointTenantCount> countTenants(@Param("tenantIds") List<Long> tenantIds);

    /** 插入新端点。 */
    int insert(WebhookEndpoint item);

    /** 按旧版本修改地址与订阅。 */
    int update(@Param("item") WebhookEndpoint item, @Param("url") String url, @Param("eventTypes") String eventTypes,
        @Param("now") Instant now);

    /** 按旧版本与旧状态修改状态。 */
    int changeStatus(@Param("item") WebhookEndpoint item, @Param("status") WebhookEndpointStatus status,
        @Param("now") Instant now);
}
