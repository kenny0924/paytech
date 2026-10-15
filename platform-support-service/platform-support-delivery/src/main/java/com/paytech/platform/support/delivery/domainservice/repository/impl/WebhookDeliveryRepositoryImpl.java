package com.paytech.platform.support.delivery.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookDelivery;
import com.paytech.platform.support.delivery.core.dal.mapper.WebhookDeliveryMapper;
import com.paytech.platform.support.delivery.core.enums.WebhookDeliveryStatus;
import com.paytech.platform.support.delivery.domain.WebhookDeliveryFilter;
import com.paytech.platform.support.delivery.domain.WebhookDeliverySummaryView;
import com.paytech.platform.support.delivery.domainservice.repository.WebhookDeliveryRepository;

import lombok.RequiredArgsConstructor;

/** 投递访问及写入行数保护，不自行创建事务。 */
@Repository
@RequiredArgsConstructor
public class WebhookDeliveryRepositoryImpl implements WebhookDeliveryRepository {

    private final WebhookDeliveryMapper mapper;

    /** 读取投递，缺失返回空。 */
    @Override
    public WebhookDelivery find(long id) {
        return mapper.find(id);
    }

    /** 统计事件的投递数量。 */
    @Override
    public int countEvent(long eventId) {
        return mapper.countEvent(eventId);
    }

    /** 插入新投递。 */
    @Override
    public void insert(WebhookDelivery item) {
        if (mapper.insert(item) != 1) {
            throw new ErrorException("Webhook 投递插入失败");
        }
    }

    /** 按旧版本与旧状态推进投递；成功后同步原对象。 */
    @Override
    public void transition(WebhookDelivery item, WebhookDeliveryStatus status, int attemptCount,
        Instant lastAttemptTime, Instant now) {
        if (mapper.transition(item, status, attemptCount, lastAttemptTime, now) != 1) {
            throw new ConcurrentRequestException("Webhook 投递已由其他执行者推进");
        }
        item.setStatus(status);
        item.setAttemptCount(attemptCount);
        item.setLastAttemptTime(lastAttemptTime);
        item.setVersion(item.getVersion() + 1);
        item.setUpdateTime(now);
    }

    /** 按创建时间倒序分页。 */
    @Override
    public List<WebhookDeliverySummaryView> page(WebhookDeliveryFilter filter, long offset, int size) {
        return mapper.page(filter, offset, size);
    }

    /** 与分页相同条件的总数。 */
    @Override
    public long count(WebhookDeliveryFilter filter) {
        return mapper.count(filter);
    }
}
