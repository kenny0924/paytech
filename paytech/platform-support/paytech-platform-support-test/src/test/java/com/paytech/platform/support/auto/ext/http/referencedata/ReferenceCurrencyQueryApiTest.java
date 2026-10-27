package com.paytech.platform.support.auto.ext.http.referencedata;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.api.referencedata.ReferenceCurrencyQueryApi;
import com.paytech.platform.support.api.referencedata.response.ReferenceCurrencyListResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCurrencyResponseBody;
import com.paytech.platform.support.test.base.MockTestBase;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.CurrencySpecMocker;
import com.paytech.platform.support.test.mocker.local.ReferenceDataMocker;

/**
 * 参考数据币种查询：经真实 Controller、Processor、Repository、Mapper 与文件元数据批量读取。
 * <li>常驻种子 USD / AED / SAR / EGP 由人工初始化；本类另插入的币种在清理时回收。</li>
 *
 * @see ReferenceCurrencyQueryApi#listCurrencies(String, String)
 */
class ReferenceCurrencyQueryApiTest extends MockTestBase {

    private static final String PATH = "/platform/v1/reference-data/currencies";

    private static final Instant NOW = Instant.parse("2026-10-27T00:00:00.123456Z");

    @Autowired
    CurrencySpecMocker currencies;

    @Autowired
    ReferenceDataMocker referenceData;

    /** 表就绪且只有常驻种子，再插入本类公共的 DRAFT 与 INACTIVE 币种。 */
    @BeforeEach
    void setUpCurrencies() {
        currencies.assertReady();
        currencies.currency("JPY", 0, CurrencySpecStatus.DRAFT, NOW);
        currencies.currency("XTS", 2, CurrencySpecStatus.INACTIVE, 3, NOW);
    }

    /** 不带条件返回全部状态，按代码升序；种子带 ISO 数字代码、英文名与符号，没有 logo。 */
    @Test
    void test_normalFlow() {
        List<ReferenceCurrencyResponseBody> items = list("").getItems();

        assertEquals(List.of("AED", "EGP", "JPY", "SAR", "USD", "XTS"), codes(items));
        ReferenceCurrencyResponseBody usd = items.get(4);
        ReferenceCurrencyResponseBody aed = items.getFirst();
        assertAll(() -> assertEquals("840", usd.getNumericCode()), () -> assertEquals("US Dollar", usd.getName()),
            () -> assertEquals("$", usd.getSymbol()), () -> assertEquals(2, usd.getMinorUnit()),
            () -> assertEquals(CurrencyRoundingMode.HALF_UP.getCode(), usd.getRoundingMode()),
            () -> assertEquals(ReferenceDataStatus.ACTIVE.getCode(), usd.getStatus()),
            () -> assertEquals(1, usd.getVersion()), () -> assertNull(usd.getLogoFileAddress()),
            () -> assertNull(usd.getLogoPublicUrl()), () -> assertEquals("784", aed.getNumericCode()),
            () -> assertEquals("AED", aed.getSymbol()),
            () -> assertEquals(ReferenceDataStatus.INACTIVE.getCode(), items.get(5).getStatus()),
            () -> assertEquals(3, items.get(5).getVersion()));
    }

    /** 状态与编码同时生效；编码去空白、转大写，不存在的编码不报错也不出现。 */
    @Test
    void test_statusAndCodes() {
        assertAll(() -> assertEquals(List.of("AED", "EGP", "SAR", "USD"), codes(list("?status=ACTIVE").getItems())),
            () -> assertEquals(List.of("JPY"), codes(list("?status=DRAFT").getItems())),
            () -> assertEquals(List.of("AED", "USD"), codes(list("?codes= usd ,AED,XXX").getItems())),
            () -> assertEquals(List.of(), codes(list("?status=ACTIVE&codes=JPY").getItems())));
    }

    /** 公开文件的 logo 换出公开地址；私有文件只返回文件地址，公开地址为空。 */
    @Test
    void test_logoPublicUrl() {
        var publicLogo = referenceData.publicLogo();
        var privateLogo = referenceData.privateLogo();
        currencies.currency("XAU", 2, CurrencySpecStatus.ACTIVE, 1, publicLogo.fileAddress(), NOW);
        currencies.currency("XAG", 2, CurrencySpecStatus.ACTIVE, 1, privateLogo.fileAddress(), NOW);

        List<ReferenceCurrencyResponseBody> items = list("?codes=XAU,XAG,USD").getItems();

        assertEquals(List.of("USD", "XAG", "XAU"), codes(items));
        assertAll(() -> assertEquals(privateLogo.fileAddress(), items.get(1).getLogoFileAddress()),
            () -> assertNull(items.get(1).getLogoPublicUrl()),
            () -> assertEquals(publicLogo.fileAddress(), items.get(2).getLogoFileAddress()),
            () -> assertEquals(publicLogo.publicUrl(), items.get(2).getLogoPublicUrl()),
            () -> assertNull(items.getFirst().getLogoPublicUrl()));
    }

    /** 状态未知、编码格式不合法、空白项或超过 50 个编码：400。 */
    @Test
    void test_invalidParameter() {
        String fiftyOne = IntStream.range(0, 51).mapToObj(index -> "U" + (char) ('A' + index % 26) + "D")
            .collect(Collectors.joining(","));
        get(PATH + "?status=ENABLED").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(PATH + "?status=active").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(PATH + "?codes=US").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(PATH + "?codes=USD,,AED").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(PATH + "?codes=" + fiftyOne).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
    }

    /** 以内部调用方身份查询。 */
    private ReferenceCurrencyListResponseBody list(String query) {
        return get(PATH + query).assertOk().as(ReferenceCurrencyListResponseBody.class);
    }

    /** 币种代码序列。 */
    private static List<String> codes(List<ReferenceCurrencyResponseBody> items) {
        return items.stream().map(ReferenceCurrencyResponseBody::getCurrencyCode).toList();
    }

    /** 以内部调用方身份发送 GET。 */
    private PlatformResponseChecker get(String path) {
        String requestId = "plt-ref-ccy-" + UUID.randomUUID();
        return new PlatformResponseChecker(httpTestInvoker.get(path, TestRequestHeaders.internal(requestId)),
            objectMapper, requestId);
    }
}
