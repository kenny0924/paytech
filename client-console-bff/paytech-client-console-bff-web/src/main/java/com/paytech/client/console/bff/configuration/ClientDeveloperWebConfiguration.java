package com.paytech.client.console.bff.configuration;

import com.paytech.client.console.bff.ext.rest.ClientRequestInterceptor;
import com.paytech.client.console.bff.service.processor.ClientPermissionProcessor;
import com.paytech.client.console.bff.service.processor.ClientSessionProcessor;
import com.paytech.foundation.common.error.InvalidParameterException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 开发者接口边界：Origin / 防缓存 → 可信身份 → 租户上下文与权限 → 路径 ID、Query 与空 Body
 * <li>读操作要求 READ，写操作要求 MANAGE；凭证与 Webhook 分别判断。</li>
 * <li>所有响应 no-store（一次性密钥只出现在创建与轮换响应中）。</li>
 */
@Configuration(proxyBeanMethods = false)
@lombok.AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientDeveloperWebConfiguration implements WebMvcConfigurer {

    /** 允许的 Query 参数；其余路径不接受 Query。 */
    private static final Map<String, Set<String>> QUERIES = Map.of(
        "/client-developer/v1/credentials", Set.of("status"),
        "/client-developer/v1/webhook-deliveries", Set.of("endpointId", "eventType", "status", "from", "to", "page", "size"));

    private final ClientRequestInterceptor common;
    private final ClientSessionProcessor sessions;
    private final ClientPermissionProcessor permissions;

    /** 注册开发者接口拦截器。 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            /** 权限在协议检查之前判断，无权限的请求不暴露参数校验细节。 */
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
                if (!(handler instanceof HandlerMethod)) {
                    return true;
                }
                common.preHandle(request, response, handler);
                sessions.identity(request);
                permissions.require(request, permission(request));
                checkPathIds(request);
                checkQuery(request);
                checkEmptyBody(request);
                return true;
            }
        }).addPathPatterns("/client-developer/**");
    }

    /** 凭证与 Scope 归 API 凭证权限，其余归 Webhook；GET 为 READ，其余为 MANAGE。 */
    static String permission(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean credential = path.startsWith("/client-developer/v1/credentials") || path.startsWith("/client-developer/v1/scopes");
        boolean read = "GET".equals(request.getMethod());
        return (credential ? "CLIENT_API_CREDENTIAL_" : "CLIENT_WEBHOOK_") + (read ? "READ" : "MANAGE");
    }

    /** 路径 ID 必须是规范正整数。 */
    static void checkPathIds(HttpServletRequest request) {
        var variables = (Map<?, ?>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables == null) {
            return;
        }
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

    /** 只接受本路径允许的单值 Query 参数。 */
    static void checkQuery(HttpServletRequest request) {
        var allowed = QUERIES.getOrDefault(request.getRequestURI(), Set.of());
        for (var entry : request.getParameterMap().entrySet()) {
            if (!allowed.contains(entry.getKey()) || entry.getValue().length != 1) {
                throw new InvalidParameterException("查询参数无效");
            }
        }
    }

    /** GET、测试事件与重发不接受 Body。 */
    static void checkEmptyBody(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI();
        boolean empty = "GET".equals(request.getMethod()) || path.endsWith("/test-events") || path.endsWith("/replay");
        if (empty && request.getInputStream().read() != -1) {
            throw new InvalidParameterException("当前操作不接受 Body");
        }
    }
}
