package com.paytech.card.auto.binlibrary;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.binlibrary.CardBinLibraryApi;
import com.paytech.card.api.enums.BinLibraryVersionStatus;
import com.paytech.card.api.enums.CardBrand;
import com.paytech.card.api.enums.CardFundingType;
import com.paytech.card.api.enums.CardProduct;
import com.paytech.card.test.base.AbstractBinLibraryTest;
import com.paytech.foundation.common.error.CommonErrorCode;

/**
 * BIN 库版本号段列表（2026-10-27 号段列表需求 technique §3.1）：经 card 真实 HTTP 入口、只读服务与 mor_card 号段表分页计数，号段由本地固件直接插入。
 *
 * <p>
 * 覆盖：默认分页、前缀升序与总数、翻页；卡组织 / 借贷记 / 发卡国单项与组合筛选、前缀匹配（含 8 位）、无结果；只返回所查版本的号段，RETIRED 可浏览；
 * VALIDATING / INVALID 业务检查失败、版本不存在 404；非运营调用方 404、参数格式与分页条数非法 400。
 *
 * @see CardBinLibraryApi#listRanges(String, String, String, String, String, String, Integer, Integer)
 */
class BinLibraryRangeHttpTest extends AbstractBinLibraryTest {

    /** 默认分页 page=1、size=10：按前缀字符串升序，总数为全版本号段数；第 2 页取余下号段；号段属性完整返回，未提供的属性为 null。 */
    @Test
    void test_listRangesDefaultPaging() {
        long versionId = seedActiveVersion();
        for (int i = 0; i < 7; i++) {
            binLibrary.range(versionId, "62000" + i, CardBrand.UNIONPAY, "CN", "APAC");
        }

        var firstPage = ranges(1, "");
        var second = ranges(1, "?page=2");

        var first = json(firstPage);
        List<Map<String, Object>> items = items(firstPage);
        Map<String, Object> amex = items.getFirst();
        assertAll(() -> assertEquals(1, first.get("page")), () -> assertEquals(10, first.get("size")),
            () -> assertEquals(12, first.get("total")),
            () -> assertEquals(List.of("378282", "401288", "40128888", "411111", "555555", "620000", "620001", "620002",
                "620003", "620004"), prefixes(items)),
            () -> assertEquals(List.of("620005", "620006"), prefixes(items(second))),
            () -> assertEquals(12, json(second).get("total")),
            () -> assertEquals(6, amex.get("prefixLength")), () -> assertEquals(CardBrand.AMEX.getCode(), amex.get("cardBrand")),
            () -> assertEquals(CardFundingType.CHARGE.getCode(), amex.get("fundingType")), () -> assertNull(amex.get("cardLevel")),
            () -> assertEquals(CardProduct.CONSUMER.getCode(), amex.get("cardProduct")),
            () -> assertEquals("US", amex.get("issuerCountry")), () -> assertEquals("NORTH_AMERICA", amex.get("issuerRegion")),
            () -> assertNull(amex.get("issuerName")),
            () -> assertEquals(8, items.get(2).get("prefixLength")),
            () -> assertEquals("SYNTHETIC TEST ISSUER GB", items.get(2).get("issuerName")),
            () -> assertNull(items.get(5).get("fundingType")), () -> assertNull(items.get(5).get("cardProduct")));
    }

    /** 单项与组合筛选、前缀匹配（含 8 位完整前缀）与无结果；总数随筛选变化；其他版本的号段不混入，RETIRED 版本可浏览。 */
    @Test
    void test_listRangesFilters() {
        seedActiveVersion();
        long retired = binLibrary.version(2, BinLibraryVersionStatus.RETIRED, 1);
        binLibrary.range(retired, "411111", CardBrand.MASTERCARD, "AE", "GCC");
        binLibrary.version(3, BinLibraryVersionStatus.VALID, 0);

        var combined = ranges(1, "?cardBrand=" + CardBrand.VISA.getCode() + "&fundingType=" + CardFundingType.CREDIT.getCode()
            + "&issuerCountry=US");
        var noMatch = ranges(1, "?binPrefix=4&cardBrand=" + CardBrand.AMEX.getCode());

        assertAll(() -> assertEquals(List.of("401288", "40128888", "411111"),
            prefixes(items(ranges(1, "?cardBrand=" + CardBrand.VISA.getCode())))),
            () -> assertEquals(List.of("40128888"), prefixes(items(ranges(1, "?fundingType=" + CardFundingType.DEBIT.getCode())))),
            () -> assertEquals(List.of("40128888"), prefixes(items(ranges(1, "?issuerCountry=GB")))),
            () -> assertEquals(List.of("401288", "411111"), prefixes(items(combined))), () -> assertEquals(2, json(combined).get("total")),
            () -> assertEquals(List.of("401288", "40128888"), prefixes(items(ranges(1, "?binPrefix=4012")))),
            () -> assertEquals(List.of("40128888"), prefixes(items(ranges(1, "?binPrefix=40128888")))),
            () -> assertEquals(List.of("401288", "40128888", "411111"), prefixes(items(ranges(1, "?binPrefix=4")))),
            () -> assertEquals(200, noMatch.status()), () -> assertEquals(List.of(), items(noMatch)),
            () -> assertEquals(0, json(noMatch).get("total")),
            () -> assertEquals(List.of(CardBrand.MASTERCARD.getCode()),
                items(ranges(2, "")).stream().map(row -> row.get("cardBrand")).toList(), "RETIRED 版本只返回自己的号段"),
            () -> assertEquals(List.of(), items(ranges(3, "")), "VALID 空版本"));
    }

    /** 校验中或校验失败的版本不能浏览：400 业务检查失败并带版本与状态；版本不存在 404。 */
    @Test
    void test_listRangesVersionNotBrowsable() {
        binLibrary.version(1, BinLibraryVersionStatus.VALIDATING, 0);
        binLibrary.version(2, BinLibraryVersionStatus.INVALID, 3);

        var validating = ranges(1, "");
        var invalid = ranges(2, "");
        var missing = ranges(9, "");

        assertAll(() -> assertEquals(400, validating.status()),
            () -> assertEquals(CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), json(validating).get("code")),
            () -> assertTrue(String.valueOf(json(validating).get("detail")).contains("v1, 状态: " + BinLibraryVersionStatus.VALIDATING.getCode())),
            () -> assertEquals(400, invalid.status()),
            () -> assertEquals(CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), json(invalid).get("code")),
            () -> assertTrue(String.valueOf(json(invalid).get("detail")).contains("v2, 状态: " + BinLibraryVersionStatus.INVALID.getCode())),
            () -> assertEquals(404, missing.status()),
            () -> assertEquals(CommonErrorCode.NOT_FOUND.getCode(), json(missing).get("code")));
    }

    /** 非运营调用方 404；版本号、前缀、卡组织、借贷记、发卡国格式错误及分页条数非法 400 INVALID_REQUEST。 */
    @Test
    void test_listRangesParameterRules() {
        seedActiveVersion();

        var wrongCaller = callWithActor(HttpMethod.GET, BASE + "/versions/1/ranges", CardApiConstants.PAYMENT_SERVICE, null, null);
        var invalid = List.of(ranges(1, "?size=7"), ranges(1, "?page=0"), ranges(1, "?binPrefix=411111111"),
            ranges(1, "?binPrefix=41a"), ranges(1, "?binPrefix=4%25"), ranges(1, "?cardBrand=visa"),
            ranges(1, "?fundingType=DEBITCARD"), ranges(1, "?issuerCountry=us"), ops(HttpMethod.GET, "/versions/0/ranges", null));

        assertAll(() -> assertEquals(404, wrongCaller.status()),
            () -> assertEquals(List.of(400, 400, 400, 400, 400, 400, 400, 400, 400), invalid.stream().map(Response::status).toList()),
            () -> assertTrue(invalid.stream().allMatch(response -> CommonErrorCode.INVALID_REQUEST.getCode().equals(json(response).get("code")))));
    }

    /** 以运营身份列出某版本号段，query 以 ? 开头或为空。 */
    Response ranges(int versionNo, String query) {
        return ops(HttpMethod.GET, "/versions/" + versionNo + "/ranges" + query, null);
    }

    /** 各行的号段前缀。 */
    static List<Object> prefixes(List<Map<String, Object>> items) {
        return items.stream().map(row -> row.get("binPrefix")).toList();
    }
}
