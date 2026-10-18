package com.paytech.platform.support.delivery.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookDelivery;
import com.paytech.platform.support.delivery.core.enums.WebhookDeliveryStatus;
import com.paytech.platform.support.delivery.domain.WebhookDeliveryFilter;
import com.paytech.platform.support.delivery.domain.WebhookDeliverySummaryView;

/** WebhookDelivery 仓储；列表组合所属事件的公开 eventId。 */
public interface WebhookDeliveryRepository {

    /** 读取投递，缺失返回空。 */
    WebhookDelivery find(long id);

    /** 统计事件的投递数量。 */
    int countEvent(long eventId);

    /** 插入新投递。 */
    void insert(WebhookDelivery item);

    /** 按旧版本与旧状态推进投递；成功后同步原对象，失败抛并发冲突。 */
    void transition(WebhookDelivery item, WebhookDeliveryStatus status, int attemptCount, Instant lastAttemptTime,
        Instant now);

    /** 按创建时间倒序分页。 */
    List<WebhookDeliverySummaryView> page(WebhookDeliveryFilter filter, long offset, int size);

    /** 与分页相同条件的总数。 */
    long count(WebhookDeliveryFilter filter);
}
