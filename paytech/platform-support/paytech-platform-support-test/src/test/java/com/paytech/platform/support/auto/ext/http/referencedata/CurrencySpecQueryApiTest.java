package com.paytech.platform.support.auto.ext.http.referencedata;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.api.referencedata.CurrencySpecQueryApi;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecListResponseBody;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecResponseBody;
import com.paytech.platform.support.test.base.MockTestBase;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.CurrencySpecMocker;

/**
 * 全平台币种规则查询：经真实 Controller、Processor、Service、Repository、Mapper 与本地 MySQL。
 *
 * @see CurrencySpecQueryApi#listCurrencySpecs()
 * @see CurrencySpecQueryApi#getCurrencySpec(String)
 */
class CurrencySpecQueryApiTest extends MockTestBase {

    private static final String PATH = "/platform/v1/currency-specs";

    private static final Instant NOW = Instant.parse("2026-10-17T00:00:00.123456Z").truncatedTo(ChronoUnit.MICROS);

    @Autowired
    CurrencySpecMocker currencies;

    /** 表就绪且只有常驻 USD 种子，再插入本类公共的另外两条规则。 */
    @BeforeEach
    void setUpCurrencySpecs() {
        currencies.assertReady();
        currencies.currency("JPY", 0, CurrencySpecStatus.ACTIVE, NOW);
        currencies.currency("XTS", 2, CurrencySpecStatus.INACTIVE, NOW);
    }

    /** 全部规则按币种代码升序，字段与表列一致，停用规则同样返回。 */
    @Test
    void test_normalFlow() {
        var body = get(PATH).assertOk().as(CurrencySpecListResponseBody.class);

        assertEquals(List.of("JPY", "USD", "XTS"), body.getItems().stream().map(CurrencySpecResponseBody::getCurrency).toList());
        var jpy = body.getItems().getFirst();
        assertAll(() -> assertEquals(0, jpy.getMinorUnit()),
            () -> assertEquals(CurrencyRoundingMode.HALF_UP.getCode(), jpy.getRoundingMode()),
            () -> assertEquals(CurrencySpecStatus.ACTIVE.getCode(), jpy.getStatus()),
            () -> assertEquals(NOW.toString(), jpy.getUpdateTime()),
            () -> assertEquals(CurrencySpecStatus.INACTIVE.getCode(), body.getItems().get(2).getStatus()));
    }

    /** 按币种代码查询单条规则：人工初始化的 USD 种子。 */
    @Test
    void test_getByCurrency() {
        var usd = get(PATH + "/USD").assertOk().as(CurrencySpecResponseBody.class);

        assertAll(() -> assertEquals("USD", usd.getCurrency()), () -> assertEquals(2, usd.getMinorUnit()),
            () -> assertEquals(CurrencyRoundingMode.HALF_UP.getCode(), usd.getRoundingMode()),
            () -> assertEquals(CurrencySpecStatus.ACTIVE.getCode(), usd.getStatus()));
    }

    /** 未登记的币种：404。 */
    @Test
    void test_noDataFound() {
        get(PATH + "/EUR").assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    /** 币种代码格式不合法：400。 */
    @Test
    void test_invalidParameter() {
        get(PATH + "/usd").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(PATH + "/USDX").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
    }

    /** 以内部调用方身份发送 GET。 */
    private PlatformResponseChecker get(String path) {
        String requestId = "plt-ccy-" + UUID.randomUUID();
        return new PlatformResponseChecker(httpTestInvoker.get(path, TestRequestHeaders.internal(requestId)),
            objectMapper, requestId);
    }
}
