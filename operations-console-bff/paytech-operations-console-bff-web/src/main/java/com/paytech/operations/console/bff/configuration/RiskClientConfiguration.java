package com.paytech.operations.console.bff.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.foundation.web.request.RequestIdFilter;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.integration.RiskDiscoveryInterceptor;
import com.paytech.risk.api.decision.DecisionReleaseManagementApi;
import com.paytech.risk.api.decision.DecisionRuleSetManagementApi;
import com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode;

/** 独立的 Risk 发现与严格协议工厂，复用 Foundation 错误语义。 */
@Configuration(proxyBeanMethods = false)
public class RiskClientConfiguration {
    private static final String RISK_BASE_URL = "http://risk-service";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(4);

    /** 复用统一发现、超时和错误解码配置注册字典 HTTP 代理，不添加重试。 */
    @Bean
    com.paytech.risk.api.decision.DecisionDictionaryApi decisionDictionaryApi(HttpServiceProxyFactory operationsRiskProxyFactory) {
        return operationsRiskProxyFactory.createClient(com.paytech.risk.api.decision.DecisionDictionaryApi.class);
    }

    /** 注册历史与发布引用的轻量 HTTP 代理，沿用现有 Risk 连接边界。 */
    @Bean
    com.paytech.risk.api.decision.DecisionConfigurationQueryApi decisionConfigurationQueryApi(HttpServiceProxyFactory operationsRiskProxyFactory) {
        return operationsRiskProxyFactory.createClient(com.paytech.risk.api.decision.DecisionConfigurationQueryApi.class);
    }

    @Bean
    com.paytech.risk.api.decision.DecisionFlowQueryApi decisionFlowQueryApi(HttpServiceProxyFactory operationsRiskProxyFactory) {
        return operationsRiskProxyFactory.createClient(com.paytech.risk.api.decision.DecisionFlowQueryApi.class);
    }

    @Bean
    com.paytech.risk.api.decision.DecisionFlowManagementApi decisionFlowManagementApi(
        @org.springframework.beans.factory.annotation.Qualifier("operationsRiskFlowProxyFactory") HttpServiceProxyFactory operationsRiskProxyFactory) {
        return operationsRiskProxyFactory.createClient(com.paytech.risk.api.decision.DecisionFlowManagementApi.class);
    }

    /** 变量写入的字段错误沿用 Foundation 已批准的校验错误投影。 */
    @Bean
    com.paytech.risk.api.decision.RiskVariableApi riskVariableApi(
        @org.springframework.beans.factory.annotation.Qualifier("operationsRiskFlowProxyFactory") HttpServiceProxyFactory factory) {
        return factory.createClient(com.paytech.risk.api.decision.RiskVariableApi.class);
    }

    /** 名单维护；写入字段错误沿用页面 Body 路径投影。 */
    @Bean
    com.paytech.risk.api.control.RiskListManagementApi riskListManagementApi(
        @org.springframework.beans.factory.annotation.Qualifier("operationsRiskFlowProxyFactory") HttpServiceProxyFactory factory) {
        return factory.createClient(com.paytech.risk.api.control.RiskListManagementApi.class);
    }

    /** 执行发布；写入字段错误沿用页面 Body 路径投影。 */
    @Bean
    com.paytech.risk.api.decision.ExecutionReleaseManagementApi executionReleaseManagementApi(
        @org.springframework.beans.factory.annotation.Qualifier("operationsRiskFlowProxyFactory") HttpServiceProxyFactory factory) {
        return factory.createClient(com.paytech.risk.api.decision.ExecutionReleaseManagementApi.class);
    }

    @Bean
    DecisionRuleSetManagementApi decisionRuleSetManagementApi(HttpServiceProxyFactory operationsRiskProxyFactory) {
        return operationsRiskProxyFactory.createClient(DecisionRuleSetManagementApi.class);
    }

    @Bean
    DecisionReleaseManagementApi decisionReleaseManagementApi(HttpServiceProxyFactory operationsRiskProxyFactory) {
        return operationsRiskProxyFactory.createClient(DecisionReleaseManagementApi.class);
    }

    @Bean
    HttpServiceProxyFactory operationsRiskProxyFactory(Environment environment, RiskDiscoveryInterceptor discovery,
        @Value("${operations.risk-test-base-url:}") String testBaseUrl) {
        return riskProxyFactory(environment, discovery, testBaseUrl, HttpServiceClientOptions.strict());
    }

    /** 只有本期流程写接口接入字段校验错误，其他 Risk 代理保持原分类。 */
    @Bean
    HttpServiceProxyFactory operationsRiskFlowProxyFactory(Environment environment, RiskDiscoveryInterceptor discovery,
        @Value("${operations.risk-test-base-url:}") String testBaseUrl) {
        return riskProxyFactory(environment, discovery, testBaseUrl,
            HttpServiceClientOptions.strict().withRequestValidationFailures());
    }

    /** 两组代理复用相同的发现、超时、禁止重定向及成功响应门禁。 */
    private HttpServiceProxyFactory riskProxyFactory(Environment environment, RiskDiscoveryInterceptor discovery,
        String testBaseUrl, HttpServiceClientOptions mode) {
        RestClient.Builder builder = RestClient.builder();
        if (testBaseUrl.isBlank()) {
            builder.baseUrl(RISK_BASE_URL).requestInterceptor(discovery);
        } else {
            validateTestAddress(testBaseUrl, environment);
            builder.baseUrl(testBaseUrl);
        }
        RestClient client = builder.requestFactory(requestFactory()).requestInterceptor(requestIdInterceptor())
            .requestInterceptor(responseReadInterceptor())
            .configureMessageConverters(converters -> {
                // 只调整此 Risk Client：默认 Jackson 仍写请求，但不能绕过成功 Reader 的类型/媒体校验。
                converters.disableDefaults()
                    .addCustomConverter(new RiskOwnerResponseHttpMessageConverter())
                    .addCustomConverter(new RequestJsonWriter());
            }).build();
        HttpServiceClientOptions options = mode
            .withDependencyFailureMapper((kind, cause) -> switch (kind) {
                case TIMEOUT -> new ErrorException(OperationsBffErrorCode.DEPENDENCY_TIMEOUT, null, cause);
                case UNAVAILABLE -> new ServiceUnavailableException(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE, cause);
            });
        return HttpServiceClientFactory.create(client, options, java.util.stream.Stream.concat(
            Arrays.stream(RiskDecisionConfigurationErrorCode.values()),
            Arrays.stream(com.paytech.risk.api.enums.RiskVariableErrorCode.values()))
            .toArray(com.paytech.foundation.common.enums.CodeMessageEnum[]::new));
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        // Socket 读取超时保留类型；Spring 7 的 JDK 流关闭定时器会丢失 Headers 后的超时原因。
        var factory = new NonRedirectingRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    private ClientHttpRequestInterceptor requestIdInterceptor() {
        return (request, body, execution) -> {
            String requestId = MDC.get(RequestIdFilter.MDC_KEY);
            if (requestId != null) request.getHeaders().set(RequestIdFilter.HEADER_NAME, requestId);
            return execution.execute(request, body);
        };
    }

    private ClientHttpRequestInterceptor responseReadInterceptor() {
        return (request, body, execution) -> {
            var response = execution.execute(request, body);
            PushbackInputStream stream = null;
            try {
                // 状态和首字节在 Converter 前才被 Spring 读取；提前归入传输边界并原样归还字节。
                response.getStatusCode();
                stream = new PushbackInputStream(response.getBody(), 1);
                int first = stream.read();
                if (first >= 0) stream.unread(first);
                return new PrefetchedResponse(response, stream);
            } catch (IOException failure) {
                if (stream != null) {
                    try { stream.close(); }
                    catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
                }
                response.close();
                throw new ResourceAccessException("Risk 响应读取失败", failure);
            }
        };
    }

    private void validateTestAddress(String value, Environment environment) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException invalidAddress) {
            throw new IllegalArgumentException("Risk 测试直连地址格式无效");
        }
        if (!Arrays.asList(environment.getActiveProfiles()).contains("auto")
            || !"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost())
            || uri.getPort() < 1 || uri.getPort() > 65535 || !uri.getRawPath().isEmpty()
            || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Risk 测试直连只允许 auto Profile 下的 loopback HTTP 地址");
        }
    }

    private static final class NonRedirectingRequestFactory extends SimpleClientHttpRequestFactory {
        @Override
        protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
            super.prepareConnection(connection, method);
            connection.setInstanceFollowRedirects(false);
        }
    }

    private record PrefetchedResponse(ClientHttpResponse response, InputStream body) implements ClientHttpResponse {
        @Override public HttpStatusCode getStatusCode() throws IOException { return response.getStatusCode(); }
        @Override public String getStatusText() throws IOException { return response.getStatusText(); }
        @Override public HttpHeaders getHeaders() { return response.getHeaders(); }
        @Override public InputStream getBody() { return body; }
        @Override public void close() {
            try { body.close(); }
            catch (IOException ignored) {
                // close 与 Spring Response 一致采用尽力释放；仍必须执行底层连接清理。
            } finally { response.close(); }
        }
    }

    private static final class RequestJsonWriter extends JacksonJsonHttpMessageConverter {
        @Override
        public boolean canRead(ResolvableType type, MediaType mediaType) {
            return false;
        }

        @Override
        public boolean canRead(Class<?> type, MediaType mediaType) {
            return false;
        }
    }
}
