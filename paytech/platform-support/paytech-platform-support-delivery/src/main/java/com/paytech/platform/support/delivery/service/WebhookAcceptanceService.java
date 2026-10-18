package com.paytech.platform.support.delivery.service;

import com.paytech.platform.support.delivery.domain.AcceptWebhookEventContext;

/** 事件受理的本地原子保存。 */
public interface WebhookAcceptanceService {

    /**
     * 同一事务保存事件、全部投递并登记每条投递的补偿任务
     * <li>来源身份唯一冲突使整个事务回滚，DuplicateKeyException 原样上抛，由调用方重读原事件比较</li>
     *
     * @param context 已校验的事件与投递
     */
    void accept(AcceptWebhookEventContext context);
}
