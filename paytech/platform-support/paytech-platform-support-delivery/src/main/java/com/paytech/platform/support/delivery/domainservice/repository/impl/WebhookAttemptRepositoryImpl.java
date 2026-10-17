package com.paytech.platform.support.delivery.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookAttempt;
import com.paytech.platform.support.delivery.core.dal.mapper.WebhookAttemptMapper;
import com.paytech.platform.support.delivery.core.enums.WebhookAttemptStatus;
import com.paytech.platform.support.delivery.core.enums.WebhookFailureCode;
import com.paytech.platform.support.delivery.domainservice.repository.WebhookAttemptRepository;

import lombok.RequiredArgsConstructor;

/** 单表访问及写入行数保护，不自行创建事务。 */
@Repository
@RequiredArgsConstructor
public class WebhookAttemptRepositoryImpl implements WebhookAttemptRepository {

    private final WebhookAttemptMapper mapper;

    /** 按序号升序读取投递的全部尝试。 */
    @Override
    public List<WebhookAttempt> findDelivery(long deliveryId) {
        return mapper.findDelivery(deliveryId);
    }

    /** 读取投递中仍为 PROCESSING 的尝试，缺失返回空。 */
    @Override
    public WebhookAttempt findProcessing(long deliveryId) {
        return mapper.findProcessing(deliveryId);
    }

    /** 插入新尝试。 */
    @Override
    public void insert(WebhookAttempt item) {
        if (mapper.insert(item) != 1) {
            throw new ErrorException("Webhook 投递尝试插入失败");
        }
    }

    /** 只结束 PROCESSING 尝试；成功后同步原对象。 */
    @Override
    public void finish(WebhookAttempt item, WebhookAttemptStatus status, Integer httpStatus,
        WebhookFailureCode failureCode, Integer durationMs, Instant now) {
        if (mapper.finish(item, status, httpStatus, failureCode, durationMs, now) != 1) {
            throw new ConcurrentRequestException("Webhook 投递尝试已结束");
        }
        item.setStatus(status);
        item.setHttpStatus(httpStatus);
        item.setFailureCode(failureCode);
        item.setDurationMs(durationMs);
        item.setFinishedTime(now);
        item.setUpdateTime(now);
    }
}
