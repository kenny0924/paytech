package com.paytech.platform.support.delivery.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationProvider;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationMedium;
import com.paytech.platform.support.delivery.domain.NotificationRouting;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationProviderRepository;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationRouteRepository;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationTemplateRepository;
import com.paytech.platform.support.delivery.integration.provider.EmailProviders;
import com.paytech.platform.support.delivery.service.NotificationConfigurationService;

import lombok.RequiredArgsConstructor;

/** 普通通知受理与即时发送共用的配置检查；缺少真实或测试适配器时不假装可发送。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.enabled", havingValue = "true")
public class NotificationConfigurationServiceImpl implements NotificationConfigurationService {

    private final EmailProviders adapters;
    private final NotificationRouteRepository routes;
    private final NotificationProviderRepository providers;
    private final NotificationTemplateRepository templates;

    /** 主备账号都必须属于 EMAIL。 */
    @Override
    public NotificationRouting resolve(String templateCode) {
        var template = templates.findCode(templateCode);
        if (template == null || template.getMedium() != NotificationMedium.EMAIL
            || template.getStatus() != NotificationConfigurationStatus.ACTIVE) {
            throw new BizCheckFailException("普通邮件模板不存在或未启用");
        }
        var route = routes.findTemplate(template.getTemplateId());
        if (route == null || route.getStatus() != NotificationConfigurationStatus.ACTIVE) {
            throw new BizCheckFailException("邮件路由未启用");
        }
        var primary = requireProvider(route.getPrimaryProviderId());
        if (route.getFallbackProviderId() != null)
            requireProvider(route.getFallbackProviderId());
        return new NotificationRouting(template, route, primary);
    }

    /** 校验实际发送账号和唯一适配器绑定。 */
    NotificationProvider requireProvider(long providerId) {
        var provider = providers.find(providerId);
        if (provider == null || provider.getMedium() != NotificationMedium.EMAIL
            || provider.getStatus() != NotificationConfigurationStatus.ACTIVE) {
            throw new BizCheckFailException("邮件发送账号未启用");
        }
        adapters.require(provider.getProviderCode());
        return provider;
    }
}
