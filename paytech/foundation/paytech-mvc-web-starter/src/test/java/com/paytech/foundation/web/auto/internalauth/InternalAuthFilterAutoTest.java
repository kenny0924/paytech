package com.paytech.foundation.web.auto.internalauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.paytech.foundation.internalauth.api.CallerIdentity;
import com.paytech.foundation.internalauth.api.CallerIdentityArgument;
import com.paytech.foundation.internalauth.api.CallerIdentityHolder;
import com.paytech.foundation.internalauth.api.InternalAuthMode;
import com.paytech.foundation.internalauth.core.inbound.InternalCallerAuthenticator;
import com.paytech.foundation.internalauth.core.supplier.StaticKeyInternalTokenSupport;
import com.paytech.foundation.internalauth.core.token.InternalTokenVerifierImpl;
import com.paytech.foundation.web.error.ApiProblemResponseFactory;
import com.paytech.foundation.web.error.GlobalExceptionHandler;
import com.paytech.foundation.web.error.MvcProblemExceptionMapper;
import com.paytech.foundation.web.error.ProblemResponseWriter;
import com.paytech.foundation.web.error.RequestBodyFieldErrorProjector;
import com.paytech.foundation.web.internalauth.CallerIdentityArgumentResolver;
import com.paytech.foundation.web.internalauth.InternalAuthFilter;
import com.paytech.foundation.web.request.RequestIdFilter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Servlet 入站内部调用身份：OFF / DUAL / REQUIRED 三种模式经真实过滤器、共享 Problem 写出器与参数解析（MockMvc）。
 *
 * @see InternalAuthFilter#doFilter(jakarta.servlet.ServletRequest, jakarta.servlet.ServletResponse,
 *      jakarta.servlet.FilterChain)
 * @see CallerIdentityArgumentResolver#resolveArgument(org.springframework.core.MethodParameter,
 *      org.springframework.web.method.support.ModelAndViewContainer,
 *      org.springframework.web.context.request.NativeWebRequest, org.springframework.web.bind.support.WebDataBinderFactory)
 */
class InternalAuthFilterAutoTest {

    private static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");

    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final String SERVICE = "card-service";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ECKey key = generate();
    private final StaticKeyInternalTokenSupport issuer = new StaticKeyInternalTokenSupport(key, key.toPublicJWK(),
        "payment-service", "main", CLOCK);
    private final ProblemResponseWriter writer = new ProblemResponseWriter(new ApiProblemResponseFactory(CLOCK),
        mapper);

    @Test
    void test_normalFlow_requiredAcceptsValidToken() throws Exception {
        MockMvc mvc = mvc(InternalAuthMode.REQUIRED);

        assertOk(mvc, get("/internal/caller").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("GENERAL")),
            "payment-service|GENERAL|true|bound");
    }

    @Test
    void test_businessRuleViolation_requiredRejectsWithProblem() throws Exception {
        MockMvc mvc = mvc(InternalAuthMode.REQUIRED);

        MockHttpServletResponse response = mvc.perform(get("/internal/caller").header("X-Request-Id", "req-401"))
            .andReturn().getResponse();

        assertEquals(401, response.getStatus());
        assertEquals("req-401", response.getHeader("X-Request-Id"));
        assertEquals("application/problem+json", response.getContentType());
        JsonNode problem = mapper.readTree(response.getContentAsByteArray());
        assertEquals("about:blank", problem.get("type").asString());
        assertEquals("Unauthorized", problem.get("title").asString());
        assertEquals("INTERNAL_TOKEN_INVALID", problem.get("code").asString());
        assertEquals("内部调用凭证无效", problem.get("detail").asString());
        assertEquals("req-401", problem.get("requestId").asString());
        assertEquals("2026-10-17T10:00:00.000000Z", problem.get("timestamp").asString());
        assertProblem(mvc, get("/internal/caller").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("ledger-service")),
            401, "INTERNAL_TOKEN_INVALID");
        assertProblem(mvc, get("/internal/caller").header("X-Caller-Service", "payment-service"), 401,
            "INTERNAL_TOKEN_INVALID");
    }

    @Test
    void test_normalFlow_dualAcceptsTokenOrLegacyHeader() throws Exception {
        MockMvc mvc = mvc(InternalAuthMode.DUAL);

        assertOk(mvc, get("/internal/caller").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("GENERAL")),
            "payment-service|GENERAL|true|bound");
        assertOk(mvc, get("/internal/caller").header("X-Caller-Service", "risk-service"),
            "risk-service|null|false|bound");
        assertOk(mvc, get("/internal/caller").header(HttpHeaders.AUTHORIZATION, "Bearer broken")
            .header("X-Caller-Service", "risk-service"), "risk-service|null|false|bound");
        assertOk(mvc, get("/internal/optional"), "none");
        assertProblem(mvc, get("/internal/caller").header("X-Caller-Service", "Bad Name"), 401,
            "INTERNAL_TOKEN_INVALID");
    }

    @Test
    void test_edgeCase_offKeepsExistingBehaviour() throws Exception {
        MockMvc mvc = standalone().build();

        assertOk(mvc, get("/internal/optional").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("GENERAL")), "none");
        assertProblem(mvc, get("/internal/caller"), 401, "INTERNAL_TOKEN_INVALID");
    }

    @Test
    void test_edgeCase_exemptPrefixesSkipVerification() throws Exception {
        MockMvc mvc = mvc(InternalAuthMode.REQUIRED);

        assertOk(mvc, get("/actuator/health"), "ok");
        assertOk(mvc, get("/public/ping"), "ok");
    }

    @Test
    void test_businessRuleViolation_keySourceUnavailableReturns503() throws Exception {
        InternalTokenVerifierImpl verifier = new InternalTokenVerifierImpl(keyId -> {
            throw new com.paytech.foundation.internalauth.api.InternalTokenUnavailableException(null);
        }, SERVICE, "main", CLOCK);
        MockMvc mvc = standalone().addFilters(new RequestIdFilter(), new InternalAuthFilter(
            new InternalCallerAuthenticator(InternalAuthMode.REQUIRED, verifier, List.of("/actuator")), writer)).build();

        assertProblem(mvc, get("/internal/caller").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("GENERAL")), 503,
            "SERVICE_UNAVAILABLE");
    }

    private void assertOk(MockMvc mvc, RequestBuilder request, String expectedBody) throws Exception {
        MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
        String body = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(200, response.getStatus(), body);
        assertEquals(expectedBody, mapper.readTree(body).asString());
    }

    private void assertProblem(MockMvc mvc, RequestBuilder request, int status, String code) throws Exception {
        MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
        assertEquals(status, response.getStatus());
        assertEquals(code, mapper.readTree(response.getContentAsByteArray()).get("code").asString());
    }

    private MockMvc mvc(InternalAuthMode mode) {
        InternalTokenVerifierImpl verifier = new InternalTokenVerifierImpl(issuer, SERVICE, "main", CLOCK);
        InternalCallerAuthenticator authenticator = new InternalCallerAuthenticator(mode, verifier,
            List.of("/actuator", "/public/"));
        return standalone().addFilters(new RequestIdFilter(), new InternalAuthFilter(authenticator, writer)).build();
    }

    private StandaloneMockMvcBuilder standalone() {
        MvcProblemExceptionMapper exceptionMapper = new MvcProblemExceptionMapper((code, status) -> status,
            new RequestBodyFieldErrorProjector(mapper));
        return MockMvcBuilders.standaloneSetup(new CallerController())
            .setControllerAdvice(new GlobalExceptionHandler(exceptionMapper, writer))
            .setCustomArgumentResolvers(new CallerIdentityArgumentResolver())
            .setMessageConverters(new JacksonJsonHttpMessageConverter(mapper));
    }

    private String token(String purpose) {
        return issuer.token(SERVICE, purpose);
    }

    private String tokenFor(String audience) {
        return issuer.token(audience, "GENERAL");
    }

    private static ECKey generate() {
        try {
            return new ECKeyGenerator(Curve.P_256).keyID("kid-1").generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** 读取调用方身份的测试 Controller。 */
    @RestController
    static final class CallerController {

        @GetMapping("/internal/caller")
        String caller(@CallerIdentityArgument CallerIdentity identity) {
            String bound = CallerIdentityHolder.current() == identity ? "bound" : "unbound";
            return identity.caller() + "|" + identity.purpose() + "|" + identity.verified() + "|" + bound;
        }

        @GetMapping("/internal/optional")
        String optional(@CallerIdentityArgument(required = false) CallerIdentity identity) {
            return identity == null ? "none" : identity.caller();
        }

        @GetMapping({"/actuator/health", "/public/ping"})
        String open() {
            return "ok";
        }
    }
}
