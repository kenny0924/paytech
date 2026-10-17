package com.paytech.platform.support.service.processor.webhook;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.delivery.domain.ReplayWebhookDeliveryCommand;
import com.paytech.platform.support.delivery.service.WebhookManagementService;
import com.paytech.platform.support.service.processor.webhook.request.ReplayWebhookDeliveryRequest;

import lombok.AllArgsConstructor;

/**
 * 对已结束的投递生成新投递；Event ID 与 Body 不变，使用当前地址与密钥
 * <li>鉴权、新投递与补偿任务同一事务；提交后由补偿机制执行，操作由 Controller 审计</li>
 * <li>每次调用都生成一条新投递；Client 按 eventId 去重，重复 Replay 无业务副作用</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = {"operations.authentication.enabled", "webhook.enabled"}, havingValue = "true")
public class ReplayWebhookDeliveryProcessor extends AbstractProcessor<ReplayWebhookDeliveryRequest, Long> {

    private final WebhookManagementSupport support;
    private final WebhookManagementService management;

    @Override
    protected Long processRequest(ReplayWebhookDeliveryRequest request) {
        return support.replay(request.getActor(), request.getSession(), request.getReason(),
            (context, now) -> management.replay(new ReplayWebhookDeliveryCommand(request.getDeliveryId(),
                context.account().id(), now)), request.getDeliveryId());
    }
}
