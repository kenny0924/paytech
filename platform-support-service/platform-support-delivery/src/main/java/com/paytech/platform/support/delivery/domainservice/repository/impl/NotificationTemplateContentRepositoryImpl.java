package com.paytech.platform.support.delivery.domainservice.repository.impl;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationTemplateContent;
import com.paytech.platform.support.delivery.core.dal.mapper.NotificationTemplateContentMapper;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationTemplateContentRepository;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

/** 单表访问及写入行数保护，不自行创建事务。 */
@Repository
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "notification.enabled", havingValue = "true")
public class NotificationTemplateContentRepositoryImpl implements NotificationTemplateContentRepository {

    private final NotificationTemplateContentMapper mapper;

    /** 读取本表的准确身份，缺失返回空。 */
    @Override
    public NotificationTemplateContent find(long id) {
        return mapper.find(id);
    }

    /** 读取精确语言内容，不执行语言回退。 */
    @Override
    public NotificationTemplateContent findLocale(long templateId, String locale) {
        return mapper.findLocale(templateId, locale);
    }
}
