package com.paytech.platform.support.delivery.domainservice.repository.impl;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationDelivery;
import com.paytech.platform.support.delivery.core.dal.mapper.NotificationDeliveryMapper;
import com.paytech.platform.support.delivery.core.enums.NotificationDeliveryStatus;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationDeliveryRepository;

import lombok.RequiredArgsConstructor;

/** 单表访问及写入行数保护，不自行创建事务。 */
@Repository
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "notification.enabled",
    havingValue = "true")
public class NotificationDeliveryRepositoryImpl implements NotificationDeliveryRepository {

    private final NotificationDeliveryMapper mapper;

    /** 读取本表的准确身份，缺失返回空。 */
    @Override
    public NotificationDelivery find(long id) {
        return mapper.find(id);
    }

    /** 插入新事实；唯一约束冲突使整个受理事务回滚。 */
    @Override
    public void insert(NotificationDelivery item) {
        if (mapper.insert(item) != 1)
            throw new ErrorException("通知事实插入失败");
    }

    /** 按旧版本及状态推进投递；成功后才同步原对象。 */
    @Override
    public void transition(NotificationDelivery item, NotificationDeliveryStatus status, int attemptCount,
        Instant nextRetryTime, Instant now) {
        if (mapper.transition(item, status, attemptCount, nextRetryTime, now) != 1) {
            throw new ConcurrentRequestException("通知投递已由其他执行者推进");
        }
        item.setStatus(status);
        item.setAttemptCount(attemptCount);
        item.setNextRetryTime(nextRetryTime);
        item.setVersion(item.getVersion() + 1);
        item.setUpdateTime(now);
    }
}
