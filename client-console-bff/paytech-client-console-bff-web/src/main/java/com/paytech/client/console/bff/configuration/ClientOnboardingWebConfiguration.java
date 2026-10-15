package com.paytech.client.console.bff.configuration;

import com.paytech.client.console.bff.ext.rest.ClientRequestInterceptor;
import com.paytech.client.console.bff.service.processor.ClientSessionProcessor;
import com.paytech.foundation.common.error.InvalidParameterException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

/** 入驻入口与认证共用 Origin/防缓存边界，严格区分空 Body 命令。 */
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "client.onboarding-enabled",
    havingValue = "true"
)
@Configuration(proxyBeanMethods = false)
@lombok.AllArgsConstructor
public class ClientOnboardingWebConfiguration implements WebMvcConfigurer {

    private final ClientRequestInterceptor common;
    private final ClientSessionProcessor sessions;

    /** 先验证可信身份，再检查具体路径的协议；管理监听不会注册业务入口。 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry
            .addInterceptor(
                new HandlerInterceptor() {
                    /** 严格 Query、路径 ID 与幂等键，不能把重复 Header 传给 Owner。 */
                    @Override
                    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                        throws IOException {
                        if (!(handler instanceof org.springframework.web.method.HandlerMethod)) {
                            return true;
                        }
                        common.preHandle(request, response, handler);
                        sessions.identity(request);
                        var variables = (Map<?, ?>) request.getAttribute(
                            org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE
                        );
                        if (variables != null) {
                            for (var value : variables.values()) {
                                String id = String.valueOf(value);
                                try {
                                    if (!id.matches("[1-9][0-9]{0,18}")) {
                                        throw new NumberFormatException();
                                    }
                                    Long.parseLong(id);
                                } catch (NumberFormatException invalid) {
                                    throw new InvalidParameterException("资源身份格式无效");
                                }
                            }
                        }
                        boolean options = request.getRequestURI().endsWith("/form-options");
                        for (var entry : request.getParameterMap().entrySet()) {
                            if (
                                !options ||
                                !"clientType".equals(entry.getKey()) ||
                                entry.getValue().length != 1 ||
                                entry.getValue()[0].isBlank()
                            ) {
                                throw new InvalidParameterException("入驻查询参数无效");
                            }
                        }
                        String method = request.getMethod(),
                            path = request.getRequestURI();
                        boolean submission = path.endsWith("/submissions");
                        boolean empty =
                            Set.of("GET", "HEAD", "DELETE").contains(method) ||
                            path.endsWith("/completion") ||
                            path.endsWith("/download") ||
                            submission;
                        if (empty && request.getInputStream().read() != -1) {
                            throw new InvalidParameterException("当前操作不接受 Body");
                        }
                        return true;
                    }
                }
            )
            .addPathPatterns("/client-onboarding/**", "/onboarding-files/**");
    }
}
