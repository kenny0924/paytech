package com.paytech.platform.support.auto.ext.http.referencedata;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.api.referencedata.ReferenceCountryQueryApi;
import com.paytech.platform.support.api.referencedata.response.ReferenceCountryListResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCountryResponseBody;
import com.paytech.platform.support.test.base.MockTestBase;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.ReferenceDataMocker;

/**
 * 参考数据国家查询：经真实 Controller、Processor、Repository、Mapper 读取人工初始化的 249 行种子与文件元数据。
 *
 * @see ReferenceCountryQueryApi#listCountries(String, String)
 */
class ReferenceCountryQueryApiTest extends MockTestBase {

    private static final String PATH = "/platform/v1/reference-data/countries";

    @Autowired
    ReferenceDataMocker referenceData;

    /** 五张参考数据表与完整种子已人工准备。 */
    @BeforeEach
    void setUpCountries() {
        referenceData.assertSeeded();
    }

    /** 全部国家按代码升序，带 ISO 三位码、数字码、英文名、区号与法定货币；没有区号或法定货币的为空。 */
    @Test
    void test_normalFlow() {
        List<ReferenceCountryResponseBody> items = list("").getItems();
        Map<String, ReferenceCountryResponseBody> byCode = items.stream()
            .collect(Collectors.toMap(ReferenceCountryResponseBody::getCountryCode, Function.identity()));

        List<String> codes = items.stream().map(ReferenceCountryResponseBody::getCountryCode).toList();
        ReferenceCountryResponseBody ae = byCode.get("AE");
        ReferenceCountryResponseBody aq = byCode.get("AQ");
        assertAll(() -> assertEquals(249, items.size()), () -> assertEquals(codes.stream().sorted().toList(), codes),
            () -> assertEquals("ARE", ae.getAlpha3Code()), () -> assertEquals("784", ae.getNumericCode()),
            () -> assertEquals("United Arab Emirates", ae.getName()), () -> assertEquals("GCC", ae.getRegionCode()),
            () -> assertEquals("971", ae.getCallingCode()), () -> assertEquals("AED", ae.getDefaultCurrencyCode()),
            () -> assertEquals(ReferenceDataStatus.ACTIVE.getCode(), ae.getStatus()),
            () -> assertEquals(1, ae.getVersion()), () -> assertNull(ae.getLogoFileAddress()),
            () -> assertEquals("010", aq.getNumericCode()), () -> assertNull(aq.getCallingCode()),
            () -> assertNull(aq.getDefaultCurrencyCode()),
            () -> assertTrue(items.stream().allMatch(item -> item.getAlpha3Code().matches("[A-Z]{3}")
                && item.getNumericCode().matches("[0-9]{3}") && !item.getName().isBlank()), "ISO 编码与英文名完整"));
    }

    /** 状态与编码同时生效；编码去空白、转大写，不存在的编码不出现。 */
    @Test
    void test_statusAndCodes() {
        referenceData.countryStatus("AQ", ReferenceDataStatus.INACTIVE);

        assertAll(() -> assertEquals(248, list("?status=ACTIVE").getItems().size()),
            () -> assertEquals(List.of("AQ"), codes(list("?status=INACTIVE"))),
            () -> assertEquals(List.of("AE", "SA"), codes(list("?codes=sa, ae,ZZ"))),
            () -> assertEquals(List.of("EG"), codes(list("?status=ACTIVE&codes=EG,AQ"))));
    }

    /** 一次查询中公开国旗换出公开地址，私有文件与没有国旗的国家公开地址为空。 */
    @Test
    void test_logoPublicUrl() {
        var publicLogo = referenceData.publicLogo();
        var privateLogo = referenceData.privateLogo();
        referenceData.countryLogo("AE", publicLogo.fileAddress());
        referenceData.countryLogo("SA", privateLogo.fileAddress());

        List<ReferenceCountryResponseBody> items = list("?codes=AE,EG,SA").getItems();

        assertAll(() -> assertEquals(publicLogo.fileAddress(), items.get(0).getLogoFileAddress()),
            () -> assertEquals(publicLogo.publicUrl(), items.get(0).getLogoPublicUrl()),
            () -> assertNull(items.get(1).getLogoFileAddress()), () -> assertNull(items.get(1).getLogoPublicUrl()),
            () -> assertEquals(privateLogo.fileAddress(), items.get(2).getLogoFileAddress()),
            () -> assertNull(items.get(2).getLogoPublicUrl()));
    }

    /** 状态未知、编码格式不合法：400。 */
    @Test
    void test_invalidParameter() {
        get(PATH + "?status=DELETED").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(PATH + "?codes=ARE").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(PATH + "?codes=A1").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
    }

    /** 国家代码序列。 */
    private static List<String> codes(ReferenceCountryListResponseBody body) {
        return body.getItems().stream().map(ReferenceCountryResponseBody::getCountryCode).toList();
    }

    /** 以内部调用方身份查询。 */
    private ReferenceCountryListResponseBody list(String query) {
        return get(PATH + query).assertOk().as(ReferenceCountryListResponseBody.class);
    }

    /** 以内部调用方身份发送 GET。 */
    private PlatformResponseChecker get(String path) {
        String requestId = "plt-ref-cty-" + UUID.randomUUID();
        return new PlatformResponseChecker(httpTestInvoker.get(path, TestRequestHeaders.internal(requestId)),
            objectMapper, requestId);
    }
}
