package com.paytech.platform.support.delivery.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationAttempt;
import com.paytech.platform.support.delivery.core.enums.*;

/** NotificationAttempt 单表仓储。 */
public interface NotificationAttemptRepository {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationAttempt find(long id);

    /** 按投递、尝试身份升序读取一组投递的全部尝试；空集合直接返回空列表。 */
    List<NotificationAttempt> findDeliveries(List<Long> deliveryIds);

    /** 插入新事实；唯一约束冲突使整个受理事务回滚。 */
    void insert(NotificationAttempt item);

    /** 读取投递最新一次发送尝试，没有时返回空；更早的 UNKNOWN 尝试只作历史。 */
    NotificationAttempt findCurrent(long deliveryId);

    /** 与 Delivery 版本更新处于同一事务，成功后同步原尝试。 */
    void resolve(NotificationAttempt item, NotificationAttemptStatus status, String messageId, String code, NotificationResolutionSource source, Instant now);
}
