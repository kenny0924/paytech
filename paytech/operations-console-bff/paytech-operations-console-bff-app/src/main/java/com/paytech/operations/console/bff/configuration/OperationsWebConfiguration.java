package com.paytech.operations.console.bff.configuration;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.paytech.operations.console.bff.ext.rest.OperationsRequestInterceptor;
import com.paytech.operations.console.bff.ext.rest.OperatorContextArgumentResolver;

/** 注册入站协议防护和可信身份转换，启动时校验 HTTP test 的环境约束。 @author Codex */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OperationsBrowserProperties.class)
public class OperationsWebConfiguration implements WebMvcConfigurer {
    private final OperationsRequestInterceptor requestInterceptor;
    private final OperatorContextArgumentResolver contextResolver;

    public OperationsWebConfiguration(Environment environment, OperationsBrowserProperties properties,
        OperationsRequestInterceptor requestInterceptor, OperatorContextArgumentResolver contextResolver) {
        properties.validateProfiles(environment.getActiveProfiles());
        this.requestInterceptor = requestInterceptor;
        this.contextResolver = contextResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestInterceptor).addPathPatterns("/**")
            .excludePathPatterns("/actuator/**", "/error");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(contextResolver);
    }
}
