package com.paytech.platform.support.delivery.domainservice.repository;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationAttempt;
import java.time.Instant;
import com.paytech.platform.support.delivery.core.enums.*;

/** NotificationAttempt 单表仓储。 */
public interface NotificationAttemptRepository {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationAttempt find(long id);

    /** 插入新事实；唯一约束冲突使整个受理事务回滚。 */
    void insert(NotificationAttempt item);

    /** 读取尚未明确失败的尝试；由用例验证唯一性。 */
    java.util.List<NotificationAttempt> findOpen(long deliveryId);

    /** 与 Delivery 版本更新处于同一事务，成功后同步原尝试。 */
    void resolve(NotificationAttempt item, NotificationAttemptStatus status, String messageId, String code, NotificationResolutionSource source, Instant now);
}
