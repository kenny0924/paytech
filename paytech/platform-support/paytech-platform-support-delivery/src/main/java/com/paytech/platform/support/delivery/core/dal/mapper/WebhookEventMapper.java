package com.paytech.platform.support.delivery.core.dal.mapper;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookEvent;

/** Webhook 事件单表 SQL 绑定。 */
public interface WebhookEventMapper {

    /** 读取事件，缺失返回空。 */
    WebhookEvent find(@Param("id") long id);

    /** 按来源身份读取已受理事件。 */
    WebhookEvent findSource(@Param("tenantId") long tenantId, @Param("sourceEventKey") String sourceEventKey,
        @Param("sourceSystem") String sourceSystem);

    /** 插入新事件；来源身份唯一冲突使整个受理事务回滚。 */
    int insert(WebhookEvent item);
}
