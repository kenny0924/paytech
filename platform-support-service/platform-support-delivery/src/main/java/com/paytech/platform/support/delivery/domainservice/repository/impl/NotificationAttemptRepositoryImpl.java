package com.paytech.platform.support.delivery.domainservice.repository.impl;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationAttempt;
import java.time.Instant;
import com.paytech.platform.support.delivery.core.enums.*;
import com.paytech.platform.support.delivery.core.dal.mapper.NotificationAttemptMapper;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationAttemptRepository;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

/** 单表访问及写入行数保护，不自行创建事务。 */
@Repository
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "notification.enabled", havingValue = "true")
public class NotificationAttemptRepositoryImpl implements NotificationAttemptRepository {

    private final NotificationAttemptMapper mapper;

    /** 读取本表的准确身份，缺失返回空。 */
    @Override
    public NotificationAttempt find(long id) {
        return mapper.find(id);
    }

    /** 插入新事实；唯一约束冲突使整个受理事务回滚。 */
    @Override
    public void insert(NotificationAttempt item) {
        if (mapper.insert(item) != 1) throw new ErrorException("通知事实插入失败");
    }

    /** 读取尚未明确失败的尝试；由用例验证唯一性。 */
    @Override
    public java.util.List<NotificationAttempt> findOpen(long deliveryId) {
        return mapper.findOpen(deliveryId);
    }

    /** 与 Delivery 版本更新处于同一事务，成功后同步原尝试。 */
    @Override
    public void resolve(NotificationAttempt item, NotificationAttemptStatus status, String messageId, String code, NotificationResolutionSource source, Instant now) {
        if (mapper.resolve(item, status, messageId, code, source, now) != 1) {
            throw new ConcurrentRequestException("通知尝试已变更");
        }
        item.setStatus(status);
        if (messageId != null) item.setProviderMessageId(messageId);
        item.setResolutionSource(source);
        if (source == NotificationResolutionSource.SEND_RESPONSE) {
            item.setResponseCode(code);
            item.setFinishedTime(now);
        } else {
            item.setResolutionCode(code);
        }
        if (status == NotificationAttemptStatus.ACCEPTED || status == NotificationAttemptStatus.FAILED) {
            if (item.getResolvedTime() == null) item.setResolvedTime(now);
        }
        item.setUpdateTime(now);
    }
}
