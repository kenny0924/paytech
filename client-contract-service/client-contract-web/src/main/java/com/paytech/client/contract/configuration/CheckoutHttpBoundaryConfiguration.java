package com.paytech.client.contract.configuration;

import java.util.Collections;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.paytech.foundation.common.error.FailException;
import static com.paytech.client.contract.api.enums.CheckoutErrorCode.*;

/** 已批准查询参数的封闭入口，不接受 caller 指定的路由或分类。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class CheckoutHttpBoundaryConfiguration implements WebMvcConfigurer {
    /** 完整解析仅接收 JSON Body，拒绝重复 Caller Header 和所有查询参数。 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            /** 在业务读取前校验唯一调用方标识及封闭参数范围。 */
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (Collections.list(request.getHeaders("X-Caller-Service")).size() != 1)
                    throw new FailException(CLIENT_NOT_FOUND);
                if (!request.getParameterMap().isEmpty()) {
                    throw new FailException(INVALID_CHECKOUT_QUERY);
                }
                return true;
            }
        }).addPathPatterns("/checkout-context/v1/resolutions");
    }
}
