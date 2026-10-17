package com.paytech.client.console.bff.configuration;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import com.paytech.foundation.common.error.*;
import com.paytech.foundation.httpclient.api.*;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.client.console.bff.domain.enums.ClientBffErrorCode;
import com.paytech.client.console.bff.integration.PlatformDiscoveryInterceptor;
import com.paytech.platform.support.api.authentication.ClientAuthenticationApi;
import com.paytech.platform.support.api.authorization.ClientAuthorizationApi;
import com.paytech.platform.support.api.clientapicredential.ClientApiCredentialApi;
import com.paytech.platform.support.api.clientinvitation.ClientInvitationApi;
import com.paytech.platform.support.api.clientteam.ClientTeamApi;
import com.paytech.platform.support.api.clientwebhook.ClientWebhookApi;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import com.paytech.platform.support.api.enums.ClientTeamErrorCode;
import com.paytech.foundation.common.enums.CodeMessageEnum;
import java.util.stream.Stream;

/** 复用 Platform 私网发现与公共严格 Problem 解码；连接不持有业务状态。 */
@Configuration(proxyBeanMethods=false)
public class PlatformClientConfiguration {
    private static final String PLATFORM_SERVICE = "platform-support-service";

    /** 只在测试侧显式指定 loopback 协议替身；正常服务由 Nacos 发现。 */
    @Bean ClientAuthenticationApi clientAuthenticationApi(Environment env, PlatformDiscoveryInterceptor discovery,
        @Value("${client.platform-test-base-url:}") String testUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        return create(env, discovery, testUrl, tokens, ClientAuthenticationApi.class);
    }

    /** 工作区成员权限判断（PLT-I02 CLIENT）。 */
    @Bean ClientAuthorizationApi clientAuthorizationApi(Environment env, PlatformDiscoveryInterceptor discovery,
        @Value("${client.platform-test-base-url:}") String testUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        return create(env, discovery, testUrl, tokens, ClientAuthorizationApi.class);
    }

    /** 工作区 API 凭证自助。 */
    @Bean ClientApiCredentialApi clientApiCredentialApi(Environment env, PlatformDiscoveryInterceptor discovery,
        @Value("${client.platform-test-base-url:}") String testUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        return create(env, discovery, testUrl, tokens, ClientApiCredentialApi.class);
    }

    /** 工作区 Webhook 自助。 */
    @Bean ClientWebhookApi clientWebhookApi(Environment env, PlatformDiscoveryInterceptor discovery,
        @Value("${client.platform-test-base-url:}") String testUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        return create(env, discovery, testUrl, tokens, ClientWebhookApi.class);
    }

    /** 工作区团队成员与邀请管理。 */
    @Bean ClientTeamApi clientTeamApi(Environment env, PlatformDiscoveryInterceptor discovery,
        @Value("${client.platform-test-base-url:}") String testUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        return create(env, discovery, testUrl, tokens, ClientTeamApi.class, teamErrors());
    }

    /** 被邀请人预览、详情与接受邀请。 */
    @Bean ClientInvitationApi clientInvitationApi(Environment env, PlatformDiscoveryInterceptor discovery,
        @Value("${client.platform-test-base-url:}") String testUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        return create(env, discovery, testUrl, tokens, ClientInvitationApi.class, teamErrors());
    }

    /** 团队与邀请接口同时使用认证类与团队类错误码（会话、权限、工作区不可用沿用认证类）。 */
    static CodeMessageEnum[] teamErrors() {
        return Stream.concat(Stream.of(ClientAuthenticationErrorCode.values()), Stream.of(ClientTeamErrorCode.values()))
            .toArray(CodeMessageEnum[]::new);
    }

    /** 只注册认证类错误码的 Platform 接口代理。 */
    static <T> T create(Environment env, PlatformDiscoveryInterceptor discovery, String testUrl,
        ObjectProvider<InternalTokenSupplier> tokens, Class<T> type) {
        return create(env, discovery, testUrl, tokens, type, ClientAuthenticationErrorCode.values());
    }

    /** 同一套发现、超时与严格 Problem 解码创建 Platform 接口代理；contractErrors 为该接口发布的业务错误码。 */
    static <T> T create(Environment env, PlatformDiscoveryInterceptor discovery, String testUrl,
        ObjectProvider<InternalTokenSupplier> tokens, Class<T> type, CodeMessageEnum... contractErrors) {
        RestClient.Builder builder=RestClient.builder();
        if(testUrl.isBlank()) {
            builder.baseUrl("http://" + PLATFORM_SERVICE + "/platform-support").requestInterceptor(discovery);
        } else {
            URI uri=URI.create(testUrl);
            if(!Arrays.asList(env.getActiveProfiles()).contains("auto") || !"http".equals(uri.getScheme())
                || !"127.0.0.1".equals(uri.getHost()) || uri.getPort()<1 || uri.getUserInfo()!=null
                || uri.getQuery()!=null || uri.getFragment()!=null) {
                throw new IllegalArgumentException("Platform 测试直连仅允许 auto 的 loopback HTTP");
            }
            builder.baseUrl(testUrl);
        }
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        var options=InternalCallTokenOptions.apply(HttpServiceClientOptions.strict().withRequestValidationFailures(),
            tokens, PLATFORM_SERVICE)
            .withDependencyFailureMapper((kind,cause)->switch(kind) {
                case TIMEOUT -> new ErrorException(ClientBffErrorCode.DEPENDENCY_TIMEOUT,null,cause);
                case UNAVAILABLE -> new ServiceUnavailableException(ClientBffErrorCode.DEPENDENCY_UNAVAILABLE,cause);
            });
        return HttpServiceClientFactory.create(builder.requestFactory(factory).build(),options,
            contractErrors).createClient(type);
    }
}
