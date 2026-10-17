package com.paytech.billing.auto.ext.http.fact;

import static com.paytech.billing.test.builder.EconomicFactRequestBuilder.CLIENT_ID;
import static com.paytech.billing.test.builder.EconomicFactRequestBuilder.CURRENCY;
import static com.paytech.billing.test.builder.EconomicFactRequestBuilder.ENTITY_NO;
import static com.paytech.billing.test.builder.EconomicFactRequestBuilder.FINANCE_ENTITY_ID;
import static com.paytech.billing.test.builder.EconomicFactRequestBuilder.LOCAL_PARTNER_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.billing.api.EconomicFactCommandApi;
import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.enums.EconomicFactStatus;
import com.paytech.billing.api.enums.EconomicsType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.domain.EconomicFactBookScope;
import com.paytech.billing.test.base.AbstractEconomicFactApiTest;
import com.paytech.billing.test.builder.EconomicFactRequestBuilder;
import com.paytech.billing.test.checker.BillingResponseChecker;

import tools.jackson.databind.JsonNode;

/**
 * 按账簿主体读取已批准经济事实（account 重放）与发布进度（关账一致性检查）
 * <li>OE 账簿按 entityNo、Finance Entity 账簿按 financeEntityId 匹配并限定币种；只返回 APPROVED 与 SUPERSEDED，按 factId 升序游标分页。</li>
 *
 * @see EconomicFactCommandApi#listEconomicFacts(String, String, String, String, String)
 * @see EconomicFactCommandApi#getPendingEconomicFacts(String, String, String)
 */
class EconomicFactQueryTest extends AbstractEconomicFactApiTest {

    /** 分页：DRAFT 与其他 OE 的事实不出现；limit 1 逐页读取，满页返回下一页游标，末页游标为空；Finance Entity 账簿只含合作方事实。 */
    @Test
    void test_listByBookPagination() {
        long first = submitApprovedAndPublished(EconomicFactRequestBuilder.template());
        long partner = submitApprovedAndPublished(EconomicFactRequestBuilder.template().partner().addLine(
            AmountSource.PLATFORM_SERVICE_CHARGE, FactPartyRole.LOCAL_PARTNER, LOCAL_PARTNER_ID,
            FactPartyRole.FINANCE_ENTITY, FINANCE_ENTITY_ID, "1.00", FactLineDirection.CHARGE));
        submitDraft(EconomicFactRequestBuilder.template());
        submitApprovedAndPublished(EconomicFactRequestBuilder.template().entityNo("SA001").clearLines().addLine(
            AmountSource.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, "SA001", FactPartyRole.CLIENT, CLIENT_ID,
            "1.00", FactLineDirection.CHARGE));

        List<String> ids = new ArrayList<>();
        String cursor = null;
        do {
            BillingResponseChecker page = list(EconomicFactBookScope.OPERATING_ENTITY, ENTITY_NO, cursor, "1").assertSuccess(200);
            page.json().path("items").forEach(item -> ids.add(item.path("factId").asString()));
            cursor = page.text("nextFromFactId");
        } while (cursor != null);
        JsonNode financeBook =
            list(EconomicFactBookScope.FINANCE_ENTITY, FINANCE_ENTITY_ID, null, null).assertSuccess(200).json();

        assertAll(() -> assertEquals(List.of(Long.toString(first), Long.toString(partner)), ids),
            () -> assertEquals(1, financeBook.path("items").size()),
            () -> assertEquals(Long.toString(partner), financeBook.path("items").get(0).path("factId").asString()),
            () -> assertEquals(2, financeBook.path("items").get(0).path("lines").size()),
            () -> assertTrue(financeBook.path("nextFromFactId").isNull()
                || financeBook.path("nextFromFactId").isMissingNode(), "末页游标应为空"));
        list(EconomicFactBookScope.OPERATING_ENTITY, FINANCE_ENTITY_ID, null, null).assertInvalidRequest();
        list(EconomicFactBookScope.OPERATING_ENTITY, ENTITY_NO, null, "501").assertInvalidRequest();
    }

    /** 发布进度：已批准未发布的事实计入 pending 与最大未发布 ID，已发布事实给出最大已发布 ID；DRAFT 不计入。 */
    @Test
    void test_pendingProgress() {
        long published = submitApprovedAndPublished(EconomicFactRequestBuilder.template());
        submitDraft(EconomicFactRequestBuilder.template());
        long unpublished = published + 1_000_000L;
        testMapper.insertEconomicFact(unpublished, EconomicsType.MANUAL_ADJUSTMENT.getCode(), "ADJ-UNPUBLISHED",
            Long.parseLong(CLIENT_ID), ENTITY_NO, null, CURRENCY, EconomicFactStatus.APPROVED.getCode(), 1, null);
        testMapper.insertEconomicFact(unpublished + 1, EconomicsType.MANUAL_ADJUSTMENT.getCode(), "ADJ-SUPERSEDED",
            Long.parseLong(CLIENT_ID), ENTITY_NO, null, CURRENCY, EconomicFactStatus.SUPERSEDED.getCode(), 1,
            Instant.now());

        pending(EconomicFactBookScope.OPERATING_ENTITY, ENTITY_NO).assertSuccess(200).assertText("pendingCount", "1")
            .assertText("maxPendingFactId", Long.toString(unpublished))
            .assertText("maxPublishedFactId", Long.toString(unpublished + 1));
        pending(EconomicFactBookScope.FINANCE_ENTITY, FINANCE_ENTITY_ID).assertSuccess(200)
            .assertText("pendingCount", "0").assertText("maxPendingFactId", null).assertText("maxPublishedFactId", null);
    }

    /** 读取一页。 */
    BillingResponseChecker list(String bookEntityType, String bookEntityId, String fromFactId, String limit) {
        StringBuilder query = new StringBuilder(PATH + "?bookEntityType=" + bookEntityType + "&bookEntityId="
            + bookEntityId + "&currency=" + CURRENCY);
        if (fromFactId != null) {
            query.append("&fromFactId=").append(fromFactId);
        }
        if (limit != null) {
            query.append("&limit=").append(limit);
        }
        return get(query.toString());
    }

    /** 发布进度。 */
    BillingResponseChecker pending(String bookEntityType, String bookEntityId) {
        return get(PATH + "/pending?bookEntityType=" + bookEntityType + "&bookEntityId=" + bookEntityId + "&currency="
            + CURRENCY);
    }
}
