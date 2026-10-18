package com.paytech.foundation.internalauth.auto.supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.paytech.foundation.internalauth.api.InternalTokenUnavailableException;
import com.paytech.foundation.internalauth.contract.InternalTokenIssueApi;
import com.paytech.foundation.internalauth.contract.InternalTokenIssueRequest;
import com.paytech.foundation.internalauth.contract.InternalTokenIssueResponse;
import com.paytech.foundation.internalauth.core.supplier.PlatformInternalTokenSupplier;
import com.paytech.foundation.internalauth.test.MutableClock;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.foundation.testsupport.api.MockHttpServer;

/**
 * 向 Platform 申请内部 Token：按 (target, purpose) 缓存到过期前 60 秒，失败关闭；Platform 由实现签发合同的测试端点代替。
 *
 * @see PlatformInternalTokenSupplier#token(String, String)
 */
class PlatformInternalTokenSupplierAutoTest {

    private static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");

    private final PlatformController controller = new PlatformController();
    private final MockHttpServer server = new MockHttpServer(controller);
    private final MutableClock clock = new MutableClock(NOW);
    private final PlatformInternalTokenSupplier supplier = new PlatformInternalTokenSupplier(
        HttpServiceProxyFactory.builderFor(RestClientAdapter.create(RestClient.create(server.baseUrl()))).build()
            .createClient(InternalTokenIssueApi.class), clock);

    @AfterEach
    void closeServer() {
        server.assertNoUnexpectedRequests();
        server.close();
    }

    @Test
    void test_normalFlow_cachedUntilSixtySecondsBeforeExpiry() {
        controller.expiresAt = NOW.plusSeconds(300);

        String first = supplier.token("card-service", "GENERAL");
        clock.advance(Duration.ofSeconds(239));
        String cached = supplier.token("card-service", "GENERAL");
        clock.advance(Duration.ofSeconds(1));
        controller.expiresAt = NOW.plusSeconds(600);
        String refreshed = supplier.token("card-service", "GENERAL");

        assertEquals(first, cached);
        assertNotEquals(first, refreshed);
        assertEquals(List.of("card-service/GENERAL", "card-service/GENERAL"), controller.requests);
    }

    @Test
    void test_normalFlow_cachedPerTargetAndPurpose() {
        controller.expiresAt = NOW.plusSeconds(300);

        supplier.token("card-service", "GENERAL");
        supplier.token("card-service", "CHANNEL_CARD_MATERIAL_REVEAL");
        supplier.token("ledger-service", "GENERAL");
        supplier.token("card-service", "GENERAL");

        assertEquals(List.of("card-service/GENERAL", "card-service/CHANNEL_CARD_MATERIAL_REVEAL",
            "ledger-service/GENERAL"), controller.requests);
    }

    @Test
    void test_businessRuleViolation_platformUnavailableFailsClosed() {
        controller.failing = true;

        assertThrows(InternalTokenUnavailableException.class, () -> supplier.token("card-service", "GENERAL"));
    }

    @Test
    void test_businessRuleViolation_tokenTooCloseToExpiry() {
        controller.expiresAt = NOW.plusSeconds(60);

        assertThrows(InternalTokenUnavailableException.class, () -> supplier.token("card-service", "GENERAL"));
    }

    @Test
    void test_invalidParameter() {
        assertThrows(IllegalArgumentException.class, () -> supplier.token("Card_Service", "GENERAL"));
        assertThrows(IllegalArgumentException.class, () -> supplier.token("card-service", "general"));
        assertEquals(List.of(), controller.requests);
    }

    /** 实现签发合同的测试端点：返回按请求编号区分的 Token，或按配置返回 503。 */
    @RestController
    static final class PlatformController implements InternalTokenIssueApi {

        private final List<String> requests = new CopyOnWriteArrayList<>();
        private volatile Instant expiresAt = NOW.plusSeconds(300);
        private volatile boolean failing;

        @Override
        public InternalTokenIssueResponse issue(InternalTokenIssueRequest request) {
            requests.add(request.getTargetService() + "/" + request.getPurpose());
            if (failing) {
                throw MockHttpFault.raw(503, "{}");
            }
            InternalTokenIssueResponse response = new InternalTokenIssueResponse();
            response.setToken("token-" + requests.size());
            response.setExpiresAt(expiresAt.toString());
            return response;
        }
    }
}
