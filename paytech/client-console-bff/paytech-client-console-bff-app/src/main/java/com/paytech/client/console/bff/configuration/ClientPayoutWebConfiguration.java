package com.paytech.client.console.bff.configuration;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.paytech.client.console.bff.ext.rest.ClientActorResolver;
import com.paytech.client.console.bff.ext.rest.ClientPermissionGuard;
import com.paytech.client.console.bff.ext.rest.ClientRequestInterceptor;
import com.paytech.foundation.common.error.InvalidParameterException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 资金接口边界：Origin / 防缓存 → 可信身份 → 租户上下文与权限 → 路径 ID、Query 白名单与空 Body
 * <li>余额要求 CLIENT_BALANCE_READ；收款账户与字段要求的 GET 要求 CLIENT_PAYOUT_ACCOUNT_READ；全部 POST 要求
 * CLIENT_PAYOUT_ACCOUNT_MANAGE。</li>
 * <li>权限在协议检查之前判断，无权限的请求不暴露参数校验细节；Query 白名单与 Gateway 路由一致。</li>
 *
 * @author Claude
 */
@Configuration(proxyBeanMethods = false)
@lombok.AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientPayoutWebConfiguration implements WebMvcConfigurer {

    static final String ACCOUNT_READ = "CLIENT_PAYOUT_ACCOUNT_READ";

    static final String ACCOUNT_MANAGE = "CLIENT_PAYOUT_ACCOUNT_MANAGE";

    static final String BALANCE_READ = "CLIENT_BALANCE_READ";

    private static final String ROOT = "/client-payout/v1";

    private static final String BALANCES = ROOT + "/balances";

    /** GET 允许的 Query 参数（与 Gateway 路由白名单一致）；其余请求不接受 Query。 */
    private static final Map<String, Set<String>> QUERIES = Map.of(
        ROOT + "/beneficiary-accounts", Set.of("status", "currency"),
        ROOT + "/beneficiary-requirements", Set.of("railType", "bankCountry", "currency"));

    /** 必填 Query 参数。 */
    private static final Map<String, Set<String>> REQUIRED_QUERIES = Map.of(
        ROOT + "/beneficiary-requirements", Set.of("railType", "bankCountry", "currency"));

    /** Query 参数值格式。 */
    private static final Map<String, String> QUERY_PATTERNS = Map.of(
        "status", "PENDING_REVIEW|VERIFIED|REJECTED|SUSPENDED|CLOSED", "currency", "[A-Z]{3}",
        "railType", "LOCAL|SWIFT", "bankCountry", "[A-Z]{2}");

    private final ClientRequestInterceptor common;
    private final ClientActorResolver actors;
    private final ClientPermissionGuard permissions;

    /** 注册资金接口拦截器。 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            /** 依次执行公共边界、身份、租户与权限、协议检查。 */
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws IOException {
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
        }).addPathPatterns("/client-payout/**");
    }

    /** 余额为 BALANCE_READ；其余 GET 为 ACCOUNT_READ；POST 为 ACCOUNT_MANAGE。 */
    static String permission(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod())) {
            return ACCOUNT_MANAGE;
        }
        return BALANCES.equals(request.getRequestURI()) ? BALANCE_READ : ACCOUNT_READ;
    }

    /** 只接受本路径允许的单值 Query 参数，取值符合格式，必填参数齐全。 */
    static void checkQuery(HttpServletRequest request) {
        var allowed = "GET".equals(request.getMethod()) ? QUERIES.getOrDefault(request.getRequestURI(), Set.of()) : Set.<String>of();
        for (var entry : request.getParameterMap().entrySet()) {
            if (!allowed.contains(entry.getKey()) || entry.getValue().length != 1
                || !entry.getValue()[0].matches(QUERY_PATTERNS.get(entry.getKey()))) {
                throw new InvalidParameterException("查询参数无效");
            }
        }
        for (String required : REQUIRED_QUERIES.getOrDefault(request.getRequestURI(), Set.of())) {
            if (request.getParameter(required) == null) {
                throw new InvalidParameterException("查询参数无效");
            }
        }
    }

    /** GET 不接受 Body。 */
    static void checkEmptyBody(HttpServletRequest request) throws IOException {
        if ("GET".equals(request.getMethod()) && request.getInputStream().read() != -1) {
            throw new InvalidParameterException("当前操作不接受 Body");
        }
    }
}
