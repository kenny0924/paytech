package com.paytech.platform.support.delivery.integration.provider;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationProvider;

import lombok.RequiredArgsConstructor;

/** 按服务商类型精确选择适配器；无适配器时失败关闭，不创建生产模拟实现。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.enabled", havingValue = "true")
public class EmailProviders {

    private final List<EmailProvider> providers;

    /** 账号类型必须恰有一个适配器，不能回退到任意可用适配器。 */
    public EmailProvider require(NotificationProvider account) {
        var matches = providers.stream().filter(p -> p.providerType().equals(account.getProviderType())).toList();
        if (matches.size() != 1) {
            throw new ServiceUnavailableException();
        }
        return matches.getFirst();
    }

    /** 本环境是否恰有一个该类型的适配器，供运营页面展示，不抛出不可用。 */
    public boolean isRegistered(String providerType) {
        return providers.stream().filter(p -> p.providerType().equals(providerType)).count() == 1;
    }
}
