package com.paytech.platform.support.delivery.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationAttempt;
import com.paytech.platform.support.delivery.core.enums.*;

/** 通知单表 SQL 绑定。 */
public interface NotificationAttemptMapper {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationAttempt find(@Param("id") long id);

    /** 按投递、尝试身份升序读取一组投递的全部尝试。 */
    List<NotificationAttempt> findDeliveries(@Param("deliveryIds") List<Long> deliveryIds);

    /** 插入新事实；唯一约束冲突使整个受理事务回滚。 */
    int insert(NotificationAttempt item);

    /** 读取投递最新一次发送尝试，没有时返回空；更早的 UNKNOWN 尝试只作历史。 */
    NotificationAttempt findCurrent(@Param("deliveryId") long deliveryId);

    /** 与 Delivery 版本更新处于同一事务，成功后同步原尝试。 */
    int resolve(@Param("item") NotificationAttempt item, @Param("status") NotificationAttemptStatus status, @Param("messageId") String messageId, @Param("code") String code, @Param("source") NotificationResolutionSource source, @Param("now") Instant now);
}
