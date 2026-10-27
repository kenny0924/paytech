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
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.api.referencedata.response.CountrySubdivisionListResponseBody;
import com.paytech.platform.support.api.referencedata.response.CountrySubdivisionResponseBody;
import com.paytech.platform.support.api.referencedata.response.IndustryListResponseBody;
import com.paytech.platform.support.api.referencedata.response.IndustryResponseBody;
import com.paytech.platform.support.api.referencedata.response.MccResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCountryListResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCountryResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCurrencyListResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCurrencyResponseBody;
import com.paytech.platform.support.test.base.MockTestBase;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.ReferenceDataMocker;

import tools.jackson.core.type.TypeReference;

/**
 * 初始数据完整性（AC-04、AC-05）：经五个查询接口读取人工初始化的种子，核对编码完整、跨表引用存在且可用。
 * <li>初始数据由需求目录的生成脚本产出；本类守住生成结果与执行结果的一致，不复制 SQL。</li>
 */
class ReferenceDataSeedIntegrityTest extends MockTestBase {

    private static final String BASE = "/platform/v1/reference-data";

    private static final TypeReference<PagingResponseBody<MccResponseBody>> PAGE = new TypeReference<>() {
    };

    /** 至少应标记为卡组织高风险的 MCC（Visa 高品牌风险与 Mastercard 特殊商户登记公布的类目）。 */
    private static final Set<String> REQUIRED_HIGH_RISK = Set.of("5122", "5912", "5962", "5966", "5967", "7995", "6051");

    @Autowired
    ReferenceDataMocker referenceData;

    /** 五张参考数据表与完整种子已人工准备。 */
    @BeforeEach
    void setUpSeeds() {
        referenceData.assertSeeded();
    }

    /** 每个细类的建议 MCC 都存在且 ACTIVE；每个细类的上级是存在的大类。 */
    @Test
    void test_subIndustryDefaultMccActive() {
        List<IndustryResponseBody> industries = get(BASE + "/industries").as(IndustryListResponseBody.class).getItems();
        Set<String> categories = industries.stream().filter(item -> item.getLevel() == 1)
            .map(IndustryResponseBody::getIndustryCode).collect(Collectors.toSet());
        List<IndustryResponseBody> subIndustries = industries.stream().filter(item -> item.getLevel() == 2).toList();
        String defaultMccs = subIndustries.stream().map(IndustryResponseBody::getDefaultMccCode).distinct()
            .collect(Collectors.joining(","));

        Set<String> activeMccs = get(BASE + "/mccs?status=ACTIVE&size=50&codes=" + defaultMccs).as(PAGE).items()
            .stream().map(MccResponseBody::getMccCode).collect(Collectors.toSet());

        assertAll(() -> assertEquals(10, categories.size()), () -> assertEquals(32, subIndustries.size()),
            () -> assertTrue(subIndustries.stream().allMatch(item -> categories.contains(item.getParentIndustryCode())),
                "细类的上级都是大类"),
            () -> assertEquals(Set.of(defaultMccs.split(",")), activeMccs, "建议 MCC 都存在且 ACTIVE"));
    }

    /** 249 个国家都有三位码、数字码与英文名；法定货币为三位大写代码或为空。 */
    @Test
    void test_countriesComplete() {
        List<ReferenceCountryResponseBody> countries = get(BASE + "/countries")
            .as(ReferenceCountryListResponseBody.class).getItems();

        assertAll(() -> assertEquals(249, countries.size()),
            () -> assertEquals(249, countries.stream().map(ReferenceCountryResponseBody::getAlpha3Code).distinct()
                .count(), "三位码唯一"),
            () -> assertTrue(countries.stream().allMatch(item -> item.getNumericCode().matches("[0-9]{3}")
                && !item.getName().isBlank()), "数字码与英文名"),
            () -> assertTrue(countries.stream().allMatch(item -> item.getDefaultCurrencyCode() == null
                || item.getDefaultCurrencyCode().matches("[A-Z]{3}")), "法定货币代码格式"),
            () -> assertTrue(countries.stream().allMatch(item -> item.getCallingCode() == null
                || item.getCallingCode().matches("[0-9]{1,4}")), "区号只含数字"));
    }

    /** 行政区 AE 7、SA 13、EG 27，编码前缀与所属国家一致；入驻要用的币种都 ACTIVE。 */
    @Test
    void test_subdivisionsAndCurrencies() {
        List<CountrySubdivisionResponseBody> subdivisions = get(BASE + "/country-subdivisions")
            .as(CountrySubdivisionListResponseBody.class).getItems();
        Map<String, Long> perCountry = subdivisions.stream()
            .collect(Collectors.groupingBy(CountrySubdivisionResponseBody::getCountryCode, Collectors.counting()));
        Map<String, String> currencies = get(BASE + "/currencies?codes=USD,AED,SAR,EGP")
            .as(ReferenceCurrencyListResponseBody.class).getItems().stream()
            .collect(Collectors.toMap(ReferenceCurrencyResponseBody::getCurrencyCode,
                ReferenceCurrencyResponseBody::getStatus));
        Set<String> highRisk = get(BASE + "/mccs?size=50&codes=" + String.join(",", REQUIRED_HIGH_RISK)).as(PAGE)
            .items().stream().filter(MccResponseBody::getNetworkHighRisk).map(MccResponseBody::getMccCode)
            .collect(Collectors.toSet());

        assertAll(() -> assertEquals(Map.of("AE", 7L, "SA", 13L, "EG", 27L), perCountry),
            () -> assertTrue(subdivisions.stream().allMatch(item -> item.getSubdivisionCode()
                .startsWith(item.getCountryCode() + "-")), "编码前缀与国家一致"),
            () -> assertEquals(Set.of("USD", "AED", "SAR", "EGP").stream()
                .collect(Collectors.toMap(Function.identity(), code -> ReferenceDataStatus.ACTIVE.getCode())), currencies),
            () -> assertEquals(REQUIRED_HIGH_RISK, highRisk, "高风险 MCC 标记"));
    }

    /** 以内部调用方身份读取并要求 200。 */
    private PlatformResponseChecker get(String path) {
        String requestId = "plt-ref-seed-" + UUID.randomUUID();
        return new PlatformResponseChecker(httpTestInvoker.get(path, TestRequestHeaders.internal(requestId)),
            objectMapper, requestId).assertOk();
    }
}
