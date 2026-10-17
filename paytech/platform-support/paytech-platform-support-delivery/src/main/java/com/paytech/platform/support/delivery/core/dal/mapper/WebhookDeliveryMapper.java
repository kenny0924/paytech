package com.paytech.platform.support.delivery.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookDelivery;
import com.paytech.platform.support.delivery.core.enums.WebhookDeliveryStatus;
import com.paytech.platform.support.delivery.domain.WebhookDeliveryFilter;
import com.paytech.platform.support.delivery.domain.WebhookDeliverySummaryView;

/** Webhook 投递 SQL 绑定；列表与所属事件组合查询公开 eventId。 */
public interface WebhookDeliveryMapper {

    /** 读取投递，缺失返回空。 */
    WebhookDelivery find(@Param("id") long id);

    /** 统计事件的投递数量。 */
    int countEvent(@Param("eventId") long eventId);

    /** 插入新投递。 */
    int insert(WebhookDelivery item);

    /** 按旧版本与旧状态推进投递。 */
    int transition(@Param("item") WebhookDelivery item, @Param("status") WebhookDeliveryStatus status,
        @Param("attemptCount") int attemptCount, @Param("lastAttemptTime") Instant lastAttemptTime,
        @Param("now") Instant now);

    /** 按创建时间倒序分页。 */
    List<WebhookDeliverySummaryView> page(@Param("filter") WebhookDeliveryFilter filter, @Param("offset") long offset,
        @Param("size") int size);

    /** 与分页相同条件的总数。 */
    long count(@Param("filter") WebhookDeliveryFilter filter);
}
