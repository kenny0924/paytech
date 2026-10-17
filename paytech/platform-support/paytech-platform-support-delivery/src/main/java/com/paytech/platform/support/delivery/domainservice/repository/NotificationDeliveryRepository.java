package com.paytech.platform.support.delivery.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationDelivery;
import com.paytech.platform.support.delivery.core.enums.*;

/** NotificationDelivery 单表仓储。 */
public interface NotificationDeliveryRepository {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationDelivery find(long id);

    /** 按投递身份升序读取一组请求的全部投递；空集合直接返回空列表。 */
    List<NotificationDelivery> findRequests(List<Long> requestIds);

    /** 插入新事实；唯一约束冲突使整个受理事务回滚。 */
    void insert(NotificationDelivery item);

    /** 按旧版本及状态推进投递；成功后才同步原对象。 */
    void transition(NotificationDelivery item, NotificationDeliveryStatus status, int attemptCount, Instant nextRetryTime, Instant now);
}
