package com.paytech.risk.configuration;

import java.io.IOException;
import java.util.Set;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.paytech.foundation.common.error.InvalidParameterException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 评估查询协议严格读取：准确 Query、不接受重复或空参数、GET 不带 Body；统一评估的 POST 不受影响。 */
@Configuration(proxyBeanMethods = false)
public class RiskEvaluationQueryRequestConfiguration implements WebMvcConfigurer {

    /** 拒绝评估查询的未知、重复、空 Query 以及查询 Body。 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            /** 不合法时在进入业务入口前返回 400 INVALID_REQUEST。 */
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws IOException {
                if (!"GET".equals(request.getMethod())) {
                    return true;
                }
                Set<String> keys = keys(request.getRequestURI());
                for (var entry : request.getParameterMap().entrySet()) {
                    if (!keys.contains(entry.getKey()) || entry.getValue().length != 1 || entry.getValue()[0].isEmpty()) {
                        throw new InvalidParameterException("评估查询 Query 参数未知、重复或为空");
                    }
                }
                if (request.getInputStream().read() != -1) {
                    throw new InvalidParameterException("评估查询不接受 Body");
                }
                return true;
            }
        }).addPathPatterns("/risk-decisions/v1/**");
    }

    /** 只有按业务身份查询接受 pointCode、businessKey；按 ID 查询与解释不接受 Query。 */
    static Set<String> keys(String path) {
        if (path.endsWith("/evaluations")) {
            return Set.of("pointCode", "businessKey");
        }
        return Set.of();
    }
}
