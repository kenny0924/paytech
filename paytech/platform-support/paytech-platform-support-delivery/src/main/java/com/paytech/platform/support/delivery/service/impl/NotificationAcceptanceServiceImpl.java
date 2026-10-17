package com.paytech.platform.support.delivery.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.platform.support.delivery.domain.SubmitNotificationContext;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationDeliveryRepository;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationRequestRepository;
import com.paytech.platform.support.delivery.ext.eventhandler.NotificationDeliveryHandler;
import com.paytech.platform.support.delivery.service.NotificationAcceptanceService;

import lombok.RequiredArgsConstructor;

/** 注册任务只属于受理事务；Handler 的本地执行 Service 不反向依赖 Registrar。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.enabled", havingValue = "true")
public class NotificationAcceptanceServiceImpl implements NotificationAcceptanceService {

    private final CompensationEventRegistrar events;
    private final PlatformTransactionManager transactionManager;
    private final NotificationRequestRepository requests;
    private final NotificationDeliveryRepository deliveries;

    /** 唯一冲突整体回滚，不通过异常路径返回伪造受理结果。 */
    @Override
    public void accept(SubmitNotificationContext context) {
        try {
            transaction().executeWithoutResult(tx -> {
                requests.insert(context.getRequest());
                for (var delivery : context.getDeliveries()) {
                    deliveries.insert(delivery);
                    events.register(NotificationDeliveryHandler.buildEvent(delivery.getDeliveryId()));
                }
            });
        } catch (DuplicateKeyException conflict) {
            throw new ConcurrentRequestException("通知业务身份或收件人已存在", conflict);
        }
    }

    /** 显式读写短事务，不修改宿主只读模板。 */
    TransactionTemplate transaction() {
        var tx = new TransactionTemplate(transactionManager);
        tx.setTimeout(15);
        return tx;
    }
}
