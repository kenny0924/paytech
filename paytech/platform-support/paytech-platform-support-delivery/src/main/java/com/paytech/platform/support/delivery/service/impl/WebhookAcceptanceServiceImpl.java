package com.paytech.platform.support.delivery.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.platform.support.delivery.domain.AcceptWebhookEventContext;
import com.paytech.platform.support.delivery.domainservice.repository.WebhookDeliveryRepository;
import com.paytech.platform.support.delivery.domainservice.repository.WebhookEventRepository;
import com.paytech.platform.support.delivery.ext.eventhandler.WebhookDeliveryHandler;
import com.paytech.platform.support.delivery.service.WebhookAcceptanceService;

import lombok.RequiredArgsConstructor;

/** 受理事务只写本库并登记补偿任务；提交后由 Foundation 唤醒执行。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "webhook.enabled", havingValue = "true")
public class WebhookAcceptanceServiceImpl implements WebhookAcceptanceService {

    private final CompensationEventRegistrar events;
    private final PlatformTransactionManager transactionManager;
    private final WebhookEventRepository webhookEvents;
    private final WebhookDeliveryRepository deliveries;

    /** 唯一冲突整体回滚并原样上抛，不在异常路径伪造受理结果。 */
    @Override
    public void accept(AcceptWebhookEventContext context) {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(15);
        transaction.executeWithoutResult(status -> {
            webhookEvents.insert(context.getEvent());
            for (var delivery : context.getDeliveries()) {
                deliveries.insert(delivery);
                events.register(WebhookDeliveryHandler.buildEvent(delivery.getDeliveryId()));
            }
        });
    }
}
