package com.paytech.platform.support.delivery.domainservice.repository;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationTemplateContent;

/** NotificationTemplateContent 单表仓储。 */
public interface NotificationTemplateContentRepository {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationTemplateContent find(long id);

    /** 读取精确语言内容，不执行语言回退。 */
    NotificationTemplateContent findLocale(long templateId, String locale);
}
