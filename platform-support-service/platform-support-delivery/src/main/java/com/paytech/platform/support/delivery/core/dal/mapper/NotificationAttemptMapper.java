package com.paytech.platform.support.delivery.core.dal.mapper;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationAttempt;
import java.time.Instant;
import com.paytech.platform.support.delivery.core.enums.*;
import org.apache.ibatis.annotations.Param;

/** 通知单表 SQL 绑定。 */
public interface NotificationAttemptMapper {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationAttempt find(@Param("id") long id);

    /** 插入新事实；唯一约束冲突使整个受理事务回滚。 */
    int insert(NotificationAttempt item);

    /** 读取尚未明确失败的尝试；由用例验证唯一性。 */
    java.util.List<NotificationAttempt> findOpen(@Param("deliveryId") long deliveryId);

    /** 与 Delivery 版本更新处于同一事务，成功后同步原尝试。 */
    int resolve(@Param("item") NotificationAttempt item, @Param("status") NotificationAttemptStatus status, @Param("messageId") String messageId, @Param("code") String code, @Param("source") NotificationResolutionSource source, @Param("now") Instant now);
}
