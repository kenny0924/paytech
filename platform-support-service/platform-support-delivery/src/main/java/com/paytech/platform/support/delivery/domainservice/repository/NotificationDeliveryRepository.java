package com.paytech.platform.support.delivery.domainservice.repository;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationDelivery;
import java.time.Instant;
import com.paytech.platform.support.delivery.core.enums.*;

/** NotificationDelivery 单表仓储。 */
public interface NotificationDeliveryRepository {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationDelivery find(long id);

    /** 插入新事实；唯一约束冲突使整个受理事务回滚。 */
    void insert(NotificationDelivery item);

    /** 按旧版本及状态推进投递；成功后才同步原对象。 */
    void transition(NotificationDelivery item, NotificationDeliveryStatus status, int attemptCount, Instant nextRetryTime, Instant now);
}
