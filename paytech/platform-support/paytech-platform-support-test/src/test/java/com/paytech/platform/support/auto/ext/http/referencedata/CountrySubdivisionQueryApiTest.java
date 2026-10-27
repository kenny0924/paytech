package com.paytech.platform.support.auto.ext.http.referencedata;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.api.referencedata.CountrySubdivisionQueryApi;
import com.paytech.platform.support.api.referencedata.response.CountrySubdivisionListResponseBody;
import com.paytech.platform.support.api.referencedata.response.CountrySubdivisionResponseBody;
import com.paytech.platform.support.test.base.MockTestBase;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.ReferenceDataMocker;

/**
 * 行政区查询：经真实 Controller、Processor、Repository、Mapper 读取人工初始化的 AE / SA / EG 种子。
 *
 * @see CountrySubdivisionQueryApi#listSubdivisions(String, String, String)
 */
class CountrySubdivisionQueryApiTest extends MockTestBase {

    private static final String PATH = "/platform/v1/reference-data/country-subdivisions";

    @Autowired
    ReferenceDataMocker referenceData;

    /** 五张参考数据表与完整种子已人工准备。 */
    @BeforeEach
    void setUpSubdivisions() {
        referenceData.assertSeeded();
    }

    /** 按国家筛选：AE 7 个酋长国、SA 13 个地区、EG 27 个省，按代码升序；字段与 ISO 3166-2 一致。 */
    @Test
    void test_normalFlow() {
        List<CountrySubdivisionResponseBody> ae = list("?countryCode=AE").getItems();
        CountrySubdivisionResponseBody dubai = ae.stream().filter(item -> "AE-DU".equals(item.getSubdivisionCode()))
            .findFirst().orElseThrow();

        assertAll(() -> assertEquals(7, ae.size()), () -> assertEquals(codes(ae).stream().sorted().toList(), codes(ae)),
            () -> assertEquals(13, list("?countryCode=SA").getItems().size()),
            () -> assertEquals(27, list("?countryCode=eg").getItems().size()),
            () -> assertEquals(47, list("").getItems().size()),
            () -> assertEquals("AE", dubai.getCountryCode()), () -> assertEquals("EMIRATE", dubai.getCategory()),
            () -> assertEquals("Dubayy", dubai.getName()), () -> assertEquals("امارة دبي", dubai.getLocalName()),
            () -> assertNull(dubai.getParentSubdivisionCode()),
            () -> assertEquals(ReferenceDataStatus.ACTIVE.getCode(), dubai.getStatus()),
            () -> assertEquals(1, dubai.getVersion()));
    }

    /** 没有收录行政区的国家返回空列表。 */
    @Test
    void test_noDataFound() {
        assertTrue(list("?countryCode=US").getItems().isEmpty());
    }

    /** 国家、状态与编码同时生效；不存在的编码不出现。 */
    @Test
    void test_statusAndCodes() {
        referenceData.subdivisionStatus("EG-C", ReferenceDataStatus.INACTIVE);

        assertAll(() -> assertEquals(26, list("?countryCode=EG&status=ACTIVE").getItems().size()),
            () -> assertEquals(List.of("EG-C"), codes(list("?status=INACTIVE").getItems())),
            () -> assertEquals(List.of("AE-DU", "EG-C"), codes(list("?codes=eg-c,AE-DU,SA-13").getItems())),
            () -> assertEquals(List.of("AE-DU"), codes(list("?countryCode=AE&codes=AE-DU,EG-C").getItems())));
    }

    /** 国家代码、状态或编码格式不合法：400。 */
    @Test
    void test_invalidParameter() {
        get(PATH + "?countryCode=ARE").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(PATH + "?codes=AE_DU").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(PATH + "?codes=AE-DUBA").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(PATH + "?status=OPEN").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
    }

    /** 行政区代码序列。 */
    private static List<String> codes(List<CountrySubdivisionResponseBody> items) {
        return items.stream().map(CountrySubdivisionResponseBody::getSubdivisionCode).toList();
    }

    /** 以内部调用方身份查询。 */
    private CountrySubdivisionListResponseBody list(String query) {
        return get(PATH + query).assertOk().as(CountrySubdivisionListResponseBody.class);
    }

    /** 以内部调用方身份发送 GET。 */
    private PlatformResponseChecker get(String path) {
        String requestId = "plt-ref-sub-" + UUID.randomUUID();
        return new PlatformResponseChecker(httpTestInvoker.get(path, TestRequestHeaders.internal(requestId)),
            objectMapper, requestId);
    }
}
