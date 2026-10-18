package com.paytech.gateway.configuration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.paytech.foundation.internalauth.api.InternalAuthMode;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.gateway.security.GatewayInternalTokenPolicy;

/** 装配出站内部 Token 策略；Token 来源由 Foundation internal-auth 自动配置在模式非 OFF 时提供。 */
@Configuration(proxyBeanMethods = false)
public class GatewayInternalTokenConfiguration {

    /**
     * 按 {@code internal-auth.mode}（默认 OFF）创建出站 Token 策略。
     *
     * @param environment 环境
     * @param supplier Foundation Token 来源；OFF 时不存在
     * @return 出站 Token 策略
     */
    @Bean
    GatewayInternalTokenPolicy gatewayInternalTokenPolicy(Environment environment,
        ObjectProvider<InternalTokenSupplier> supplier) {
        InternalAuthMode mode = Binder.get(environment).bind("internal-auth.mode", InternalAuthMode.class)
            .orElse(InternalAuthMode.OFF);
        return new GatewayInternalTokenPolicy(mode, supplier.getIfAvailable());
    }
}
