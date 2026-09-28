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
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationDelivery;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationRequest;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationRoute;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationTemplate;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationDeliveryStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationMedium;
import com.paytech.platform.support.delivery.domain.NotificationInput;
import com.paytech.platform.support.delivery.domain.SubmitNotificationContext;
import com.paytech.platform.support.delivery.domainservice.repository.*;
import com.paytech.platform.support.delivery.integration.provider.EmailProviders;
import com.paytech.platform.support.delivery.integration.vault.NotificationAddressVault;
import com.paytech.platform.support.delivery.service.NotificationAcceptanceService;
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
    private final EmailProviders adapters;
    private final NotificationAcceptanceService service;
    private final NotificationAddressVault vault;
    private final NotificationRouteRepository routes;
    private final NotificationRequestRepository requests;
    private final NotificationProviderRepository providers;
    private final NotificationTemplateRepository templates;
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
            if (!addresses.add(NotificationInput.canonicalEmail(recipient.getEmail()))) {
                throw new InvalidParameterException("同一通知中不允许重复邮箱");
            }
            if (recipient.getLocale() != null)
                NotificationInput.locale(recipient.getLocale());
            NotificationInput.parameters(recipient.getParameters());
        }
    }

    /** 先检查全部输入与配置，事务外保护地址，再一次性保存与登记任务。 */
    @Override
    protected SubmitNotificationResponse processRequest(SubmitNotificationRequest request) {
        if (requests.findSource(request.getSourceSystem(), request.getSourceEventKey()) != null) {
            throw new ConcurrentRequestException("该来源通知意图已经受理");
        }
        var template = templates.findCode(request.getTemplateCode());
        var route = validateConfiguration(template);
        var context = buildContext(request, template, route);
        service.accept(context);
        return new SubmitNotificationResponse(context.getRequest().getRequestId(), context.getDeliveries().size());
    }

    /** 主备账号都必须属于 EMAIL；缺少真实或测试适配器时不假装受理可执行通知。 */
    NotificationRoute validateConfiguration(NotificationTemplate template) {
        if (template == null || template.getMedium() != NotificationMedium.EMAIL
            || template.getStatus() != NotificationConfigurationStatus.ACTIVE) {
            throw new BizCheckFailException("普通邮件模板不存在或未启用");
        }
        var route = routes.findTemplate(template.getTemplateId());
        if (route == null || route.getStatus() != NotificationConfigurationStatus.ACTIVE) {
            throw new BizCheckFailException("邮件路由未启用");
        }
        validateProvider(route.getPrimaryProviderId());
        if (route.getFallbackProviderId() != null)
            validateProvider(route.getFallbackProviderId());
        return route;
    }

    /** 校验实际发送账号和唯一适配器绑定。 */
    void validateProvider(long providerId) {
        var provider = providers.find(providerId);
        if (provider == null || provider.getMedium() != NotificationMedium.EMAIL
            || provider.getStatus() != NotificationConfigurationStatus.ACTIVE) {
            throw new BizCheckFailException("邮件发送账号未启用");
        }
        adapters.require(provider.getProviderCode());
    }

    /** 检查每个收件人的当前语言及必要参数，任何一项失败都不会调用 Vault。 */
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
            String email = NotificationInput.canonicalEmail(input.getRecipients().get(index).getEmail());
            deliveries.get(index).setAddressVaultToken(vault.protect(email));
        }
        return new SubmitNotificationContext(request, java.util.List.copyOf(deliveries));
    }
}
