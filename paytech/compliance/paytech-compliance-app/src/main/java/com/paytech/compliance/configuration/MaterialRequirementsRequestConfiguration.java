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

/** 私网材料查询只接受一个有界参数、不接受 Body，响应禁止缓存。 */
@Configuration(proxyBeanMethods = false)
public class MaterialRequirementsRequestConfiguration implements WebMvcConfigurer {
    /** 合同允许的 Query 参数。 */
    static final Set<String> PARAMETERS = Set.of("qualificationType");

    /** 仅绑定材料查询路径。 */
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
                        throw new InvalidParameterException("材料查询参数未知、重复或为空");
                    }
                }
                if (request.getInputStream().read() != -1) {
                    throw new InvalidParameterException("材料查询不接受 Body");
                }
                return true;
            }
        }).addPathPatterns("/internal/due-diligence/material-requirements");
    }
}
