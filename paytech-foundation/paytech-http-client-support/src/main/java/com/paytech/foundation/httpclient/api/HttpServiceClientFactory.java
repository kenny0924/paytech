package com.paytech.foundation.httpclient.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.httpclient.core.error.HttpServiceClientInterceptor;
import com.paytech.foundation.httpclient.core.transport.HttpServiceExchangeInterceptor;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * 为同步 typed HTTP 接口安装公共异常边界，不创建第二层代理或自动重试。
 * 自动传播 MDC Request ID；宿主负责发现、超时与禁止自动重定向，不另行安装状态处理器。
 * 非 void 接口必须返回非空值。
 * 业务 DTO 字段校验仍由宿主负责，不支持异步、流式或有意返回 null 的接口。
 *
 * @author Codex
 */
public final class HttpServiceClientFactory {

    private HttpServiceClientFactory() {}

    /**
     * 创建统一转换 HTTP、网络与解码失败的接口工厂。
     *
     * @param client 已配置传输策略和 JSON 转换器的客户端
     * @param contractErrors 调用方明确理解的特殊 4xx 错误码，仅公开其默认消息
     * @return 供宿主创建 typed API Bean 的 Spring 工厂
     */
    public static HttpServiceProxyFactory create(RestClient client, CodeMessageEnum... contractErrors) {
        return create(client, HttpServiceClientOptions.strict(), contractErrors);
    }

    /**
     * Creates a typed client with an explicitly selected error-wire compatibility mode.
     *
     * @param client configured transport client
     * @param options immutable decoding options
     * @param contractErrors registered safe 4xx business codes
     * @return typed HTTP service proxy factory
     */
    public static HttpServiceProxyFactory create(RestClient client, HttpServiceClientOptions options,
        CodeMessageEnum... contractErrors) {
        Objects.requireNonNull(client, "client must not be null");
        Objects.requireNonNull(options, "options must not be null");
        HttpServiceClientInterceptor interceptor = new HttpServiceClientInterceptor(contractErrors,
            options.legacyErrorResponseEnabled(), options.dependencyFailureMapper(), options.requestValidationFailuresEnabled());
        RestClient guardedClient = guard(client.mutate(), options).build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(guardedClient))
            .proxyFactoryCustomizer((factory, serviceType) -> factory.addAdvice(0, interceptor))
            .build();
    }

    /**
     * 为 Spring 自动注册的 HTTP Service Group 安装相同异常边界。
     * Spring Boot / Cloud 继续负责创建客户端、服务发现、负载均衡及传输配置。
     */
    public static RestClientHttpServiceGroupConfigurer configureGroup(String groupName, HttpServiceClientOptions options,
        CodeMessageEnum... contractErrors) {
        Objects.requireNonNull(groupName, "groupName must not be null");
        Objects.requireNonNull(options, "options must not be null");
        HttpServiceClientInterceptor interceptor = new HttpServiceClientInterceptor(contractErrors,
            options.legacyErrorResponseEnabled(), options.dependencyFailureMapper(), options.requestValidationFailuresEnabled());
        return groups -> groups.filterByName(groupName).forEachGroup((group, client, proxy) -> {
            guard(client, options);
            proxy.proxyFactoryCustomizer((factory, serviceType) -> factory.addAdvice(0, interceptor));
        });
    }

    private static RestClient.Builder guard(RestClient.Builder client, HttpServiceClientOptions options) {
        client.requestInterceptor(new HttpServiceExchangeInterceptor(options.strictJsonResponseBytes()));
        if (options.strictJsonResponseBytes() > 0) {
            client.configureMessageConverters(converters -> converters.disableDefaults()
                .addCustomConverter(strictJsonConverter()));
        }
        return client
            .defaultStatusHandler(status -> status.is3xxRedirection(), (request, response) -> {
                throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
            })
            .defaultStatusHandler(status -> status.is4xxClientError(), HttpServiceClientFactory::throwClientResponseException)
            .defaultStatusHandler(status -> status.is5xxServerError(), HttpServiceClientFactory::throwServerResponseException);
    }

    private static JacksonJsonHttpMessageConverter strictJsonConverter() {
        return new JacksonJsonHttpMessageConverter(JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .withCoercionConfig(LogicalType.Textual, coercions -> coercions
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)));
    }

    private static void throwClientResponseException(HttpRequest request, ClientHttpResponse response) throws IOException {
        throw HttpClientErrorException.create(sanitizedStatusMessage(response), response.getStatusCode(), "",
            response.getHeaders(), response.getBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void throwServerResponseException(HttpRequest request, ClientHttpResponse response) throws IOException {
        throw HttpServerErrorException.create(sanitizedStatusMessage(response), response.getStatusCode(), "",
            response.getHeaders(), response.getBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String sanitizedStatusMessage(ClientHttpResponse response) throws IOException {
        return "Upstream HTTP response status " + response.getStatusCode().value();
    }
}
