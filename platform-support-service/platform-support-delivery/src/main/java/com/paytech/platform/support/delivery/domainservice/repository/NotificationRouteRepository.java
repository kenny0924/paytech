package com.paytech.platform.support.delivery.domainservice.repository;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationRoute;

/** NotificationRoute 单表仓储。 */
public interface NotificationRouteRepository {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationRoute find(long id);

    /** 读取模板唯一的当前路由。 */
    NotificationRoute findTemplate(long templateId);
}
