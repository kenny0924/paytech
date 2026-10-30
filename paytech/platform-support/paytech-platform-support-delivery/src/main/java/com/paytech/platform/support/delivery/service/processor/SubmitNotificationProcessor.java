package com.paytech.platform.support.delivery.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.delivery.core.configuration.NotificationProperties;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationDelivery;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationRequest;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationRoute;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationTemplate;
import com.paytech.platform.support.delivery.core.enums.NotificationDeliveryStatus;
import com.paytech.platform.support.delivery.domain.NotificationInput;
import com.paytech.platform.support.delivery.domain.SubmitNotificationContext;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationRequestRepository;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationTemplateContentRepository;
import com.paytech.platform.support.delivery.integration.vault.NotificationAddressVault;
import com.paytech.platform.support.delivery.service.NotificationAcceptanceService;
import com.paytech.platform.support.delivery.service.NotificationConfigurationService;
import com.paytech.platform.support.delivery.service.processor.request.SubmitNotificationRequest;
import com.paytech.platform.support.delivery.service.processor.response.SubmitNotificationResponse;

import lombok.RequiredArgsConstructor;

/** 模块公开入口：普通通知原子受理；不接收 HTTP Header、外部身份或认证秘密。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.enabled", havingValue = "true")
public class SubmitNotificationProcessor
    extends AbstractProcessor<SubmitNotificationRequest, SubmitNotificationResponse> {

    private final Clock clock;
    private final IdGenerator ids;
    private final NotificationProperties properties;
    private final NotificationAcceptanceService service;
    private final NotificationConfigurationService configuration;
    private final NotificationAddressVault vault;
    private final NotificationRequestRepository requests;
    private final NotificationTemplateContentRepository contents;

    /** 公共父类完成基础字段校验；这里只补跨字段及规范化重复地址检查。 */
    @Override
    protected void validate(SubmitNotificationRequest request) {
        if (!request.getSourceEventKey().equals(request.getSourceEventKey().strip())
            || request.getSourceEventKey().codePoints().anyMatch(Character::isISOControl)) {
            throw new InvalidParameterException("通知来源业务标识不合法");
        }
        NotificationInput.locale(request.getDefaultLocale());
        NotificationInput.parameters(request.getDefaultParameters());
        var addresses = new HashSet<String>();
        for (var recipient : request.getRecipients()) {
            if (!addresses.add(addressKey(recipient))) {
                throw new InvalidParameterException("同一通知中不允许重复邮箱");
            }
            if (recipient.getLocale() != null)
                NotificationInput.locale(recipient.getLocale());
            NotificationInput.parameters(recipient.getParameters());
        }
    }

    /**
     * 收件人去重键：邮箱规范化值，或已保护 Token
     * <li>两者恰好提供一个；同一请求混用两种来源时不能跨来源判重，由提供 Token 的入口保证不混用。</li>
     */
    static String addressKey(SubmitNotificationRequest.Recipient recipient) {
        boolean hasEmail = recipient.getEmail() != null;
        boolean hasToken = recipient.getAddressVaultToken() != null;
        if (hasEmail == hasToken) {
            throw new InvalidParameterException("收件人必须且只能提供邮箱或已保护邮箱 Token 之一");
        }
        if (hasToken) {
            if (recipient.getAddressVaultToken().isBlank()) {
                throw new InvalidParameterException("收件人邮箱 Token 不能为空");
            }
            return "token:" + recipient.getAddressVaultToken();
        }
        if (recipient.getEmail().isBlank()) {
            throw new InvalidParameterException("收件邮箱不能为空");
        }
        return "email:" + NotificationInput.canonicalEmail(recipient.getEmail());
    }

    /** 先检查全部输入与配置，事务外保护地址，再一次性保存与登记任务；即时发送模板不经此入口。 */
    @Override
    protected SubmitNotificationResponse processRequest(SubmitNotificationRequest request) {
        if (requests.findSource(request.getSourceSystem(), request.getSourceEventKey()) != null) {
            throw new ConcurrentRequestException("该来源通知意图已经受理");
        }
        if (properties.isTransientTemplate(request.getTemplateCode())) {
            throw new BizCheckFailException("该模板只用于即时发送");
        }
        var routing = configuration.resolve(request.getTemplateCode());
        var context = buildContext(request, routing.getTemplate(), routing.getRoute());
        service.accept(context);
        return new SubmitNotificationResponse(context.getRequest().getRequestId(), context.getDeliveries().size());
    }

    /** 检查每个收件人的当前语言及必要参数，任何一项失败都不会调用 Vault；已提供 Token 的收件人不再 Protect。 */
    SubmitNotificationContext buildContext(SubmitNotificationRequest input, NotificationTemplate template,
        NotificationRoute route) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var request = new NotificationRequest();
        request.setRequestId(ids.nextId());
        request.setTenantId(input.getTenantId());
        request.setSourceSystem(input.getSourceSystem());
        request.setSourceEventKey(input.getSourceEventKey());
        request.setNotificationTemplateId(template.getTemplateId());
        request.setNotificationRouteId(route.getRouteId());
        request.setDefaultLocale(NotificationInput.locale(input.getDefaultLocale()));
        request.setDefaultParameters(NotificationInput.parameters(input.getDefaultParameters()));
        request.setCreateTime(now);
        request.setUpdateTime(now);
        var deliveries = new ArrayList<NotificationDelivery>();
        for (var recipient : input.getRecipients()) {
            var delivery = new NotificationDelivery();
            delivery.setDeliveryId(ids.nextId());
            delivery.setRequestId(request.getRequestId());
            delivery.setLocale(recipient.getLocale() == null ? null : NotificationInput.locale(recipient.getLocale()));
            delivery.setParameters(NotificationInput.parameters(recipient.getParameters()));
            var content = contents.findLocale(template.getTemplateId(),
                delivery.getLocale() == null ? request.getDefaultLocale() : delivery.getLocale());
            if (content == null)
                throw new BizCheckFailException("邮件模板缺少所需语言");
            NotificationInput.render(content.getSubjectTemplate(), content.getBodyTemplate(),
                NotificationInput.merge(request.getDefaultParameters(), delivery.getParameters()));
            delivery.setStatus(NotificationDeliveryStatus.PENDING);
            delivery.setVersion(1);
            delivery.setCreateTime(now);
            delivery.setUpdateTime(now);
            deliveries.add(delivery);
        }
        for (int index = 0; index < deliveries.size(); index++) {
            var recipient = input.getRecipients().get(index);
            deliveries.get(index).setAddressVaultToken(recipient.getAddressVaultToken() != null
                ? recipient.getAddressVaultToken() : vault.protect(NotificationInput.canonicalEmail(recipient.getEmail())));
        }
        return new SubmitNotificationContext(request, java.util.List.copyOf(deliveries));
    }
}
