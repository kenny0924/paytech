package com.paytech.operations.console.bff.configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Stream;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.foundation.web.request.RequestIdFilter;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.integration.PlatformDiscoveryInterceptor;
import com.paytech.platform.support.api.authentication.OperationsAuthenticationApi;
import com.paytech.platform.support.api.authorization.OperationsAuthorizationApi;
import com.paytech.platform.support.api.enums.GatewayConfigurationErrorCode;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationManagementApi;
import com.paytech.platform.support.api.operations.OperationsManagementApi;

/** Platform HTTP 接口代理的单例注册，以及发现、超时和关联标识配置。 @author Codex */
@Configuration(proxyBeanMethods = false)
public class PlatformClientConfiguration {
    private static final String PLATFORM_BASE_URL = "http://platform-support-service/platform-support";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    /** 租户访问复用既有 Platform 私网发现及字段错误解码。 */
    @Bean
    com.paytech.platform.support.api.tenantaccessmanagement.TenantAccessManagementApi tenantAccessManagementApi(HttpServiceProxyFactory operationsConfigurationPlatformProxyFactory) {
        return operationsConfigurationPlatformProxyFactory.createClient(com.paytech.platform.support.api.tenantaccessmanagement.TenantAccessManagementApi.class);
    }

    /** 管理合同复用当前发现和 Problem 解码。 */
    @Bean
    com.paytech.platform.support.api.apicredentialmanagement.ApiCredentialManagementApi apiCredentialManagementApi(HttpServiceProxyFactory operationsConfigurationPlatformProxyFactory) {
        return operationsConfigurationPlatformProxyFactory.createClient(com.paytech.platform.support.api.apicredentialmanagement.ApiCredentialManagementApi.class);
    }

    /** 管理合同复用当前发现和 Problem 解码。 */
    @Bean
    com.paytech.platform.support.api.apiscopemanagement.ApiScopeManagementApi apiScopeManagementApi(HttpServiceProxyFactory operationsConfigurationPlatformProxyFactory) {
        return operationsConfigurationPlatformProxyFactory.createClient(com.paytech.platform.support.api.apiscopemanagement.ApiScopeManagementApi.class);
    }

    /** 复用现有 Platform HTTP 发现与 Problem 解码边界。 */
    @Bean
    com.paytech.platform.support.api.consolemanagement.ConsoleResourceManagementApi consoleResourceManagementApi(HttpServiceProxyFactory operationsConfigurationPlatformProxyFactory) {
        return operationsConfigurationPlatformProxyFactory.createClient(com.paytech.platform.support.api.consolemanagement.ConsoleResourceManagementApi.class);
    }

    /** 复用现有 Platform HTTP 发现与 Problem 解码边界。 */
    @Bean
    com.paytech.platform.support.api.permissionmanagement.PermissionManagementApi permissionManagementApi(HttpServiceProxyFactory operationsConfigurationPlatformProxyFactory) {
        return operationsConfigurationPlatformProxyFactory.createClient(com.paytech.platform.support.api.permissionmanagement.PermissionManagementApi.class);
    }

    /** 外部 API 配置管理复用 Platform 私网发现与字段错误解码。 */
    @Bean
    com.paytech.platform.support.api.externalapi.StandardApiManagementApi standardApiManagementApi(HttpServiceProxyFactory operationsConfigurationPlatformProxyFactory) {
        return operationsConfigurationPlatformProxyFactory.createClient(com.paytech.platform.support.api.externalapi.StandardApiManagementApi.class);
    }

    /** 外部 API 配置管理复用 Platform 私网发现与字段错误解码。 */
    @Bean
    com.paytech.platform.support.api.externalapi.IntegrationManagementApi integrationManagementApi(HttpServiceProxyFactory operationsConfigurationPlatformProxyFactory) {
        return operationsConfigurationPlatformProxyFactory.createClient(com.paytech.platform.support.api.externalapi.IntegrationManagementApi.class);
    }

    /** 外部 API 配置管理复用 Platform 私网发现与字段错误解码。 */
    @Bean
    com.paytech.platform.support.api.externalapi.IntegrationApiManagementApi integrationApiManagementApi(HttpServiceProxyFactory operationsConfigurationPlatformProxyFactory) {
        return operationsConfigurationPlatformProxyFactory.createClient(com.paytech.platform.support.api.externalapi.IntegrationApiManagementApi.class);
    }

    /** 外部 API 配置管理复用 Platform 私网发现与字段错误解码。 */
    @Bean
    com.paytech.platform.support.api.externalapi.IntegrationScriptManagementApi integrationScriptManagementApi(HttpServiceProxyFactory operationsConfigurationPlatformProxyFactory) {
        return operationsConfigurationPlatformProxyFactory.createClient(com.paytech.platform.support.api.externalapi.IntegrationScriptManagementApi.class);
    }

    /** 外部 API 配置管理复用 Platform 私网发现与字段错误解码。 */
    @Bean
    com.paytech.platform.support.api.externalapi.IntegrationImplementationManagementApi integrationImplementationManagementApi(HttpServiceProxyFactory operationsConfigurationPlatformProxyFactory) {
        return operationsConfigurationPlatformProxyFactory.createClient(com.paytech.platform.support.api.externalapi.IntegrationImplementationManagementApi.class);
    }

    /** 外部 API 配置管理复用 Platform 私网发现与字段错误解码。 */
    @Bean
    com.paytech.platform.support.api.externalapi.ExternalApiEditorApi externalApiEditorApi(HttpServiceProxyFactory operationsConfigurationPlatformProxyFactory) {
        return operationsConfigurationPlatformProxyFactory.createClient(com.paytech.platform.support.api.externalapi.ExternalApiEditorApi.class);
    }

    @Bean
    GatewayConfigurationManagementApi gatewayConfigurationManagementApi(
        HttpServiceProxyFactory operationsConfigurationPlatformProxyFactory) {
        return operationsConfigurationPlatformProxyFactory.createClient(GatewayConfigurationManagementApi.class);
    }

    @Bean
    OperationsManagementApi operationsManagementApi(HttpServiceProxyFactory operationsPlatformProxyFactory) {
        return operationsPlatformProxyFactory.createClient(OperationsManagementApi.class);
    }

    @Bean
    OperationsAuthorizationApi operationsAuthorizationApi(HttpServiceProxyFactory operationsPlatformProxyFactory) {
        return operationsPlatformProxyFactory.createClient(OperationsAuthorizationApi.class);
    }

    @Bean
    OperationsAuthenticationApi operationsAuthenticationApi(HttpServiceProxyFactory operationsPlatformProxyFactory) {
        return operationsPlatformProxyFactory.createClient(OperationsAuthenticationApi.class);
    }

    @Bean
    HttpServiceProxyFactory operationsPlatformProxyFactory(Environment environment, PlatformDiscoveryInterceptor discovery,
        @Value("${operations.platform-test-base-url:}") String testBaseUrl) {
        return platformProxy(environment, discovery, testBaseUrl, false);
    }

    /** 配置写操作显式接入字段路径投影，不改变其他 Platform 调用的历史解码行为。 */
    @Bean
    HttpServiceProxyFactory operationsConfigurationPlatformProxyFactory(Environment environment, PlatformDiscoveryInterceptor discovery,
        @Value("${operations.platform-test-base-url:}") String testBaseUrl) {
        return platformProxy(environment, discovery, testBaseUrl, true);
    }

    private HttpServiceProxyFactory platformProxy(Environment environment, PlatformDiscoveryInterceptor discovery,
        String testBaseUrl, boolean configurationValidation) {
        RestClient.Builder builder = RestClient.builder();
        if (testBaseUrl.isBlank()) {
            builder.baseUrl(PLATFORM_BASE_URL).requestInterceptor(discovery);
        } else {
            validateTestAddress(testBaseUrl, environment);
            builder.baseUrl(testBaseUrl);
        }
        RestClient client = builder.requestFactory(requestFactory())
            .requestInterceptor(requestIdInterceptor()).build();
        CodeMessageEnum[] errors = Stream.concat(Arrays.stream(OperationsErrorCode.values()),
            Arrays.stream(GatewayConfigurationErrorCode.values())).toArray(CodeMessageEnum[]::new);
        HttpServiceClientOptions options = HttpServiceClientOptions.migrationWithLegacyErrorResponse()
            .withDependencyFailureMapper((kind, cause) -> switch (kind) {
                case TIMEOUT -> new ErrorException(OperationsBffErrorCode.DEPENDENCY_TIMEOUT, null, cause);
                case UNAVAILABLE ->
                    new ServiceUnavailableException(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE, cause);
            });
        return HttpServiceClientFactory.create(client, configurationValidation ? options.withRequestValidationFailures() : options, errors);
    }

    private JdkClientHttpRequestFactory requestFactory() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    private ClientHttpRequestInterceptor requestIdInterceptor() {
        return (request, body, execution) -> {
            String requestId = MDC.get(RequestIdFilter.MDC_KEY);
            if (requestId != null) {
                request.getHeaders().set(RequestIdFilter.HEADER_NAME, requestId);
            }
            return execution.execute(request, body);
        };
    }

    private void validateTestAddress(String value, Environment environment) {
        URI uri = URI.create(value);
        if (!Arrays.asList(environment.getActiveProfiles()).contains("auto")
            || !"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost()) || uri.getPort() < 1
            || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Platform 测试直连只允许 auto Profile 下的 loopback HTTP 地址");
        }
    }
}
