package com.paytech.platform.support.delivery.domainservice.repository;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationTemplate;

/** NotificationTemplate 单表仓储。 */
public interface NotificationTemplateRepository {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationTemplate find(long id);

    /** 按唯一模板编码读取当前定义。 */
    NotificationTemplate findCode(String code);
}
