package com.paytech.client.console.bff.configuration;

import com.paytech.client.console.bff.domain.enums.ClientBffErrorCode;
import com.paytech.client.console.bff.ext.rest.ClientRequestInterceptor;
import com.paytech.client.console.bff.service.processor.ClientSessionProcessor;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 邀请接口边界：Origin / Content-Type / 防缓存 → 不接受 Query →（详情、接受）可信身份与账号上下文
 * <li>预览公开：不读取、不要求身份头，链接证明由 Platform 校验。</li>
 * <li>详情与接受只接受账号上下文；租户上下文在调用 Platform 前返回 403 CLIENT_ACCOUNT_CONTEXT_REQUIRED，
 * 页面先切回账号上下文（W01）再处理，避免以某个工作区成员会话加入另一个工作区。</li>
 */
@Configuration(proxyBeanMethods = false)
@lombok.AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientInvitationWebConfiguration implements WebMvcConfigurer {

    static final String PREVIEW = "/client-invitations/v1/preview";

    private final ClientRequestInterceptor common;
    private final ClientSessionProcessor sessions;

    /** 注册邀请接口拦截器。 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            /** 公开预览只做公共边界；其余先核对身份再要求账号上下文。 */
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (!(handler instanceof HandlerMethod)) {
                    return true;
                }
                common.preHandle(request, response, handler);
                if (!request.getParameterMap().isEmpty()) {
                    throw new InvalidParameterException("查询参数无效");
                }
                if (PREVIEW.equals(request.getRequestURI())) {
                    return true;
                }
                sessions.identity(request);
                requireAccountContext(request);
                return true;
            }
        }).addPathPatterns("/client-invitations/**");
    }

    /** 只接受 Gateway 重建的单值账号上下文。 */
    static void requireAccountContext(HttpServletRequest request) {
        var values = Collections.list(request.getHeaders("X-Gateway-Context-Type"));
        if (values.size() != 1 || !"ACCOUNT".equals(values.getFirst())) {
            throw new FailException(ClientBffErrorCode.ACCOUNT_CONTEXT_REQUIRED);
        }
    }
}
