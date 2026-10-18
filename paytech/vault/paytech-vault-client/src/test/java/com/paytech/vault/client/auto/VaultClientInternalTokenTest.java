package com.paytech.vault.client.auto;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.test.context.FilteredClassLoader;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.internalauth.api.InternalTokenClaims;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.foundation.internalauth.api.InternalTokenUnavailableException;
import com.paytech.vault.client.ProtectCommand;
import com.paytech.vault.client.RevealQuery;
import com.paytech.vault.client.TokenMode;
import com.paytech.vault.client.VaultClient;
import com.paytech.vault.client.VaultClientErrorCode;
import com.paytech.vault.client.admin.VaultAdminClient;
import com.paytech.vault.client.test.ClientContext;
import com.paytech.vault.client.test.VaultSimulator;

/**
 * 可选内部调用 Token（D26）：存在 {@code InternalTokenSupplier} 时附加 {@code Authorization: Bearer}，
 * 不存在或宿主未引入 internal-auth 时请求与引入前一致；{@code X-Caller-Service} 始终保留。
 *
 * <p>被测入口：{@code VaultClient.protect/reveal}、{@code VaultAdminClient}（自动配置 + 真实 HTTP 到 VaultSimulator）。
 */
class VaultClientInternalTokenTest {
    private static final String COMPACT_TOKEN = "eyJhbGciOiJFUzI1NiJ9.e30.c2lnbmF0dXJl";

    private static ProtectCommand command() {
        return new ProtectCommand("EMAIL_ADDRESS_V1", TokenMode.STABLE, "TEST-ONLY-NOT-A-SECRET@example.test",
            "CHECKOUT_CONTACT", "logical-command-1");
    }

    /** 记录调用参数的 Supplier；返回固定 Token 或按配置失败。 */
    private static final class RecordingSupplier implements InternalTokenSupplier {
        private final List<String> calls = new CopyOnWriteArrayList<>();
        private final boolean unavailable;

        private RecordingSupplier(boolean unavailable) {
            this.unavailable = unavailable;
        }

        @Override
        public String token(String targetService, String purpose) {
            calls.add(targetService + "|" + purpose);
            if (unavailable) {
                throw new InternalTokenUnavailableException(new IllegalStateException("platform down"));
            }
            return COMPACT_TOKEN;
        }
    }

    @Test
    void test_supplierPresentAttachesBearerTokenAndKeepsCallerHeader() throws Exception {
        var supplier = new RecordingSupplier(false);
        try (var simulator = new VaultSimulator()) {
            simulator.respond(200, "{\"token\":\"" + VaultClientSafetyTest.TOKEN + "\"}");
            simulator.respond(200, "{\"value\":\"\"}");
            ClientContext.direct(simulator).withBean(InternalTokenSupplier.class, () -> supplier).run(context -> {
                VaultClient client = context.getBean(VaultClient.class);
                client.protect(command());
                client.reveal(new RevealQuery(VaultClientSafetyTest.TOKEN, "CHECKOUT_CONTACT"));
            });
            assertThat(simulator.requests).hasSize(2);
            for (var request : simulator.requests) {
                assertThat(request.headers().get("Authorization")).containsExactly("Bearer " + COMPACT_TOKEN);
                assertThat(request.headers().get("X-Caller-Service")).containsExactly("commerce-service");
            }
        }
        assertThat(supplier.calls).containsExactly("vault-service|" + InternalTokenClaims.GENERAL_PURPOSE,
            "vault-service|" + InternalTokenClaims.GENERAL_PURPOSE);
    }

    @Test
    void test_supplierAbsentSendsNoAuthorizationAndStripsHostHeader() throws Exception {
        try (var simulator = new VaultSimulator()) {
            simulator.respond(200, "{\"token\":\"" + VaultClientSafetyTest.TOKEN + "\"}");
            ClientContext.direct(simulator)
                .withBean(RestClientCustomizer.class,
                    () -> builder -> builder.defaultHeader("Authorization", "Bearer host-forged"))
                .run(context -> context.getBean(VaultClient.class).protect(command()));
            assertThat(simulator.requests).hasSize(1);
            assertThat(simulator.requests.getFirst().headers().get("Authorization")).isNull();
            assertThat(simulator.requests.getFirst().headers().get("X-Caller-Service")).containsExactly("commerce-service");
        }
    }

    @Test
    void test_withoutInternalAuthOnClasspathClientStartsAndSendsNoToken() throws Exception {
        try (var simulator = new VaultSimulator()) {
            simulator.respond(200, "{\"value\":\"\"}");
            ClientContext.direct(simulator)
                .withClassLoader(new FilteredClassLoader("com.paytech.foundation.internalauth"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    context.getBean(VaultClient.class).reveal(new RevealQuery(VaultClientSafetyTest.TOKEN, "P"));
                });
            assertThat(simulator.requests).hasSize(1);
            assertThat(simulator.requests.getFirst().headers().get("Authorization")).isNull();
        }
    }

    @Test
    void test_supplierFailureFailsClosedWithoutSendingRequest() throws Exception {
        var supplier = new RecordingSupplier(true);
        try (var simulator = new VaultSimulator()) {
            ClientContext.direct(simulator).withBean(InternalTokenSupplier.class, () -> supplier).run(context -> {
                Throwable failure = catchThrowable(() -> context.getBean(VaultClient.class).protect(command()));
                assertThat(failure).isExactlyInstanceOf(ServiceUnavailableException.class).hasNoCause()
                    .hasMessageNotContaining("platform down");
                assertThat(((ServiceUnavailableException) failure).getCode())
                    .isEqualTo(VaultClientErrorCode.DEPENDENCY_UNAVAILABLE.getCode());
            });
            assertThat(simulator.requests).isEmpty();
        }
        assertThat(supplier.calls).hasSize(1);
    }

    @Test
    void test_adminClientAlsoAttachesToken() throws Exception {
        var supplier = new RecordingSupplier(false);
        try (var simulator = new VaultSimulator()) {
            simulator.respond(200, "{\"policyVersionNo\":1,\"items\":[]}");
            ClientContext.direct(simulator).withPropertyValues("vault.client.admin-enabled=true",
                    "spring.application.name=operations-console-bff")
                .withBean(InternalTokenSupplier.class, () -> supplier).run(context -> {
                    // 只关注出站头；响应体是否满足管理接口形状不在本用例范围。
                    catchThrowable(() -> context.getBean(VaultAdminClient.class).listDataTypes("operator-1", null));
                });
            assertThat(simulator.requests).hasSize(1);
            assertThat(simulator.requests.getFirst().headers().get("Authorization"))
                .containsExactly("Bearer " + COMPACT_TOKEN);
            assertThat(simulator.requests.getFirst().headers().get("X-Caller-Service"))
                .containsExactly("operations-console-bff");
        }
    }
}
