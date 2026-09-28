package com.paytech.platform.support.delivery.integration.provider;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ServiceUnavailableException;

import lombok.RequiredArgsConstructor;

/** 精确账号绑定；无适配器时失败关闭，不创建生产模拟实现。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.enabled", havingValue = "true")
public class EmailProviders {

    private final List<EmailProvider> providers;

    /** 同一账号必须恰有一个适配器，不能回退到任意可用 Provider。 */
    public EmailProvider require(String providerCode) {
        var matches = providers.stream().filter(p -> providerCode.equals(p.providerCode())).toList();
        if (matches.size() != 1) {
            throw new ServiceUnavailableException();
        }
        return matches.getFirst();
    }
}
