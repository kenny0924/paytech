package com.paytech.platform.support.auto.ext.http.referencedata;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.platform.support.api.referencedata.IndustryQueryApi;
import com.paytech.platform.support.api.referencedata.response.IndustryListResponseBody;
import com.paytech.platform.support.api.referencedata.response.IndustryResponseBody;
import com.paytech.platform.support.test.base.MockTestBase;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.ReferenceDataMocker;

/**
 * 两级行业查询：经真实 Controller、Processor、Repository、Mapper 读取人工初始化的 10 个大类与 32 个细类。
 *
 * @see IndustryQueryApi#listIndustries(Integer, String, String, String)
 */
class IndustryQueryApiTest extends MockTestBase {

    private static final String PATH = "/platform/v1/reference-data/industries";

    private static final List<String> CATEGORIES = List.of("RETAIL", "DIGITAL_GOODS", "SOFTWARE_SAAS",
        "PROFESSIONAL_SERVICES", "EDUCATION", "TRAVEL", "FOOD_BEVERAGE", "MEDIA_ENTERTAINMENT", "NON_PROFIT", "OTHER");

    @Autowired
    ReferenceDataMocker referenceData;

    /** 五张参考数据表与完整种子已人工准备。 */
    @BeforeEach
    void setUpIndustries() {
        referenceData.assertSeeded();
    }

    /** 全部行业：大类按 sortNo 排列，每个大类之后紧跟其细类（细类按 sortNo）；细类带建议 MCC，大类没有。 */
    @Test
    void test_normalFlow() {
        List<IndustryResponseBody> items = list("").getItems();

        List<String> categories = new ArrayList<>();
        String currentCategory = null;
        for (IndustryResponseBody item : items) {
            if (item.getLevel() == 1) {
                currentCategory = item.getIndustryCode();
                categories.add(currentCategory);
                assertNull(item.getDefaultMccCode(), item.getIndustryCode());
                continue;
            }
            assertEquals(currentCategory, item.getParentIndustryCode(), item.getIndustryCode() + " 紧跟所属大类");
            assertTrue(item.getDefaultMccCode().matches("[0-9]{4}"), item.getIndustryCode() + " 带建议 MCC");
        }
        IndustryResponseBody apparel = items.get(1);
        assertAll(() -> assertEquals(42, items.size()), () -> assertEquals(CATEGORIES, categories),
            () -> assertEquals("RETAIL_APPAREL", apparel.getIndustryCode()),
            () -> assertEquals("Apparel & accessories", apparel.getName()),
            () -> assertEquals("5651", apparel.getDefaultMccCode()), () -> assertEquals(10, apparel.getSortNo()),
            () -> assertEquals(2, apparel.getLevel()), () -> assertTrue(apparel.getDescription() != null),
            () -> assertNull(apparel.getLogoPublicUrl()));
    }

    /** 层级、上级大类与编码筛选；不存在的编码不出现。 */
    @Test
    void test_filters() {
        assertAll(() -> assertEquals(CATEGORIES, codes(list("?level=1"))),
            () -> assertEquals(32, list("?level=2").getItems().size()),
            () -> assertEquals(List.of("RETAIL_APPAREL", "RETAIL_ELECTRONICS", "RETAIL_HOME", "RETAIL_BEAUTY",
                "RETAIL_GENERAL"), codes(list("?parentCode=retail"))),
            () -> assertEquals(List.of("RETAIL", "RETAIL_APPAREL", "OTHER"),
                codes(list("?codes=other,RETAIL_APPAREL,retail,UNKNOWN_CODE"))),
            () -> assertEquals(List.of(), codes(list("?level=1&parentCode=RETAIL"))));
    }

    /** 图标为公开文件时换出公开地址。 */
    @Test
    void test_logoPublicUrl() {
        var logo = referenceData.publicLogo();
        referenceData.industryLogo("RETAIL", logo.fileAddress());

        IndustryResponseBody retail = list("?codes=RETAIL").getItems().getFirst();

        assertAll(() -> assertEquals(logo.fileAddress(), retail.getLogoFileAddress()),
            () -> assertEquals(logo.publicUrl(), retail.getLogoPublicUrl()));
    }

    /** 层级不是 1 或 2、上级编码或编码格式不合法、状态未知：400。 */
    @Test
    void test_invalidParameter() {
        get(PATH + "?level=3").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(PATH + "?level=0").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(PATH + "?parentCode=RETAIL-X").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(PATH + "?codes=1RETAIL").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(PATH + "?status=LIVE").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
    }

    /** 行业编码序列。 */
    private static List<String> codes(IndustryListResponseBody body) {
        return body.getItems().stream().map(IndustryResponseBody::getIndustryCode).toList();
    }

    /** 以内部调用方身份查询。 */
    private IndustryListResponseBody list(String query) {
        return get(PATH + query).assertOk().as(IndustryListResponseBody.class);
    }

    /** 以内部调用方身份发送 GET。 */
    private PlatformResponseChecker get(String path) {
        String requestId = "plt-ref-ind-" + UUID.randomUUID();
        return new PlatformResponseChecker(httpTestInvoker.get(path, TestRequestHeaders.internal(requestId)),
            objectMapper, requestId);
    }
}
