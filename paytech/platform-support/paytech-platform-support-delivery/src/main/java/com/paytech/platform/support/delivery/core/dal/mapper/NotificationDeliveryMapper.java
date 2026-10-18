package com.paytech.platform.support.delivery.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationDelivery;
import com.paytech.platform.support.delivery.core.enums.*;

/** 通知单表 SQL 绑定。 */
public interface NotificationDeliveryMapper {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationDelivery find(@Param("id") long id);

    /** 按投递身份升序读取一组请求的全部投递。 */
    List<NotificationDelivery> findRequests(@Param("requestIds") List<Long> requestIds);

    /** 插入新事实；唯一约束冲突使整个受理事务回滚。 */
    int insert(NotificationDelivery item);

    /** 按旧版本及状态推进投递；成功后才同步原对象。 */
    int transition(@Param("item") NotificationDelivery item, @Param("status") NotificationDeliveryStatus status, @Param("attemptCount") int attemptCount, @Param("nextRetryTime") Instant nextRetryTime, @Param("now") Instant now);
}
