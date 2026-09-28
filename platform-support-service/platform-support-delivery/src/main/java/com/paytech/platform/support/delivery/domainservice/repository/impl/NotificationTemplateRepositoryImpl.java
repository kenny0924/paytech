package com.paytech.platform.support.delivery.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationTemplate;
import com.paytech.platform.support.delivery.core.dal.mapper.NotificationTemplateMapper;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationTemplateRepository;

import lombok.RequiredArgsConstructor;

/** 单表访问及写入行数保护，不自行创建事务。 */
@Repository
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "notification.enabled",
    havingValue = "true")
public class NotificationTemplateRepositoryImpl implements NotificationTemplateRepository {

    private final NotificationTemplateMapper mapper;

    /** 读取本表的准确身份，缺失返回空。 */
    @Override
    public NotificationTemplate find(long id) {
        return mapper.find(id);
    }

    /** 按唯一模板编码读取当前定义。 */
    @Override
    public NotificationTemplate findCode(String code) {
        return mapper.findCode(code);
    }
}
