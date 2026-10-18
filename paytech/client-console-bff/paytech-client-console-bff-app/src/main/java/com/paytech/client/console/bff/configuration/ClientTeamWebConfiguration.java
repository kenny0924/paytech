package com.paytech.client.console.bff.configuration;

import com.paytech.client.console.bff.ext.rest.ClientActorResolver;
import com.paytech.client.console.bff.ext.rest.ClientPermissionGuard;
import com.paytech.client.console.bff.ext.rest.ClientRequestInterceptor;
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
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 团队接口边界：Origin / 防缓存 → 可信身份 → 租户上下文与权限 → 路径 ID、Query 与空 Body
 * <li>GET 成员列表要求 CLIENT_TEAM_READ；邀请列表与全部写操作要求 CLIENT_TEAM_MANAGE。</li>
 * <li>权限在协议检查之前判断，无权限的请求不暴露参数校验细节；最终授权仍由 Platform 在每个接口重判。</li>
 */
@Configuration(proxyBeanMethods = false)
@lombok.AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientTeamWebConfiguration implements WebMvcConfigurer {

    static final String TEAM_READ = "CLIENT_TEAM_READ";

    static final String TEAM_MANAGE = "CLIENT_TEAM_MANAGE";

    private static final String MEMBERS = "/client-team/v1/members";

    /** GET 允许的 Query 参数；其余请求不接受 Query。 */
    private static final Map<String, Set<String>> QUERIES = Map.of(
        MEMBERS, Set.of("page", "size"),
        "/client-team/v1/invitations", Set.of("status", "page", "size"));

    private final ClientRequestInterceptor common;
    private final ClientActorResolver actors;
    private final ClientPermissionGuard permissions;

    /** 注册团队接口拦截器。 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            /** 依次执行公共边界、身份、租户与权限、协议检查。 */
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
                if (!(handler instanceof HandlerMethod)) {
                    return true;
                }
                common.preHandle(request, response, handler);
                permissions.require(actors.resolve(request), permission(request));
                ClientDeveloperWebConfiguration.checkPathIds(request);
                checkQuery(request);
                checkEmptyBody(request);
                return true;
            }
        }).addPathPatterns("/client-team/**");
    }

    /** 只有 GET 成员列表是 READ，其余（含邀请列表）是 MANAGE。 */
    static String permission(HttpServletRequest request) {
        boolean memberList = "GET".equals(request.getMethod()) && MEMBERS.equals(request.getRequestURI());
        return memberList ? TEAM_READ : TEAM_MANAGE;
    }

    /** 只接受 GET 列表允许的单值 Query 参数。 */
    static void checkQuery(HttpServletRequest request) {
        var allowed = "GET".equals(request.getMethod()) ? QUERIES.getOrDefault(request.getRequestURI(), Set.of()) : Set.<String>of();
        for (var entry : request.getParameterMap().entrySet()) {
            if (!allowed.contains(entry.getKey()) || entry.getValue().length != 1) {
                throw new InvalidParameterException("查询参数无效");
            }
        }
    }

    /** GET、重发、撤销与移除不接受 Body。 */
    static void checkEmptyBody(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI();
        boolean empty = "GET".equals(request.getMethod()) || path.endsWith("/resend") || path.endsWith("/revocation")
            || path.endsWith("/removal");
        if (empty && request.getInputStream().read() != -1) {
            throw new InvalidParameterException("当前操作不接受 Body");
        }
    }
}
