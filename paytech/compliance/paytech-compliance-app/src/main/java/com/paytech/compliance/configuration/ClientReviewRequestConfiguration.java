package com.paytech.compliance.configuration;

import java.io.IOException;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.paytech.foundation.common.error.InvalidParameterException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Client 入驻审核（I01/I02 与运营 O01—O09）只接受合同内的 Query，查询不接受 Body。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "onboarding.review.enabled", havingValue = "true")
public class ClientReviewRequestConfiguration implements WebMvcConfigurer {
    /** 审核列表允许的 Query 参数。 */
    static final Set<String> LIST_PARAMETERS = Set.of("page", "size", "status", "qualificationType", "applicationId");

    /** 绑定审核登记与运营审核路径。 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            /** 拒绝未知、重复或空 Query 及 GET 携带 Body。 */
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws IOException {
                Set<String> allowed = parameters(request.getMethod(), request.getRequestURI());
                for (var item : request.getParameterMap().entrySet()) {
                    if (!allowed.contains(item.getKey()) || item.getValue().length != 1 || item.getValue()[0].isBlank()) {
                        throw new InvalidParameterException("审核 Query 参数未知、重复或为空");
                    }
                }
                if ("GET".equals(request.getMethod()) && request.getInputStream().read() != -1) {
                    throw new InvalidParameterException("审核查询不接受 Body");
                }
                return true;
            }
        }).addPathPatterns("/client-onboarding-review/v1/**", "/client-onboarding-admin/v1/**");
    }

    /** 只有 GET 审核列表接受 Query。 */
    static Set<String> parameters(String method, String path) {
        if ("GET".equals(method) && path.endsWith("/client-onboarding-admin/v1/cases")) {
            return LIST_PARAMETERS;
        }
        return Set.of();
    }
}
