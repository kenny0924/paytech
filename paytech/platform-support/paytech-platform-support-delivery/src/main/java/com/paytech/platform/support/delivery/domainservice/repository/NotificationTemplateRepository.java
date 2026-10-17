package com.paytech.platform.support.delivery.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationTemplate;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;

/** NotificationTemplate 单表仓储。 */
public interface NotificationTemplateRepository {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationTemplate find(long id);

    /** 按唯一模板编码读取当前定义。 */
    NotificationTemplate findCode(String code);

    /** 按身份批量读取；空集合直接返回空列表。 */
    List<NotificationTemplate> findIds(List<Long> ids);

    /** 按编码升序分页，keyword 为已转义的 LIKE 模式，可空。 */
    List<NotificationTemplate> page(String keyword, NotificationConfigurationStatus status, long offset, int size);

    /** 与分页相同条件的总数。 */
    long count(String keyword, NotificationConfigurationStatus status);

    /** 插入新模板；编码唯一冲突由调用方事务回滚。 */
    void insert(NotificationTemplate item);

    /** 按原版本和原状态切换启停；成功后同步原对象。 */
    void changeStatus(NotificationTemplate item, NotificationConfigurationStatus status, Instant now);
}
