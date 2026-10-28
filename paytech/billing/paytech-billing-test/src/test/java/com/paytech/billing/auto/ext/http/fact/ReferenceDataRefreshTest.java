package com.paytech.billing.auto.ext.http.fact;

import static com.paytech.billing.test.builder.TestCurrencies.USD;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.billing.test.base.AbstractBillingFactApiTest;
import com.paytech.billing.test.builder.BillingFactRequestBuilder;
import com.paytech.platform.support.referencedata.client.ReferenceDataKind;
import com.paytech.platform.support.referencedata.client.ReferenceDataRefresher;

import tools.jackson.databind.JsonNode;

/**
 * billing 接入参考数据客户端后的币种快照刷新与就绪（参考数据客户端组件 AC-03、AC-07；原币种快照需求 AC-06）
 * <li>全局 BeforeEach 已以默认 USD 加载一次；Platform 503 时本次刷新失败、保留上一份快照，计费事实登记与就绪探针（readiness 组含
 * referenceData）不受影响。</li>
 * <li>加载、校验、未就绪与周期等通用行为由客户端模块自己的 auto 测试覆盖，这里只验证 billing 的接入。</li>
 *
 * @see ReferenceDataRefresher#refreshNow()
 */
class ReferenceDataRefreshTest extends AbstractBillingFactApiTest {

    /** Platform 503：刷新失败但保留 USD 快照；登记照常，就绪探针仍为 UP。 */
    @Test
    void test_platformUnavailableKeepsSnapshot() {
        referenceDataMocker.unavailable(ReferenceDataKind.CURRENCY);

        List<ReferenceDataRefresher.Result> results = referenceDataRefresher.refreshNow();

        assertAll(() -> assertFalse(results.get(0).succeeded(), "503 时本次刷新应失败"),
            () -> assertEquals(2, referenceData.currencies().require(USD).minorUnit()),
            () -> assertEquals(2, referenceDataMocker.callCount(ReferenceDataKind.CURRENCY)),
            () -> assertEquals("billing-service", referenceDataMocker.lastCallerService(), "请求应带 X-Caller-Service"));
        submit(BillingFactRequestBuilder.template()).assertSuccess(201);
        assertReadinessUp();
    }

    /** 就绪探针（readiness 组含 referenceData）为 UP。 */
    void assertReadinessUp() {
        EntityExchangeResult<byte[]> response = management("/actuator/health/readiness");
        JsonNode body = objectMapper.readTree(response.getResponseBody());
        assertAll(() -> assertEquals(200, response.getStatus().value()),
            () -> assertEquals(Status.UP.getCode(), body.path("status").asString()));
    }
}
