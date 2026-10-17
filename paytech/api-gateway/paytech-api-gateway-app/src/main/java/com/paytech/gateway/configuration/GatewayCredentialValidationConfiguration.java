package com.paytech.gateway.configuration;

import java.util.Arrays;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.paytech.gateway.security.clientapi.ClientApiCredentialDependencyUnavailableException;
import com.paytech.gateway.security.clientapi.PlatformClientApiCredentialValidationClient;
import com.paytech.gateway.security.clientapi.ReactiveClientApiCredentialValidationClient;
import com.paytech.gateway.web.GatewayExchangeAttributes;
import com.paytech.gateway.web.GatewayProtocol;
import com.paytech.gateway.web.OwnerProblemValidator;
import com.paytech.platform.support.api.apicredential.ApiCredentialValidationApi;
import com.paytech.platform.support.api.enums.ApiCredentialErrorCode;

import io.netty.channel.ChannelOption;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/** PLT-I03 独立有界 transport 与 Owner typed proxy；不借用业务 Target 或阻塞客户端。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PlatformCredentialValidationProperties.class)
public class GatewayCredentialValidationConfiguration {
    @Bean(destroyMethod = "dispose")
    ConnectionProvider platformCredentialConnectionProvider(PlatformCredentialValidationProperties properties) {
        return ConnectionProvider.builder("gateway-platform-credential").maxConnections(32).pendingAcquireMaxCount(64)
            .pendingAcquireTimeout(properties.connectTimeout()).maxIdleTime(properties.responseTimeout()).build();
    }

    @Bean(destroyMethod = "dispose")
    LoopResources platformCredentialLoopResources() {
        return LoopResources.create("gateway-platform-credential", 1, true);
    }

    @Bean
    ReactiveClientApiCredentialValidationClient reactiveClientApiCredentialValidationClient(
        PlatformCredentialValidationProperties properties, Environment environment,
        ConnectionProvider platformCredentialConnectionProvider, LoopResources platformCredentialLoopResources,
        ObjectProvider<ReactorLoadBalancerExchangeFilterFunction> loadBalancerProvider, OwnerProblemValidator validator) {
        properties.validateEnvironment(environment);
        JsonMapper mapper = strictMapper(properties.maxResponseBytes());
        JacksonJsonDecoder decoder = new JacksonJsonDecoder(mapper);
        decoder.setMaxInMemorySize(properties.maxResponseBytes());
        HttpClient transport = HttpClient.create(platformCredentialConnectionProvider)
            .runOn(platformCredentialLoopResources).followRedirect(false).disableRetry(true)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(properties.connectTimeout().toMillis()))
            .responseTimeout(properties.responseTimeout());
        String origin = properties.discoveryEnabled() ? "http://" + properties.serviceId() : properties.baseUri().toString();
        WebClient.Builder builder = WebClient.builder().baseUrl(origin + "/platform-support")
            .clientConnector(new ReactorClientHttpConnector(transport))
            .codecs(codecs -> {
                codecs.defaultCodecs().maxInMemorySize(properties.maxResponseBytes());
                codecs.defaultCodecs().jacksonJsonDecoder(decoder);
            }).filter(protocolFilter(properties, mapper, validator));
        if (properties.discoveryEnabled()) {
            var loadBalancer = loadBalancerProvider.getIfAvailable();
            if (loadBalancer == null) {
                throw new IllegalStateException("Gateway Platform Credential 服务发现不可用");
            }
            builder.filter(loadBalancer);
        }
        ApiCredentialValidationApi proxy = HttpServiceProxyFactory.builderFor(WebClientAdapter.create(builder.build()))
            .build().createClient(ApiCredentialValidationApi.class);
        return new PlatformClientApiCredentialValidationClient(proxy, properties.responseTimeout());
    }

    private ExchangeFilterFunction protocolFilter(PlatformCredentialValidationProperties properties,
        JsonMapper mapper, OwnerProblemValidator validator) {
        return (request, next) -> Mono.deferContextual(context -> {
            String requestId = context.get(GatewayExchangeAttributes.REQUEST_ID);
            ClientRequest correlated = ClientRequest.from(request)
                .headers(headers -> headers.set(GatewayProtocol.REQUEST_ID, requestId)).build();
            return next.exchange(correlated).flatMap(response -> {
                if (response.statusCode().value() == 200 && validJsonMedia(response)) {
                    return Mono.just(response);
                }
                if (response.statusCode().value() == 401) {
                    return authenticationFailure(response, requestId, properties.maxResponseBytes(), mapper, validator);
                }
                return response.releaseBody().then(Mono.error(new ClientApiCredentialDependencyUnavailableException()));
            });
        });
    }

    private boolean validJsonMedia(ClientResponse response) {
        var values = response.headers().asHttpHeaders().getOrEmpty("Content-Type");
        if (values.size() != 1) {
            return false;
        }
        try {
            MediaType media = MediaType.parseMediaType(values.getFirst());
            return "application".equalsIgnoreCase(media.getType()) && "json".equalsIgnoreCase(media.getSubtype());
        } catch (IllegalArgumentException exception) {
            // 返回协议失败分支释放响应 Body，避免在 MediaType 解析时丢失连接所有权。
            return false;
        }
    }

    private Mono<ClientResponse> authenticationFailure(ClientResponse response, String requestId, int maximum,
        JsonMapper mapper, OwnerProblemValidator validator) {
        return DataBufferUtils.join(response.bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class), maximum)
            .flatMap(buffer -> {
                byte[] bytes = new byte[buffer.readableByteCount()];
                try {
                    buffer.read(bytes);
                    validator.validate(HttpStatus.UNAUTHORIZED, response.headers().asHttpHeaders(), bytes, requestId, false);
                    String code = mapper.readTree(bytes).get("code").stringValue();
                    if (ApiCredentialErrorCode.API_CREDENTIAL_AUTHENTICATION_FAILED.getCode().equals(code)) {
                        return Mono.<ClientResponse>error(new BadCredentialsException("未通过身份验证"));
                    }
                    return Mono.<ClientResponse>error(new ClientApiCredentialDependencyUnavailableException());
                } finally {
                    Arrays.fill(bytes, (byte)0);
                    DataBufferUtils.release(buffer);
                }
            }).switchIfEmpty(Mono.error(new ClientApiCredentialDependencyUnavailableException()));
    }

    private JsonMapper strictMapper(int maximum) {
        JsonFactory factory = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxDocumentLength(maximum).maxNestingDepth(16).build())
            .build();
        return JsonMapper.builder(factory).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .withCoercionConfig(LogicalType.Textual, config -> config
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)).build();
    }
}
