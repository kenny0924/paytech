package com.paytech.platform.support.delivery.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationTemplateContent;

/** NotificationTemplateContent 单表仓储。 */
public interface NotificationTemplateContentRepository {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationTemplateContent find(long id);

    /** 读取精确语言内容，不执行语言回退。 */
    NotificationTemplateContent findLocale(long templateId, String locale);

    /** 按模板、语言升序读取一组模板的全部内容；空集合直接返回空列表。 */
    List<NotificationTemplateContent> findTemplates(List<Long> templateIds);

    /** 统计模板已有语言数。 */
    long countTemplate(long templateId);

    /** 插入新语言内容；模板与语言唯一冲突由调用方事务回滚。 */
    void insert(NotificationTemplateContent item);

    /** 按原版本替换标题与正文；成功后同步原对象。 */
    void update(NotificationTemplateContent item, String subject, String body, Instant now);
}
