package com.paytech.platform.support.delivery.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookEndpoint;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookEndpointTenantCount;
import com.paytech.platform.support.delivery.core.dal.mapper.WebhookEndpointMapper;
import com.paytech.platform.support.delivery.core.enums.WebhookEndpointStatus;
import com.paytech.platform.support.delivery.domainservice.repository.WebhookEndpointRepository;

import lombok.RequiredArgsConstructor;

/** 单表访问及写入行数保护，不自行创建事务。 */
@Repository
@RequiredArgsConstructor
public class WebhookEndpointRepositoryImpl implements WebhookEndpointRepository {

    private final WebhookEndpointMapper mapper;

    /** 读取端点，缺失返回空。 */
    @Override
    public WebhookEndpoint find(long id) {
        return mapper.find(id);
    }

    /** 按创建顺序读取租户的全部端点。 */
    @Override
    public List<WebhookEndpoint> findTenant(long tenantId) {
        return mapper.findTenant(tenantId);
    }

    /** 按租户统计端点数量。 */
    @Override
    public List<WebhookEndpointTenantCount> countTenants(List<Long> tenantIds) {
        return mapper.countTenants(tenantIds);
    }

    /** 插入新端点。 */
    @Override
    public void insert(WebhookEndpoint item) {
        if (mapper.insert(item) != 1) {
            throw new ErrorException("Webhook 端点插入失败");
        }
    }

    /** 按旧版本修改地址与订阅；成功后同步原对象。 */
    @Override
    public void update(WebhookEndpoint item, String url, String eventTypes, Instant now) {
        if (mapper.update(item, url, eventTypes, now) != 1) {
            throw new ConcurrentRequestException("Webhook 端点已被修改，请刷新后重试");
        }
        item.setUrl(url);
        item.setEventTypes(eventTypes);
        item.setVersion(item.getVersion() + 1);
        item.setUpdateTime(now);
    }

    /** 按旧版本与旧状态修改状态；成功后同步原对象。 */
    @Override
    public void changeStatus(WebhookEndpoint item, WebhookEndpointStatus status, Instant now) {
        if (mapper.changeStatus(item, status, now) != 1) {
            throw new ConcurrentRequestException("Webhook 端点已被修改，请刷新后重试");
        }
        item.setStatus(status);
        item.setVersion(item.getVersion() + 1);
        item.setUpdateTime(now);
    }
}
