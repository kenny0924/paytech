package com.paytech.platform.support.auto.ext.http.referencedata;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.api.referencedata.CountryRegionQueryApi;
import com.paytech.platform.support.api.referencedata.response.CountryRegionListResponseBody;
import com.paytech.platform.support.api.referencedata.response.CountryRegionResponseBody;
import com.paytech.platform.support.test.base.MockTestBase;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.ReferenceDataMocker;

/**
 * 全平台国家区域映射查询：经真实 Controller、Service、Repository、Mapper 读取本地 MySQL 的常驻种子（249 行）。
 * <li>映射只经初始化数据维护，用例不增删行；种子缺失时提示人工执行建表与种子语句。表 2026-10-27 起为 t_ref_country。</li>
 *
 * @see CountryRegionQueryApi#listCountryRegions()
 */
class CountryRegionQueryApiTest extends MockTestBase {

    private static final String PATH = "/platform/v1/country-regions";

    private static final int SEED_ROWS = 249;

    private static final Set<String> REGIONS = Set.of("GCC", "MEA", "EUROPE", "NORTH_AMERICA", "LATAM", "APAC");

    @Autowired
    ReferenceDataMocker referenceData;

    /** 参考数据表与 249 行国家种子已人工准备。 */
    @BeforeEach
    void setUpCountryRegions() {
        referenceData.assertSeeded();
    }

    /** 全部映射按国家代码升序且不重复，区域取值都在六个平台区域内，海合会六国为 GCC。 */
    @Test
    void test_normalFlow() {
        List<CountryRegionResponseBody> items = list().getItems();

        List<String> codes = items.stream().map(CountryRegionResponseBody::getCountryCode).toList();
        assertAll(() -> assertEquals(SEED_ROWS, items.size()),
            () -> assertEquals(codes.stream().sorted().distinct().toList(), codes, "国家代码升序且唯一"),
            () -> assertTrue(codes.stream().allMatch(code -> code.matches("[A-Z]{2}")), "ISO alpha-2"),
            () -> assertTrue(items.stream().allMatch(item -> REGIONS.contains(item.getRegionCode())), "区域取值"),
            () -> assertEquals(Set.of("AE", "BH", "KW", "OM", "QA", "SA"), items.stream()
                .filter(item -> "GCC".equals(item.getRegionCode())).map(CountryRegionResponseBody::getCountryCode)
                .collect(Collectors.toSet()), "GCC 只含海合会六国"));
    }

    /** 各区域的代表国家与种子注释写明的划分惯例。 */
    @Test
    void test_edgeCase_regionConventions() {
        Map<String, String> regions = list().getItems().stream()
            .collect(Collectors.toMap(CountryRegionResponseBody::getCountryCode, CountryRegionResponseBody::getRegionCode));

        Map<String, String> expected = Map.of("AE", "GCC", "EG", "MEA", "NG", "MEA", "GB", "EUROPE", "TR", "EUROPE",
            "US", "NORTH_AMERICA", "GL", "NORTH_AMERICA", "MX", "LATAM", "CN", "APAC", "KZ", "APAC");

        assertEquals(expected, expected.keySet().stream().filter(regions::containsKey)
            .collect(Collectors.toMap(Function.identity(), regions::get)));
    }

    /**
     * 旧接口响应冻结（S-01）：严格 JSON 的消费方（card）不能遇到新字段
     * <li>外层只有 items，元素只有 countryCode、regionCode 且顺序不变；停用的国家照常返回（旧接口不区分状态）。</li>
     */
    @Test
    void test_responseShapeUnchanged() {
        referenceData.countryStatus("AQ", ReferenceDataStatus.INACTIVE);
        String requestId = "plt-cr-" + UUID.randomUUID();
        var body = new PlatformResponseChecker(httpTestInvoker.get(PATH, TestRequestHeaders.internal(requestId)),
            objectMapper, requestId).assertOk().json();

        assertAll(() -> assertEquals(List.of("items"), List.copyOf(body.propertyNames())),
            () -> assertEquals(SEED_ROWS, body.path("items").size()),
            () -> body.path("items").valueStream().forEach(item -> assertEquals(List.of("countryCode", "regionCode"),
                List.copyOf(item.propertyNames()))));
    }

    /** 以内部调用方身份读取全部映射。 */
    private CountryRegionListResponseBody list() {
        String requestId = "plt-cr-" + UUID.randomUUID();
        return new PlatformResponseChecker(httpTestInvoker.get(PATH, TestRequestHeaders.internal(requestId)),
            objectMapper, requestId).assertOk().as(CountryRegionListResponseBody.class);
    }
}
