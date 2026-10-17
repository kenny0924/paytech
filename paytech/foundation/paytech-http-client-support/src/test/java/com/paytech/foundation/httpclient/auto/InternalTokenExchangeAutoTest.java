package com.paytech.foundation.httpclient.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.registry.ImportHttpServices;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.foundation.httpclient.test.simulator.HttpServiceSimulator;
import com.paytech.foundation.internalauth.api.CallerIdentity;
import com.paytech.foundation.internalauth.api.InternalPurpose;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.foundation.internalauth.api.InternalTokenUnavailableException;
import com.paytech.foundation.internalauth.core.supplier.StaticKeyInternalTokenSupport;
import com.paytech.foundation.internalauth.core.token.InternalTokenVerifierImpl;

/**
 * 出站内部调用 Token：按目标服务名注入 Bearer、按 {@link InternalPurpose} 覆盖 Purpose、取不到 Token 时不发请求。
 *
 * @see HttpServiceClientOptions#withInternalToken(InternalTokenSupplier, String)
 * @see HttpServiceClientOptions#withInternalToken(InternalTokenSupplier)
 * @see HttpServiceClientFactory#create(RestClient, HttpServiceClientOptions,
 *      com.paytech.foundation.common.enums.CodeMessageEnum...)
 * @see HttpServiceClientFactory#configureGroup(String, HttpServiceClientOptions,
 *      com.paytech.foundation.common.enums.CodeMessageEnum...)
 */
class InternalTokenExchangeAutoTest {

    private static final String TARGET = "card-service";

    private final ECKey key = generate();
    private final StaticKeyInternalTokenSupport issuer = new StaticKeyInternalTokenSupport(key, key.toPublicJWK(),
        "payment-service", "main", Clock.systemUTC());

    private HttpServiceSimulator simulator;

    @BeforeEach
    void startSimulator() throws IOException {
        simulator = new HttpServiceSimulator();
    }

    @AfterEach
    void stopSimulator() {
        simulator.close();
    }

    @Test
    void test_normalFlow_createInjectsTokenForTargetAndPurpose() {
        CardApi api = HttpServiceClientFactory.create(client(),
            HttpServiceClientOptions.strict().withInternalToken(issuer, TARGET)).createClient(CardApi.class);

        api.general();
        api.reveal();

        List<CallerIdentity> identities = callers();
        assertEquals(2, identities.size());
        assertEquals("payment-service", identities.get(0).caller());
        assertEquals("GENERAL", identities.get(0).purpose());
        assertEquals("CHANNEL_CARD_MATERIAL_REVEAL", identities.get(1).purpose());
    }

    @Test
    void test_normalFlow_groupUsesGroupNameAsTarget() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean("transport", RestClientHttpServiceGroupConfigurer.class,
                () -> groups -> groups.forEachClient((group, builder) -> builder.baseUrl(simulator.baseUrl())));
            context.registerBean("internalToken", RestClientHttpServiceGroupConfigurer.class,
                () -> HttpServiceClientFactory.configureGroup(TARGET,
                    HttpServiceClientOptions.strict().withInternalToken(issuer)));
            context.register(ImportedCardConfiguration.class);
            context.refresh();

            context.getBean(CardApi.class).reveal();
        }

        List<CallerIdentity> identities = callers();
        assertEquals(1, identities.size());
        assertEquals("CHANNEL_CARD_MATERIAL_REVEAL", identities.getFirst().purpose());
    }

    @Test
    void test_businessRuleViolation_tokenUnavailableSendsNothing() {
        InternalTokenSupplier failing = (target, purpose) -> {
            throw new InternalTokenUnavailableException(null);
        };
        CardApi api = HttpServiceClientFactory.create(client(),
            HttpServiceClientOptions.strict().withInternalToken(failing, TARGET)).createClient(CardApi.class);

        assertThrows(ServiceUnavailableException.class, api::general);
        assertTrue(simulator.requests().isEmpty());
    }

    @Test
    void test_invalidParameter_createWithoutTargetRejected() {
        HttpServiceClientOptions options = HttpServiceClientOptions.strict().withInternalToken(issuer);

        assertThrows(IllegalArgumentException.class, () -> HttpServiceClientFactory.create(client(), options));
    }

    @Test
    void test_edgeCase_withoutInternalTokenNoAuthorizationHeader() {
        CardApi api = HttpServiceClientFactory.create(client()).createClient(CardApi.class);

        api.general();

        assertEquals(null, simulator.requestHeaders().getFirst().get(HttpHeaders.AUTHORIZATION));
    }

    private List<CallerIdentity> callers() {
        InternalTokenVerifierImpl verifier = new InternalTokenVerifierImpl(issuer, TARGET, "main", Clock.systemUTC());
        return simulator.requestHeaders().stream()
            .map(headers -> headers.get(HttpHeaders.AUTHORIZATION).getFirst().substring("Bearer ".length()))
            .map(verifier::verify).toList();
    }

    private RestClient client() {
        HttpClient transport = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1))
            .followRedirects(HttpClient.Redirect.NEVER).build();
        return RestClient.builder().baseUrl(simulator.baseUrl())
            .requestFactory(new JdkClientHttpRequestFactory(transport)).build();
    }

    private static ECKey generate() {
        try {
            return new ECKeyGenerator(Curve.P_256).keyID("kid-1").generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** 测试目标服务的内部接口。 */
    @HttpExchange("/resource")
    public interface CardApi {

        @GetExchange
        HttpHeaders general();

        @GetExchange
        @InternalPurpose("CHANNEL_CARD_MATERIAL_REVEAL")
        HttpHeaders reveal();
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @ImportHttpServices(group = TARGET, types = CardApi.class)
    static class ImportedCardConfiguration {
    }
}
