package com.paytech.client.console.bff.configuration;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.paytech.client.console.bff.ext.rest.ClientActorResolver;
import com.paytech.client.console.bff.ext.rest.ClientRequestInterceptor;

import lombok.AllArgsConstructor;

/** 认证入口统一应用同源与禁止缓存边界；Controller 的可信身份参数由入站解析器提供。 */
@Configuration(proxyBeanMethods = false)
@AllArgsConstructor
public class ClientWebConfiguration implements WebMvcConfigurer {
    private final ClientActorResolver actors;
    private final ClientRequestInterceptor interceptor;

    /** 注册认证入口的公共边界。 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/client-auth/**");
    }

    /** Controller 声明 ClientActor 参数时解析 Gateway 可信身份。 */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(actors);
    }
}
