package com.paytech.client.console.bff.configuration;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import com.paytech.client.console.bff.ext.rest.ClientRequestInterceptor;
/** 认证入口统一应用同源与禁止缓存边界。 */
@Configuration(proxyBeanMethods=false)
public class ClientWebConfiguration implements WebMvcConfigurer {
    private final ClientRequestInterceptor interceptor;
    public ClientWebConfiguration(ClientRequestInterceptor interceptor) { this.interceptor=interceptor; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/client-auth/**");
    }
}
