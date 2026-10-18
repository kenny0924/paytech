package com.paytech.platform.support.delivery.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookEndpoint;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookEndpointTenantCount;
import com.paytech.platform.support.delivery.core.enums.WebhookEndpointStatus;

/** WebhookEndpoint 单表仓储。 */
public interface WebhookEndpointRepository {

    /** 读取端点，缺失返回空。 */
    WebhookEndpoint find(long id);

    /** 按创建顺序读取租户的全部端点。 */
    List<WebhookEndpoint> findTenant(long tenantId);

    /** 按租户统计端点数量（含已停用）；没有端点的租户不出现在结果中。 */
    List<WebhookEndpointTenantCount> countTenants(List<Long> tenantIds);

    /** 插入新端点。 */
    void insert(WebhookEndpoint item);

    /** 按旧版本修改地址与订阅；成功后同步原对象，版本不符抛并发冲突。 */
    void update(WebhookEndpoint item, String url, String eventTypes, Instant now);

    /** 按旧版本与旧状态修改状态；成功后同步原对象，版本不符抛并发冲突。 */
    void changeStatus(WebhookEndpoint item, WebhookEndpointStatus status, Instant now);
}
