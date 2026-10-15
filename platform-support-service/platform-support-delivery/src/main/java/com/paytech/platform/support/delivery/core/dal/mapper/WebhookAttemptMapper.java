package com.paytech.platform.support.delivery.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookAttempt;
import com.paytech.platform.support.delivery.core.enums.WebhookAttemptStatus;
import com.paytech.platform.support.delivery.core.enums.WebhookFailureCode;

/** Webhook 投递尝试单表 SQL 绑定。 */
public interface WebhookAttemptMapper {

    /** 按序号升序读取投递的全部尝试。 */
    List<WebhookAttempt> findDelivery(@Param("deliveryId") long deliveryId);

    /** 读取投递中仍为 PROCESSING 的尝试，缺失返回空。 */
    WebhookAttempt findProcessing(@Param("deliveryId") long deliveryId);

    /** 插入新尝试。 */
    int insert(WebhookAttempt item);

    /** 只结束 PROCESSING 尝试。 */
    int finish(@Param("item") WebhookAttempt item, @Param("status") WebhookAttemptStatus status,
        @Param("httpStatus") Integer httpStatus, @Param("failureCode") WebhookFailureCode failureCode,
        @Param("durationMs") Integer durationMs, @Param("now") Instant now);
}
