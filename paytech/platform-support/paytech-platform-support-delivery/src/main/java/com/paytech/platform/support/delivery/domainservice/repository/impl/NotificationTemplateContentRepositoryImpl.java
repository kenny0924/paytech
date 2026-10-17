package com.paytech.platform.support.delivery.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationTemplateContent;
import com.paytech.platform.support.delivery.core.dal.mapper.NotificationTemplateContentMapper;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationTemplateContentRepository;

import lombok.RequiredArgsConstructor;

/** 单表访问及写入行数保护，不自行创建事务；只读写本库，不依赖 notification.enabled。 */
@Repository
@RequiredArgsConstructor
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

    /** 按模板、语言升序读取一组模板的全部内容；空集合直接返回空列表。 */
    @Override
    public List<NotificationTemplateContent> findTemplates(List<Long> templateIds) {
        if (templateIds.isEmpty()) {
            return List.of();
        }
        return mapper.findTemplates(templateIds);
    }

    /** 统计模板已有语言数。 */
    @Override
    public long countTemplate(long templateId) {
        return mapper.countTemplate(templateId);
    }

    /** 插入新语言内容；模板与语言唯一冲突由调用方事务回滚。 */
    @Override
    public void insert(NotificationTemplateContent item) {
        if (mapper.insert(item) != 1) {
            throw new ErrorException("通知语言内容插入失败");
        }
    }

    /** 按原版本替换标题与正文；成功后同步原对象。 */
    @Override
    public void update(NotificationTemplateContent item, String subject, String body, Instant now) {
        if (mapper.update(item, subject, body, now) != 1) {
            throw new ConcurrentRequestException("通知语言内容已被其他操作修改，请刷新后重试");
        }
        item.setSubjectTemplate(subject);
        item.setBodyTemplate(body);
        item.setVersion(item.getVersion() + 1);
        item.setUpdateTime(now);
    }
}
