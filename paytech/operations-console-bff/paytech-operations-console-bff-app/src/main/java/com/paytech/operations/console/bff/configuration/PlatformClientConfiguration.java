package com.paytech.operations.console.bff.configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Stream;

import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.foundation.web.request.RequestIdFilter;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.integration.PlatformDiscoveryInterceptor;
import com.paytech.platform.support.api.authentication.OperationsAuthenticationApi;
import com.paytech.platform.support.api.authorization.OperationsAuthorizationApi;
import com.paytech.platform.support.api.dataquery.DataQueryExecutionApi;
import com.paytech.platform.support.api.dataquery.DataSourceManagementApi;
import com.paytech.platform.support.api.dataquery.QueryTemplateManagementApi;
import com.paytech.platform.support.api.enums.DataQueryErrorCode;
import com.paytech.platform.support.api.enums.GatewayConfigurationErrorCode;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.api.enums.WebhookEndpointErrorCode;
import com.paytech.platform.support.api.filestorage.FileStorageManagementApi;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationManagementApi;
import com.paytech.platform.support.api.operations.OperationsManagementApi;
import com.paytech.platform.support.api.referencedata.CurrencySpecManagementApi;
import com.paytech.platform.support.api.referencedata.CurrencySpecQueryApi;
import com.paytech.platform.support.api.reviewfiles.ReviewFileApi;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Platform HTTP 接口代理的单例注册，以及发现、超时和关联标识配置。 @author Codex */
@Configuration(proxyBeanMethods = false)
public class PlatformClientConfiguration {
    private static final String PLATFORM_SERVICE = "platform-support-service";
    private static final String PLATFORM_BASE_URL = "http://" + PLATFORM_SERVICE + "/platform-support";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    /** 存储管理会在 Platform 事务外访问 Vault 与存储（桶检查依次多次调用），取 Gateway 10 秒响应上限内的 9 秒。 */
    private static final Duration FILE_STORAGE_READ_TIMEOUT = Duration.ofSeconds(9);
    private static final JsonMapper PROBLEM_JSON = JsonMapper.builder().build();

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

    /** 通知管理合同复用当前发现和 Problem 解码。 */
    @Bean
    com.paytech.platform.support.api.notificationmanagement.NotificationManagementApi notificationManagementApi(HttpServiceProxyFactory operationsConfigurationPlatformProxyFactory) {
        return operationsConfigurationPlatformProxyFactory.createClient(com.paytech.platform.support.api.notificationmanagement.NotificationManagementApi.class);
    }

    /** Webhook 管理额外登记端点地址错误码，页面按 Code 在地址字段下提示；其余沿用配置写操作的发现与 Problem 解码。 */
    @Bean
    com.paytech.platform.support.api.webhookmanagement.WebhookManagementApi webhookManagementApi(Environment environment,
        PlatformDiscoveryInterceptor discovery, @Value("${operations.platform-test-base-url:}") String testBaseUrl,
        ObjectProvider<InternalTokenSupplier> tokens) {
        return platformProxy(environment, discovery, testBaseUrl, tokens, true, WebhookEndpointErrorCode.values())
            .createClient(com.paytech.platform.support.api.webhookmanagement.WebhookManagementApi.class);
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

    /** 币种列表复用 Platform 私网发现与 Problem 解码；该查询不携带操作人。 */
    @Bean
    CurrencySpecQueryApi currencySpecQueryApi(HttpServiceProxyFactory operationsPlatformProxyFactory) {
        return operationsPlatformProxyFactory.createClient(CurrencySpecQueryApi.class);
    }

    /** 币种维护沿用配置写操作的发现与字段错误解码，并外显 Platform 业务拒绝原因（如币种已存在、已启用不可修改）。 */
    @Bean
    CurrencySpecManagementApi currencySpecManagementApi(Environment environment, PlatformDiscoveryInterceptor discovery,
        @Value("${operations.platform-test-base-url:}") String testBaseUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        return platformProxy(environment, discovery, testBaseUrl, tokens, true, true, READ_TIMEOUT)
            .createClient(CurrencySpecManagementApi.class);
    }

    /** 存储管理沿用配置写操作的发现与字段错误解码，外显 Platform 业务拒绝原因（如被写入路由引用不能停用），读取超时放宽到 9 秒。 */
    @Bean
    FileStorageManagementApi fileStorageManagementApi(Environment environment, PlatformDiscoveryInterceptor discovery,
        @Value("${operations.platform-test-base-url:}") String testBaseUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        return platformProxy(environment, discovery, testBaseUrl, tokens, true, true, FILE_STORAGE_READ_TIMEOUT)
            .createClient(FileStorageManagementApi.class);
    }

    /** 工作流动作目录：沿用配置写操作的发现与字段错误解码，并外显 Platform 业务拒绝原因（如编码已存在、版本冲突）。 */
    @Bean
    com.paytech.platform.support.api.workflow.WorkflowActionTemplateManagementApi workflowActionTemplateManagementApi(
        Environment environment, PlatformDiscoveryInterceptor discovery,
        @Value("${operations.platform-test-base-url:}") String testBaseUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        return platformProxy(environment, discovery, testBaseUrl, tokens, true, true, READ_TIMEOUT)
            .createClient(com.paytech.platform.support.api.workflow.WorkflowActionTemplateManagementApi.class);
    }

    /** 工作流流程定义：同上，发布校验不通过的原因外显。 */
    @Bean
    com.paytech.platform.support.api.workflow.WorkflowDefinitionManagementApi workflowDefinitionManagementApi(
        Environment environment, PlatformDiscoveryInterceptor discovery,
        @Value("${operations.platform-test-base-url:}") String testBaseUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        return platformProxy(environment, discovery, testBaseUrl, tokens, true, true, READ_TIMEOUT)
            .createClient(com.paytech.platform.support.api.workflow.WorkflowDefinitionManagementApi.class);
    }

    /** 工作流运行：同上，审批、提交与转交的业务拒绝原因外显。 */
    @Bean
    com.paytech.platform.support.api.workflow.WorkflowInstanceApi workflowInstanceApi(Environment environment,
        PlatformDiscoveryInterceptor discovery, @Value("${operations.platform-test-base-url:}") String testBaseUrl,
        ObjectProvider<InternalTokenSupplier> tokens) {
        return platformProxy(environment, discovery, testBaseUrl, tokens, true, true, READ_TIMEOUT)
            .createClient(com.paytech.platform.support.api.workflow.WorkflowInstanceApi.class);
    }

    /**
     * 工作流附件：上传开始需读取存储密钥并签发，完成需核对存储对象，沿用文件存储的读超时；业务拒绝原因外显
     * <li>额外登记 Platform 文件错误码（类型或大小不符、文件不存在、状态冲突），否则未登记的 Code 会被当作上游无效响应 502。</li>
     */
    @Bean
    com.paytech.platform.support.api.workflow.WorkflowAttachmentApi workflowAttachmentApi(Environment environment,
        PlatformDiscoveryInterceptor discovery, @Value("${operations.platform-test-base-url:}") String testBaseUrl,
        ObjectProvider<InternalTokenSupplier> tokens) {
        return platformProxy(environment, discovery, testBaseUrl, tokens, true, true, FILE_STORAGE_READ_TIMEOUT,
            OnboardingFileErrorCode.values())
            .createClient(com.paytech.platform.support.api.workflow.WorkflowAttachmentApi.class);
    }

    /** 数据源维护复用数据查询专用代理：测试连接与启用检查需要连接被查库。 */
    @Bean
    DataSourceManagementApi dataSourceManagementApi(HttpServiceProxyFactory operationsDataQueryPlatformProxyFactory) {
        return operationsDataQueryPlatformProxyFactory.createClient(DataSourceManagementApi.class);
    }

    /** 模板维护复用数据查询专用代理：保存与校验需要读取数据源，试运行执行查询。 */
    @Bean
    QueryTemplateManagementApi queryTemplateManagementApi(HttpServiceProxyFactory operationsDataQueryPlatformProxyFactory) {
        return operationsDataQueryPlatformProxyFactory.createClient(QueryTemplateManagementApi.class);
    }

    /** 模板执行复用数据查询专用代理。 */
    @Bean
    DataQueryExecutionApi dataQueryExecutionApi(HttpServiceProxyFactory operationsDataQueryPlatformProxyFactory) {
        return operationsDataQueryPlatformProxyFactory.createClient(DataQueryExecutionApi.class);
    }

    /**
     * 数据查询三个合同的共用代理
     * <li>登记 DataQueryErrorCode；开启字段错误与 Owner detail 外显，Platform 的业务拒绝原因（如启用检查未通过、有模板引用）原样到达页面。</li>
     * <li>读取超时取 operations.data-query-read-timeout（默认 9 秒，小于 Gateway 路由响应超时上限 10 秒），容纳连接检查与查询执行。</li>
     * <li>Platform 503 DATA_SOURCE_UNAVAILABLE 保留该 Code，页面据此提示稍后重试；其他 5xx 仍为 DEPENDENCY_UNAVAILABLE。</li>
     */
    @Bean
    HttpServiceProxyFactory operationsDataQueryPlatformProxyFactory(Environment environment,
        PlatformDiscoveryInterceptor discovery, @Value("${operations.platform-test-base-url:}") String testBaseUrl,
        @Value("${operations.data-query-read-timeout:9s}") Duration readTimeout,
        ObjectProvider<InternalTokenSupplier> tokens) {
        HttpServiceClientOptions options = InternalCallTokenOptions.apply(
            HttpServiceClientOptions.migrationWithLegacyErrorResponse(), tokens, PLATFORM_SERVICE)
            .withDependencyFailureMapper((kind, cause) -> switch (kind) {
                case TIMEOUT -> new ErrorException(OperationsBffErrorCode.DEPENDENCY_TIMEOUT, null, cause);
                case UNAVAILABLE -> new ServiceUnavailableException(dataSourceUnavailable(cause)
                    ? DataQueryErrorCode.DATA_SOURCE_UNAVAILABLE : OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE, cause);
            }).withRequestValidationFailures().withOwnerProblemDetail();
        return HttpServiceClientFactory.create(platformClient(environment, discovery, testBaseUrl, readTimeout), options,
            platformErrors(DataQueryErrorCode.values()));
    }

    /** Foundation 已校验为合规 Problem 的 503，且 Code 为 DATA_SOURCE_UNAVAILABLE。 */
    private static boolean dataSourceUnavailable(Throwable cause) {
        if (!(cause instanceof RestClientResponseException response) || response.getStatusCode().value() != 503) {
            return false;
        }
        try {
            return DataQueryErrorCode.DATA_SOURCE_UNAVAILABLE.getCode().equals(
                PROBLEM_JSON.readTree(response.getResponseBodyAsString()).path("code").asString());
        } catch (JacksonException unreadable) {
            return false;
        }
    }

    @Bean
    GatewayConfigurationManagementApi gatewayConfigurationManagementApi(
        HttpServiceProxyFactory operationsConfigurationPlatformProxyFactory) {
        return operationsConfigurationPlatformProxyFactory.createClient(GatewayConfigurationManagementApi.class);
    }

    /** 审核材料下载额外登记 Platform 文件错误码，其余沿用 Platform 发现与 Problem 解码。 */
    @Bean
    ReviewFileApi reviewFileApi(Environment environment, PlatformDiscoveryInterceptor discovery,
        @Value("${operations.platform-test-base-url:}") String testBaseUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        return platformProxy(environment, discovery, testBaseUrl, tokens, false,
            OnboardingFileErrorCode.values()).createClient(ReviewFileApi.class);
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
        @Value("${operations.platform-test-base-url:}") String testBaseUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        return platformProxy(environment, discovery, testBaseUrl, tokens, false);
    }

    /** 配置写操作显式接入字段路径投影，不改变其他 Platform 调用的历史解码行为。 */
    @Bean
    HttpServiceProxyFactory operationsConfigurationPlatformProxyFactory(Environment environment, PlatformDiscoveryInterceptor discovery,
        @Value("${operations.platform-test-base-url:}") String testBaseUrl, ObjectProvider<InternalTokenSupplier> tokens) {
        return platformProxy(environment, discovery, testBaseUrl, tokens, true);
    }

    private HttpServiceProxyFactory platformProxy(Environment environment, PlatformDiscoveryInterceptor discovery,
        String testBaseUrl, ObjectProvider<InternalTokenSupplier> tokens, boolean configurationValidation,
        CodeMessageEnum... extraErrors) {
        return platformProxy(environment, discovery, testBaseUrl, tokens, configurationValidation, false, READ_TIMEOUT,
            extraErrors);
    }

    /** ownerProblemDetail 只对已承诺 detail 可安全外显的 Platform 合同开启。 */
    private HttpServiceProxyFactory platformProxy(Environment environment, PlatformDiscoveryInterceptor discovery,
        String testBaseUrl, ObjectProvider<InternalTokenSupplier> tokens, boolean configurationValidation,
        boolean ownerProblemDetail, Duration readTimeout, CodeMessageEnum... extraErrors) {
        RestClient client = platformClient(environment, discovery, testBaseUrl, readTimeout);
        CodeMessageEnum[] errors = platformErrors(extraErrors);
        HttpServiceClientOptions options = InternalCallTokenOptions.apply(
            HttpServiceClientOptions.migrationWithLegacyErrorResponse(), tokens, PLATFORM_SERVICE)
            .withDependencyFailureMapper((kind, cause) -> switch (kind) {
                case TIMEOUT -> new ErrorException(OperationsBffErrorCode.DEPENDENCY_TIMEOUT, null, cause);
                case UNAVAILABLE ->
                    new ServiceUnavailableException(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE, cause);
            });
        options = configurationValidation ? options.withRequestValidationFailures() : options;
        return HttpServiceClientFactory.create(client, ownerProblemDetail ? options.withOwnerProblemDetail() : options, errors);
    }

    /** Platform 私网发现或 auto 测试直连地址，带 Request ID 传播与给定读取超时。 */
    private RestClient platformClient(Environment environment, PlatformDiscoveryInterceptor discovery,
        String testBaseUrl, Duration readTimeout) {
        RestClient.Builder builder = RestClient.builder();
        if (testBaseUrl.isBlank()) {
            builder.baseUrl(PLATFORM_BASE_URL).requestInterceptor(discovery);
        } else {
            validateTestAddress(testBaseUrl, environment);
            builder.baseUrl(testBaseUrl);
        }
        return builder.requestFactory(requestFactory(readTimeout)).requestInterceptor(requestIdInterceptor()).build();
    }

    /** Platform 公共错误码之外再登记本合同的专有错误码。 */
    private static CodeMessageEnum[] platformErrors(CodeMessageEnum... extraErrors) {
        return Stream.concat(Arrays.stream(OperationsErrorCode.values()),
            Stream.concat(Arrays.stream(GatewayConfigurationErrorCode.values()), Arrays.stream(extraErrors)))
            .toArray(CodeMessageEnum[]::new);
    }

    private JdkClientHttpRequestFactory requestFactory(Duration readTimeout) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(readTimeout);
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
