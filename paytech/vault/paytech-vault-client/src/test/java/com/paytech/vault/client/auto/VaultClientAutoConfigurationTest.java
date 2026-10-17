package com.paytech.vault.client.auto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;

import com.paytech.vault.client.RevealQuery;
import com.paytech.vault.client.VaultClient;
import com.paytech.vault.client.test.ClientContext;
import com.paytech.vault.client.test.VaultSimulator;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class VaultClientAutoConfigurationTest {
    private Logger contextLogger;
    private Level previousLevel;
    private boolean previousAdditive;
    private int expectedFailures;
    private ListAppender<ILoggingEvent> startupLogs;

    @BeforeEach
    void captureExpectedStartupFailures() {
        contextLogger = (Logger) LoggerFactory.getLogger("org.springframework.context.annotation.AnnotationConfigApplicationContext");
        previousLevel = contextLogger.getLevel();
        previousAdditive = contextLogger.isAdditive();
        startupLogs = new ListAppender<>();
        startupLogs.start();
        contextLogger.addAppender(startupLogs);
        contextLogger.setLevel(Level.WARN);
        contextLogger.setAdditive(false);
    }

    @AfterEach
    void assertAndRestoreStartupLogging() {
        try {
            assertThat(startupLogs.list).hasSize(expectedFailures);
            for (var event : startupLogs.list) {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).contains("Vault 客户端配置无效")
                        .doesNotContain(VaultClientSafetyTest.TOKEN, VaultClientSafetyTest.VALUE, VaultClientSafetyTest.KEY);
            }
        } finally {
            contextLogger.detachAppender(startupLogs);
            contextLogger.setLevel(previousLevel);
            contextLogger.setAdditive(previousAdditive);
            startupLogs.stop();
        }
    }

    private void assertInvalidConfiguration(AssertableApplicationContext context) {
        expectedFailures++;
        assertThat(context).hasFailed();
    }

    @Test
    void test_invalidDirectTargetsAndServiceNamesRejectAtStartup() throws Exception {
        try (var simulator = new VaultSimulator()) {
            for (String url : new String[]{"", "not a uri", "https://localhost", "http://user@localhost", "http://localhost/path",
                    "http://localhost?query=x", "http://localhost#fragment", "http:///missing-host"}) {
                ClientContext.direct(simulator).withPropertyValues("vault.client.base-url=" + url)
                        .run(this::assertInvalidConfiguration);
            }
            for (String service : new String[]{"", "Wrong", "two,values", "x".repeat(129)}) {
                ClientContext.runner().withPropertyValues("vault.client.service-id=" + service)
                        .run(this::assertInvalidConfiguration);
            }
        }
    }

    @Test
    void test_transportHasBoundedLifetimeAndPositiveSubMillisecondTimeout() throws Exception {
        var transport = new java.util.concurrent.atomic.AtomicReference<java.net.http.HttpClient>();
        try (var simulator = new VaultSimulator()) {
            ClientContext.direct(simulator).withPropertyValues("vault.client.read-timeout=1ns")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        transport.set(context.getBean("vaultClientHttpTransport", java.net.http.HttpClient.class));
                        assertThat(transport.get().connectTimeout()).contains(java.time.Duration.ofSeconds(1));
                        assertThat(transport.get().followRedirects()).isEqualTo(java.net.http.HttpClient.Redirect.NEVER);
                    });
        }
        assertThat(transport.get().isTerminated()).isTrue();
    }

    @Test
    void test_serviceIdUsesRealLoadBalancerWithoutNacos() throws Exception {
        try (var simulator = new VaultSimulator()) {
            simulator.respond(200, "{\"value\":\"discovered\"}");
            ClientContext.runner().withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    org.springframework.cloud.loadbalancer.config.LoadBalancerAutoConfiguration.class,
                    org.springframework.cloud.loadbalancer.config.BlockingLoadBalancerClientAutoConfiguration.class))
                    .withBean(org.springframework.boot.restclient.RestClientCustomizer.class, () -> builder -> builder.uriBuilderFactory(
                            new org.springframework.web.util.DefaultUriBuilderFactory(simulator.url() + "/wrong-prefix")))
                    .withBean(org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier.class,
                            () -> new org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier() {
                                @Override public String getServiceId() { return "vault-service"; }
                                @Override public reactor.core.publisher.Flux<java.util.List<org.springframework.cloud.client.ServiceInstance>> get() {
                                    return reactor.core.publisher.Flux.just(java.util.List.of(new org.springframework.cloud.client.DefaultServiceInstance(
                                            "simulator", "vault-service", "127.0.0.1", simulator.port(), false)));
                                }
                            }).run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean(VaultClient.class).reveal(new RevealQuery(VaultClientSafetyTest.TOKEN, "P")).value())
                                .isEqualTo("discovered");
                        assertThat(simulator.requests).hasSize(1);
                    assertThat(simulator.requests.getFirst().path()).isEqualTo("/vault/v1/records/reveal");
                        assertThat(context.getBeanDefinitionNames()).noneMatch(name -> name.toLowerCase().contains("nacos"));
                    });
        }
    }

    @Test
    void test_directUrlIsTestOnlyAndModesAreExclusive() throws Exception {
        try (var simulator = new VaultSimulator()) {
            ClientContext.runner().withPropertyValues("vault.client.base-url=" + simulator.url())
                    .run(this::assertInvalidConfiguration);
            ClientContext.direct(simulator).withPropertyValues("vault.client.service-id=other-vault")
                    .run(this::assertInvalidConfiguration);
            ClientContext.direct(simulator).withPropertyValues("spring.profiles.active=unittest")
                    .run(context -> assertThat(context).hasSingleBean(VaultClient.class));
        }
    }

    @Test
    void test_boundedRequiredTimeoutsAndCaller() throws Exception {
        try (var simulator = new VaultSimulator()) {
            for (String invalid : new String[]{"0s", "-1s", "31s", ""}) {
                ClientContext.direct(simulator).withPropertyValues("vault.client.connect-timeout=" + invalid)
                        .run(this::assertInvalidConfiguration);
                ClientContext.direct(simulator).withPropertyValues("vault.client.read-timeout=" + invalid)
                        .run(this::assertInvalidConfiguration);
            }
            for (String invalid : new String[]{"", "Bad-Caller", "a,b", "a".repeat(129)}) {
                ClientContext.direct(simulator).withPropertyValues("vault.client.caller-service=" + invalid)
                        .run(this::assertInvalidConfiguration);
            }
            ClientContext.direct(simulator).withPropertyValues("vault.client.connect-timeout=30s", "vault.client.read-timeout=30s")
                    .run(context -> assertThat(context).hasNotFailed());
        }
    }
}
