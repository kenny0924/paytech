package com.paytech.foundation.web.internalauth;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.paytech.foundation.internalauth.core.inbound.InternalCallerAuthenticator;
import com.paytech.foundation.web.autoconfigure.WebAutoConfiguration;
import com.paytech.foundation.web.error.ProblemResponseWriter;

/**
 * 宿主引入 {@code paytech-internal-auth} 时的 Servlet 接入：参数解析始终可用，过滤器只在判定器存在（mode 非 OFF）时注册。
 *
 * @author Claude
 */
@AutoConfiguration(after = WebAutoConfiguration.class,
    afterName = "com.paytech.foundation.internalauth.core.autoconfigure.InternalAuthAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(InternalCallerAuthenticator.class)
public class InternalAuthWebAutoConfiguration {

    /**
     * 入站校验过滤器。
     *
     * @param authenticator 入站判定器
     * @param problemWriter 共享 Problem 写出器
     * @return 过滤器
     */
    @Bean
    @ConditionalOnBean(InternalCallerAuthenticator.class)
    @ConditionalOnMissingBean
    public InternalAuthFilter internalAuthFilter(InternalCallerAuthenticator authenticator,
        ProblemResponseWriter problemWriter) {
        return new InternalAuthFilter(authenticator, problemWriter);
    }

    /**
     * 注册 {@code @CallerIdentityArgument} 参数解析。
     *
     * @return MVC 配置
     */
    @Bean
    public WebMvcConfigurer callerIdentityArgumentConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CallerIdentityArgumentResolver());
            }
        };
    }
}
