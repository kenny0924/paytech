package com.paytech.platform.support.delivery.service;

import java.time.Instant;

import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookAttempt;
import com.paytech.platform.support.delivery.domain.WebhookAttemptOutcome;
import com.paytech.platform.support.delivery.domain.WebhookDeliveryContext;

/** 投递执行的本地读写；每个方法一个短事务，版本 CAS 保护并发执行者。 */
public interface WebhookDeliveryService {

    /** 读取一次执行需要的投递、事件、端点、密钥与遗留尝试。 */
    WebhookDeliveryContext load(long deliveryId);

    /** 端点停用或不再订阅：未完成的投递记 CANCELLED。 */
    void cancel(WebhookDeliveryContext context, Instant now);

    /**
     * 上次执行在发送中断：遗留尝试记 FAILED/INTERRUPTED，投递转为 RETRY_WAIT，次数用尽记 DEAD
     *
     * @param maximumAttempts 投递最大尝试次数
     */
    void interrupt(WebhookDeliveryContext context, int maximumAttempts, Instant now);

    /** 登记新尝试：投递记 PROCESSING 并递增次数，同一事务插入 PROCESSING 尝试。 */
    void begin(WebhookDeliveryContext context, WebhookAttempt attempt, Instant now);

    /**
     * 写入发送结果：2xx 记 SUCCEEDED，其余次数未满记 RETRY_WAIT、用尽记 DEAD
     *
     * @param maximumAttempts 投递最大尝试次数
     */
    void complete(WebhookDeliveryContext context, WebhookAttempt attempt, WebhookAttemptOutcome outcome,
        int maximumAttempts, Instant now);
}
