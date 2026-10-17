package com.paytech.platform.support.delivery.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationRequest;
import com.paytech.platform.support.delivery.domain.NotificationRequestFilter;

/** NotificationRequest 单表仓储。 */
public interface NotificationRequestRepository {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationRequest find(long id);

    /** 插入新事实；唯一约束冲突使整个受理事务回滚。 */
    void insert(NotificationRequest item);

    /** 按运营筛选条件、创建时间倒序分页。 */
    List<NotificationRequest> page(NotificationRequestFilter filter, long offset, int size);

    /** 与分页相同条件的总数。 */
    long count(NotificationRequestFilter filter);

    /** 查找来源系统内已受理的通知意图。 */
    NotificationRequest findSource(String sourceSystem, String sourceEventKey);
}
