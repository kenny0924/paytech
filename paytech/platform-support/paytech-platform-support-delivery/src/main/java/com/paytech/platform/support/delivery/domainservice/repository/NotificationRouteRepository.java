package com.paytech.platform.support.delivery.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationRoute;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;

/** NotificationRoute 单表仓储。 */
public interface NotificationRouteRepository {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationRoute find(long id);

    /** 读取模板唯一的当前路由。 */
    NotificationRoute findTemplate(long templateId);

    /** 读取一组模板的路由；空集合直接返回空列表。 */
    List<NotificationRoute> findTemplates(List<Long> templateIds);

    /** 读取以一组账号为主或备用账号的路由；空集合直接返回空列表。 */
    List<NotificationRoute> findProviders(List<Long> providerIds);

    /** 插入新路由；模板唯一冲突由调用方事务回滚。 */
    void insert(NotificationRoute item);

    /** 按原版本替换主备账号；成功后同步原对象。 */
    void changeProviders(NotificationRoute item, long primary, Long fallback, Instant now);

    /** 按原版本和原状态切换启停；成功后同步原对象。 */
    void changeStatus(NotificationRoute item, NotificationConfigurationStatus status, Instant now);
}
