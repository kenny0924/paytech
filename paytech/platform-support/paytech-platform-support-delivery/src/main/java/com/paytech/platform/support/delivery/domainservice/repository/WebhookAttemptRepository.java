package com.paytech.platform.support.delivery.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookAttempt;
import com.paytech.platform.support.delivery.core.enums.WebhookAttemptStatus;
import com.paytech.platform.support.delivery.core.enums.WebhookFailureCode;

/** WebhookAttempt 单表仓储。 */
public interface WebhookAttemptRepository {

    /** 按序号升序读取投递的全部尝试。 */
    List<WebhookAttempt> findDelivery(long deliveryId);

    /** 读取投递中仍为 PROCESSING 的尝试，缺失返回空。 */
    WebhookAttempt findProcessing(long deliveryId);

    /** 插入新尝试；同一序号重复由唯一键拒绝。 */
    void insert(WebhookAttempt item);

    /** 只结束 PROCESSING 尝试；成功后同步原对象，失败抛并发冲突。 */
    void finish(WebhookAttempt item, WebhookAttemptStatus status, Integer httpStatus, WebhookFailureCode failureCode,
        Integer durationMs, Instant now);
}
