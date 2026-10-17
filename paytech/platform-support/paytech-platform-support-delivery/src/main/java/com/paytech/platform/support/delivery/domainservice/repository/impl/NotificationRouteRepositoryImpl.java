package com.paytech.platform.support.delivery.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationRoute;
import com.paytech.platform.support.delivery.core.dal.mapper.NotificationRouteMapper;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationRouteRepository;

import lombok.RequiredArgsConstructor;

/** 单表访问及写入行数保护，不自行创建事务；只读写本库，不依赖 notification.enabled。 */
@Repository
@RequiredArgsConstructor
public class NotificationRouteRepositoryImpl implements NotificationRouteRepository {

    private final NotificationRouteMapper mapper;

    /** 读取本表的准确身份，缺失返回空。 */
    @Override
    public NotificationRoute find(long id) {
        return mapper.find(id);
    }

    /** 读取模板唯一的当前路由。 */
    @Override
    public NotificationRoute findTemplate(long templateId) {
        return mapper.findTemplate(templateId);
    }

    /** 读取一组模板的路由；空集合直接返回空列表。 */
    @Override
    public List<NotificationRoute> findTemplates(List<Long> templateIds) {
        if (templateIds.isEmpty()) {
            return List.of();
        }
        return mapper.findTemplates(templateIds);
    }

    /** 读取以一组账号为主或备用账号的路由；空集合直接返回空列表。 */
    @Override
    public List<NotificationRoute> findProviders(List<Long> providerIds) {
        if (providerIds.isEmpty()) {
            return List.of();
        }
        return mapper.findProviders(providerIds);
    }

    /** 插入新路由；模板唯一冲突由调用方事务回滚。 */
    @Override
    public void insert(NotificationRoute item) {
        if (mapper.insert(item) != 1) {
            throw new ErrorException("通知路由插入失败");
        }
    }

    /** 按原版本替换主备账号；成功后同步原对象。 */
    @Override
    public void changeProviders(NotificationRoute item, long primary, Long fallback, Instant now) {
        if (mapper.changeProviders(item, primary, fallback, now) != 1) {
            throw new ConcurrentRequestException("通知路由已被其他操作修改，请刷新后重试");
        }
        item.setPrimaryProviderId(primary);
        item.setFallbackProviderId(fallback);
        item.setVersion(item.getVersion() + 1);
        item.setUpdateTime(now);
    }

    /** 按原版本和原状态切换启停；成功后同步原对象。 */
    @Override
    public void changeStatus(NotificationRoute item, NotificationConfigurationStatus status, Instant now) {
        if (mapper.changeStatus(item, status, now) != 1) {
            throw new ConcurrentRequestException("通知路由已被其他操作修改，请刷新后重试");
        }
        item.setStatus(status);
        item.setVersion(item.getVersion() + 1);
        item.setUpdateTime(now);
    }
}
