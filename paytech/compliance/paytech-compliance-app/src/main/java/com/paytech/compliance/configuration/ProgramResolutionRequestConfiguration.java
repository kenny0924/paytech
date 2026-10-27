package com.paytech.compliance.configuration;

import java.io.IOException;
import java.util.Set;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.paytech.foundation.common.error.InvalidParameterException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** CP-1 程序解析只接受合同内的有界 Query、不接受 Body，响应禁止缓存（程序版本发布后立即生效）。 */
@Configuration(proxyBeanMethods = false)
public class ProgramResolutionRequestConfiguration implements WebMvcConfigurer {
    /** 合同允许的 Query 参数。 */
    static final Set<String> PARAMETERS = Set.of("jurisdiction", "partyType", "roleType", "tier", "responsiblePartnerId");

    /** 仅绑定程序解析路径。 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            /** 拒绝未知、重复或空参数及任何 Body。 */
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws IOException {
                response.setHeader("Cache-Control", "no-store");
                for (var item : request.getParameterMap().entrySet()) {
                    if (!PARAMETERS.contains(item.getKey()) || item.getValue().length != 1 || item.getValue()[0].isBlank()) {
                        throw new InvalidParameterException("程序解析参数未知、重复或为空");
                    }
                }
                if (request.getInputStream().read() != -1) {
                    throw new InvalidParameterException("程序解析不接受 Body");
                }
                return true;
            }
        }).addPathPatterns("/compliance/v1/programs/resolve");
    }
}
