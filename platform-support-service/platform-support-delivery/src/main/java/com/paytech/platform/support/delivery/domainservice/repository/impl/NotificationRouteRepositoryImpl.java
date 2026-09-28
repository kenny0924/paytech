package com.paytech.platform.support.delivery.domainservice.repository.impl;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationRoute;
import com.paytech.platform.support.delivery.core.dal.mapper.NotificationRouteMapper;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationRouteRepository;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

/** 单表访问及写入行数保护，不自行创建事务。 */
@Repository
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "notification.enabled", havingValue = "true")
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
}
