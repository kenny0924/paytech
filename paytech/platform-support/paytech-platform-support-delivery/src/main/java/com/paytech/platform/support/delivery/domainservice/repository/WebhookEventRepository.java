package com.paytech.platform.support.delivery.domainservice.repository;

import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookEvent;

/** WebhookEvent 单表仓储；事件受理后不可变。 */
public interface WebhookEventRepository {

    /** 读取事件，缺失返回空。 */
    WebhookEvent find(long id);

    /** 按来源身份读取已受理事件，缺失返回空。 */
    WebhookEvent findSource(long tenantId, String sourceEventKey, String sourceSystem);

    /** 插入新事件；来源身份唯一冲突使整个受理事务回滚。 */
    void insert(WebhookEvent item);
}
