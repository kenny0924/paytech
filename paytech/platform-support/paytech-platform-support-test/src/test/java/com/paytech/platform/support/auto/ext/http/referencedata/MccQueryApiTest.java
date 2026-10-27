package com.paytech.platform.support.auto.ext.http.referencedata;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.api.referencedata.MccQueryApi;
import com.paytech.platform.support.api.referencedata.response.MccResponseBody;
import com.paytech.platform.support.test.base.MockTestBase;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.ReferenceDataMocker;

import tools.jackson.core.type.TypeReference;

/**
 * MCC 分页搜索：经真实 Controller、Processor、Repository、Mapper 在数据库执行 LIMIT/OFFSET 与 COUNT。
 *
 * @see MccQueryApi#searchMccs(String, String, String, Integer, Integer)
 */
class MccQueryApiTest extends MockTestBase {

    private static final String PATH = "/platform/v1/reference-data/mccs";

    private static final TypeReference<PagingResponseBody<MccResponseBody>> PAGE = new TypeReference<>() {
    };

    @Autowired
    ReferenceDataMocker referenceData;

    private long total;

    /** 五张参考数据表与完整种子已人工准备，记下种子总数。 */
    @BeforeEach
    void setUpMccs() {
        referenceData.assertSeeded();
        total = search("?size=10").total();
    }

    /** 默认第 1 页 10 条，按编码升序，空页保留总数；字段与卡组织高风险标记正确。 */
    @Test
    void test_normalFlow() {
        var first = search("");
        long lastPage = (total + 199) / 200;
        var beyond = search("?page=" + (lastPage + 1) + "&size=200");
        MccResponseBody grocery = search("?codes=5411").items().getFirst();
        MccResponseBody betting = search("?codes=7995").items().getFirst();

        assertAll(() -> assertTrue(total > 250, "通用 MCC 约 300 条"), () -> assertEquals(1, first.page()),
            () -> assertEquals(10, first.size()), () -> assertEquals(10, first.items().size()),
            () -> assertEquals(codes(first).stream().sorted().toList(), codes(first)),
            () -> assertEquals("0742", codes(first).getFirst()),
            () -> assertTrue(beyond.items().isEmpty()), () -> assertEquals(total, beyond.total()),
            () -> assertEquals("Grocery Stores, Supermarkets", grocery.getName()),
            () -> assertFalse(grocery.getNetworkHighRisk()), () -> assertTrue(betting.getNetworkHighRisk()),
            () -> assertEquals(ReferenceDataStatus.ACTIVE.getCode(), grocery.getStatus()),
            () -> assertEquals(1, grocery.getVersion()));
    }

    /** 相邻两页不重叠且衔接，与一次取 20 条一致。 */
    @Test
    void test_paging() {
        var page1 = search("?page=1&size=10");
        var page2 = search("?page=2&size=10");
        var both = search("?page=1&size=20");

        List<String> joined = new java.util.ArrayList<>(codes(page1));
        joined.addAll(codes(page2));
        assertAll(() -> assertEquals(codes(both), joined), () -> assertEquals(total, page2.total()));
    }

    /** 关键字匹配编码前缀或英文名包含（不区分大小写）；LIKE 通配符按字面匹配。 */
    @Test
    void test_keyword() {
        var prefix = search("?keyword=541&size=50");
        var name = search("?keyword=GROCERY&size=50");
        var wildcard = search("?keyword=%&size=50");

        assertAll(() -> assertTrue(codes(prefix).contains("5411")),
            () -> assertTrue(codes(prefix).stream().allMatch(code -> code.startsWith("541"))),
            () -> assertEquals(List.of("5411"), codes(name)), () -> assertEquals(0, wildcard.total()),
            () -> assertEquals(name.items().size(), name.total()));
    }

    /** 编码与状态同时生效；不存在的编码不出现。 */
    @Test
    void test_statusAndCodes() {
        referenceData.mccStatus("5999", ReferenceDataStatus.INACTIVE);

        assertAll(() -> assertEquals(List.of("5411", "5999"), codes(search("?codes=5999,5411,0000"))),
            () -> assertEquals(List.of("5999"), codes(search("?status=INACTIVE"))),
            () -> assertEquals(total - 1, search("?status=ACTIVE").total()),
            () -> assertEquals(List.of("5411"), codes(search("?status=ACTIVE&codes=5411,5999"))));
    }

    /** 编码格式、状态、页码、每页条数或关键字长度不合法：400。 */
    @Test
    void test_invalidParameter() {
        get(PATH + "?codes=541").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(PATH + "?codes=54A1").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(PATH + "?status=ALL").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(PATH + "?page=0").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(PATH + "?size=15").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(PATH + "?keyword=" + "x".repeat(65)).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
    }

    /** 本页 MCC 编码序列。 */
    private static List<String> codes(PagingResponseBody<MccResponseBody> page) {
        return page.items().stream().map(MccResponseBody::getMccCode).toList();
    }

    /** 以内部调用方身份搜索。 */
    private PagingResponseBody<MccResponseBody> search(String query) {
        return get(PATH + query).assertOk().as(PAGE);
    }

    /** 以内部调用方身份发送 GET。 */
    private PlatformResponseChecker get(String path) {
        String requestId = "plt-ref-mcc-" + UUID.randomUUID();
        return new PlatformResponseChecker(httpTestInvoker.get(path, TestRequestHeaders.internal(requestId)),
            objectMapper, requestId);
    }
}
