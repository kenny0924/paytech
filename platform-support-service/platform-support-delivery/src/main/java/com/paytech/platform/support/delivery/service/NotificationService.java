package com.paytech.platform.support.delivery.service;

import java.time.Instant;
import com.paytech.platform.support.delivery.domain.*;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationAttempt;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderResult;

/** 纯本地通知事实和原子阶段；没有 Vault、Provider 或其他 Service 调用。 */
public interface NotificationService {

    /** 在本库一致性快照中恢复单个投递的权威事实。 */
    NotificationExecutionContext load(long deliveryId);

    /** 外发前原子插入尝试并增加发送次数；版本冲突不产生外部操作。 */
    void beginSend(NotificationExecutionContext context, NotificationAttempt attempt, Instant now);

    /** 查询接管时推进版本，PROCESSING 转 UNKNOWN；旧执行者不能再覆盖结果。 */
    void beginQuery(NotificationExecutionContext context, Instant now);

    /** 短事务保存经校验的外部证据及下一次发送时间。 */
    void complete(NotificationExecutionContext context, NotificationAttempt attempt, EmailProviderResult result,
        boolean query, Instant now);

    /** 配置或参数已经明确不允许新发送；没有新 Attempt。 */
    void failBeforeSend(NotificationExecutionContext context, Instant now);
}
