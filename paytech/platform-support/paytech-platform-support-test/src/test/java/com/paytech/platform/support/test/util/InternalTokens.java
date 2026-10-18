package com.paytech.platform.support.test.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.internalauth.api.CallerIdentity;
import com.paytech.foundation.internalauth.core.key.JwksInternalTokenKeySource;
import com.paytech.foundation.internalauth.core.token.InternalTokenVerifierImpl;
import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.platform.support.api.internalauth.InternalTokenIssueApi;
import com.paytech.platform.support.api.internalauth.response.InternalTokenIssueResponseBody;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * 内部调用 Token 的测试调用与验签：经真实签发接口申请，按调用方视角用 Foundation 校验器与本服务 JWKS 验证。
 * <li>无可变状态，不需要清理；签发产生的审计由全局清理回收。</li>
 */
@TestComponent
@RequiredArgsConstructor
public class InternalTokens {

    /** 签发接口路径（不含 context path）。 */
    public static final String ISSUE_PATH = "/platform/v1/internal-tokens";

    /** JWKS 接口路径（不含 context path）。 */
    public static final String JWKS_PATH = "/platform/v1/internal-auth/jwks";

    /** Mock 配置中的实例 ID。 */
    public static final String INSTANCE_ID = "main";

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final HttpTestInvoker httpTestInvoker;

    /**
     * 以调用方身份请求签发；caller 为 null 时不带 X-Caller-Service。
     *
     * @param caller 调用方服务名
     * @param body 请求体
     * @param requestId 请求 ID
     * @return 响应检查器
     */
    public PlatformResponseChecker request(String caller, Object body, String requestId) {
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        if (caller != null) {
            headers.set(InternalTokenIssueApi.CALLER_SERVICE_HEADER, caller);
        }
        return new PlatformResponseChecker(httpTestInvoker.exchange(HttpMethod.POST, ISSUE_PATH, headers, body),
            objectMapper, requestId);
    }

    /**
     * 为调用方申请访问目标服务的 GENERAL Token，断言签发成功。
     *
     * @param caller 调用方服务名
     * @param target 目标服务名
     * @return JWS Compact Token
     */
    public String issue(String caller, String target) {
        var response = request(caller, Map.of("targetService", target, "purpose", "GENERAL"),
            "plt-itk-" + UUID.randomUUID()).assertOk().as(InternalTokenIssueResponseBody.class);
        assertNotNull(response.getToken());
        return response.getToken();
    }

    /**
     * 以目标服务视角校验 Token：公钥取自本服务 JWKS 接口，aud 为目标服务，inst 为实例 ID。
     *
     * @param token JWS Compact Token
     * @param audience 目标服务名
     * @return 已验证身份
     */
    public CallerIdentity verifyAs(String token, String audience) {
        var keys = new JwksInternalTokenKeySource(jwksUri(), Duration.ofMinutes(5), Duration.ofSeconds(2),
            Duration.ofSeconds(10), Clock.systemUTC());
        return new InternalTokenVerifierImpl(keys, audience, INSTANCE_ID, Clock.systemUTC()).verify(token);
    }

    /** 本服务 JWKS 的完整地址（含随机端口与 context path）。 */
    public URI jwksUri() {
        return URI.create("http://127.0.0.1:" + environment.getRequiredProperty("local.server.port")
            + environment.getRequiredProperty("server.servlet.context-path") + JWKS_PATH);
    }
}
