package com.paytech.billing.auto.ext.http.fact;

import static com.paytech.billing.test.builder.BillingFactRequestBuilder.CLIENT_ID;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.CURRENCY;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.ENTITY_NO;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.MAKER_ID;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.OTHER_CLIENT_ID;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.OTHER_ENTITY_NO;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.billing.api.BillingFactQueryApi;
import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.test.base.AbstractBillingFactApiTest;
import com.paytech.billing.test.builder.BillingFactRequestBuilder;
import com.paytech.billing.test.checker.BillingResponseChecker;
import com.paytech.foundation.web.paging.PagingRequestBody;

import tools.jackson.databind.JsonNode;

/**
 * 计费事实管理列表：真实 HTTP 入口按 OE + 币种与可选条件分页检索任意状态事实头
 * <li>覆盖草稿、已批准、已替代、已作废四类状态与 Client、状态、事实类型、登记时间筛选，factId 降序与 LIMIT/OFFSET 分页总数，以及参数错误。</li>
 *
 * @see BillingFactQueryApi#searchBillingFacts(String, String, String, String, String, java.time.Instant,
 *      java.time.Instant, Integer, Integer)
 */
class BillingFactSearchTest extends AbstractBillingFactApiTest {

    /** 四类状态都在列表中且按 factId 降序；状态、Client 与事实类型筛选正确；其他 OE 的事实不出现；摘要字段与金额精度。 */
    @Test
    void test_filtersIncludeDrafts() {
        long superseded = submitApprovedAndPublished(BillingFactRequestBuilder.template());
        long approved = submitApprovedAndPublished(BillingFactRequestBuilder.template().supersedes(superseded));
        long voided = submitDraft(BillingFactRequestBuilder.template());
        voidFact(voided, MAKER_ID).assertSuccess(200);
        long otherClientDraft = submitDraft(BillingFactRequestBuilder.template().client(OTHER_CLIENT_ID).clearLines()
            .addLine(FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT,
                OTHER_CLIENT_ID, "3.5", FactLineDirection.CHARGE));
        submitDraft(BillingFactRequestBuilder.template().entityNo(OTHER_ENTITY_NO).clearLines().addLine(
            FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, OTHER_ENTITY_NO, FactPartyRole.CLIENT,
            CLIENT_ID, "1.00", FactLineDirection.CHARGE));

        List<JsonNode> all = search("").assertSuccess(200).assertPage(PagingRequestBody.DEFAULT_PAGE,
            PagingRequestBody.DEFAULT_SIZE, 4);
        JsonNode draft = all.get(0);
        assertAll(() -> assertEquals(ids(otherClientDraft, voided, approved, superseded), factIds(all)),
            () -> assertEquals(BillingFactType.MANUAL_ADJUSTMENT.getCode(), draft.path("factType").asString()),
            () -> assertEquals(BillingFactStatus.DRAFT.getCode(), draft.path("status").asString()),
            () -> assertEquals(OTHER_CLIENT_ID, draft.path("clientId").asString()),
            () -> assertEquals("3.50", draft.path("grossAmount").asString()),
            () -> assertEquals(MAKER_ID, draft.path("makerId").asString()),
            () -> assertTrue(absent(draft.path("checkerId")), "草稿无复核人"),
            () -> assertTrue(absent(draft.path("approvedTime")), "草稿无批准时间"),
            () -> assertFalse(absent(draft.path("sourceReference")), "应返回来源引用"),
            () -> assertFalse(absent(draft.path("createTime")), "应返回登记时间"),
            () -> assertEquals(CHECKER_ID, all.get(2).path("checkerId").asString()),
            () -> assertFalse(absent(all.get(2).path("approvedTime")), "已批准事实应返回批准时间"));

        assertAll(() -> assertEquals(ids(otherClientDraft), statusIds(BillingFactStatus.DRAFT)),
            () -> assertEquals(ids(approved), statusIds(BillingFactStatus.APPROVED)),
            () -> assertEquals(ids(superseded), statusIds(BillingFactStatus.SUPERSEDED)),
            () -> assertEquals(ids(voided), statusIds(BillingFactStatus.VOIDED)),
            () -> assertEquals(ids(voided, approved, superseded), searchIds("&clientId=" + CLIENT_ID)),
            () -> assertEquals(4, searchIds("&factType=" + BillingFactType.MANUAL_ADJUSTMENT.getCode()).size()),
            () -> assertEquals(List.of(), searchIds("&factType=" + BillingFactType.COST_ADJUSTMENT.getCode())));
    }

    /** 分页：11 条草稿按 size 10 分两页，总数在两页一致，第 2 页为最早一条；越界页为空页仍返回总数。 */
    @Test
    void test_pagination() {
        List<String> submitted = new ArrayList<>();
        for (int index = 0; index < PagingRequestBody.DEFAULT_SIZE + 1; index++) {
            submitted.add(Long.toString(submitDraft(BillingFactRequestBuilder.template())));
        }

        List<JsonNode> first = search("&page=1&size=10").assertSuccess(200).assertPage(1, 10, 11);
        List<JsonNode> second = search("&page=2&size=10").assertSuccess(200).assertPage(2, 10, 11);
        List<JsonNode> beyond = search("&page=3&size=10").assertSuccess(200).assertPage(3, 10, 11);

        List<String> collected = new ArrayList<>(factIds(first));
        collected.addAll(factIds(second));
        assertAll(() -> assertEquals(10, first.size()), () -> assertEquals(List.of(submitted.getFirst()), factIds(second)),
            () -> assertEquals(submitted.reversed(), collected), () -> assertEquals(List.of(), beyond));
    }

    /** 登记时间区间左闭右开：下限取第二条的登记时间时含第二、三条，上限取它时只含第一条。 */
    @Test
    void test_createdTimeRange() {
        long first = submitDraft(BillingFactRequestBuilder.template());
        long second = submitDraft(BillingFactRequestBuilder.template());
        long third = submitDraft(BillingFactRequestBuilder.template());
        String secondCreated = search("").assertSuccess(200).assertPage(1, 10, 3).get(1).path("createTime").asString();

        assertAll(() -> assertEquals(ids(third, second), searchIds("&createdFrom=" + secondCreated)),
            () -> assertEquals(ids(first), searchIds("&createdTo=" + secondCreated)),
            () -> assertEquals(ids(second), searchIds("&createdFrom=" + secondCreated + "&createdTo="
                + factCreateTimeOf(third))));
    }

    /** 缺少必填主体、格式不符、状态或类型不在目录、条数不在选项内、时间区间颠倒均为参数错误。 */
    @Test
    void test_invalidParameter() {
        get(PATH + "/search?currency=" + CURRENCY).assertInvalidRequest();
        get(PATH + "/search?entityNo=AE000&currency=" + CURRENCY).assertInvalidRequest();
        get(PATH + "/search?entityNo=" + ENTITY_NO + "&currency=usd").assertInvalidRequest();
        search("&status=REJECTED").assertInvalidRequest();
        search("&factType=UNKNOWN_FACT").assertInvalidRequest();
        search("&size=15").assertInvalidRequest();
        search("&createdFrom=2026-10-20T00:00:00Z&createdTo=2026-10-20T00:00:00Z").assertInvalidRequest();
        search("").assertSuccess(200).assertPage(1, 10, 0);
    }

    /** 标准 OE + 币种检索，附加查询串。 */
    BillingResponseChecker search(String query) {
        return get(PATH + "/search?entityNo=" + ENTITY_NO + "&currency=" + CURRENCY + query);
    }

    /** 检索并取本页 factId 列表。 */
    List<String> searchIds(String query) {
        return factIds(search(query).assertSuccess(200).json().path("items"));
    }

    /** 按状态检索的 factId 列表。 */
    List<String> statusIds(BillingFactStatus status) {
        return searchIds("&status=" + status.getCode());
    }

    /** 列表中某事实的登记时间。 */
    String factCreateTimeOf(long factId) {
        for (JsonNode item : search("").assertSuccess(200).json().path("items")) {
            if (item.path("factId").asString().equals(Long.toString(factId))) {
                return item.path("createTime").asString();
            }
        }
        throw new AssertionError("列表中没有事实: " + factId);
    }

    /** 一组元素的 factId。 */
    static List<String> factIds(Iterable<JsonNode> items) {
        List<String> ids = new ArrayList<>();
        items.forEach(item -> ids.add(item.path("factId").asString()));
        return ids;
    }

    /** JSON 字段缺失或为 null。 */
    static boolean absent(JsonNode node) {
        return node.isMissingNode() || node.isNull();
    }

    /** 期望的 factId 列表。 */
    static List<String> ids(long... factIds) {
        List<String> ids = new ArrayList<>();
        for (long factId : factIds) {
            ids.add(Long.toString(factId));
        }
        return ids;
    }
}
