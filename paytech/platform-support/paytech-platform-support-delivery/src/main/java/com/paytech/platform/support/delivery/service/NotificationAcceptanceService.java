package com.paytech.platform.support.delivery.service;

import com.paytech.platform.support.delivery.domain.SubmitNotificationContext;

/** 通知受理本地事务边界，与异步执行的依赖分离。 */
public interface NotificationAcceptanceService {

    /** 在一个事务保存请求、全部收件人和可靠执行任务。 */
    void accept(SubmitNotificationContext context);
}
