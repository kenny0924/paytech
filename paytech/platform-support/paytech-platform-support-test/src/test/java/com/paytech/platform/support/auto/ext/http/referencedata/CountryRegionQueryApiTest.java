package com.paytech.platform.support.auto.ext.http.referencedata;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
import com.paytech.platform.support.api.referencedata.CountryRegionQueryApi;
import com.paytech.platform.support.api.referencedata.response.CountryRegionListResponseBody;
import com.paytech.platform.support.api.referencedata.response.CountryRegionResponseBody;
import com.paytech.platform.support.test.base.MockTestBase;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.dal.ReferenceDataTestMapper;

/**
 * 全平台国家区域映射查询：经真实 Controller、Service、Repository、Mapper 读取本地 MySQL 的常驻种子（249 行）。
 * <li>映射只经初始化数据维护，用例不增删行；种子缺失时提示人工执行建表与种子语句。</li>
 *
 * @see CountryRegionQueryApi#listCountryRegions()
 */
class CountryRegionQueryApiTest extends MockTestBase {

    private static final String PATH = "/platform/v1/country-regions";

    private static final int SEED_ROWS = 249;

    private static final Set<String> REGIONS = Set.of("GCC", "MEA", "EUROPE", "NORTH_AMERICA", "LATAM", "APAC");

    private static final String SETUP = "；请在 mor_platform 人工执行 paytech/platform-support/docs/database/sql/changes/"
        + "2026-10-22-country-region.sql 与 platform-support-initial-data.sql 的「Country region seed」块"
        + "（或 paytech-platform-support-test/src/test/resources/init/ddl.sql 中同样的语句）";

    @Autowired
    ReferenceDataTestMapper referenceData;

    /** 表与 249 行种子已人工准备。 */
    @BeforeEach
    void setUpCountryRegions() {
        if (referenceData.countryRegionTableCount() != 1) {
            fail("缺少表 t_country_region" + SETUP);
        }
        assertEquals(SEED_ROWS, referenceData.countryRegionSeedCount(), "t_country_region 种子不完整" + SETUP);
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

    /** 以内部调用方身份读取全部映射。 */
    private CountryRegionListResponseBody list() {
        String requestId = "plt-cr-" + UUID.randomUUID();
        return new PlatformResponseChecker(httpTestInvoker.get(PATH, TestRequestHeaders.internal(requestId)),
            objectMapper, requestId).assertOk().as(CountryRegionListResponseBody.class);
    }
}
