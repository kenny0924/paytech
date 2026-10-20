package com.paytech.commercial.auto.ext.http.agreement;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.commercial.api.agreement.AgreementQueryApi;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.test.base.write.AbstractCommercialWriteApiTest;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.checker.write.CommercialWriteResponseChecker;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;

import tools.jackson.databind.JsonNode;

/**
 * 合同列表（运营控制台第二切片 technique.md §2）：筛选、合同编号字面前缀、创建时间倒序、分页与总数、BCR Root 版本、参数校验
 *
 * @see AgreementQueryApi#listAgreements
 */
class AgreementListHttpTest extends AbstractCommercialWriteApiTest {
    private static final String PATH = "/agreements/v1";

    /** CLIENT、Client 901、AE993、ACTIVE，最早创建，Root 版本 5。 */
    private static final long ACTIVE_ID = AgreementBuilder.AGREEMENT_ID;

    /** CLIENT、Client 901、AE993、DRAFT，编号含下划线。 */
    private static final long DRAFT_ID = AgreementBuilder.AGREEMENT_ID + 100;

    /** CLIENT、另一 Client、AE001、PENDING_SIGNATURE。 */
    private static final long OTHER_CLIENT_ID = AgreementBuilder.AGREEMENT_ID + 200;

    /** PARTNER、覆盖 AE993、DRAFT，最晚创建。 */
    private static final long PARTNER_ID = AgreementBuilder.AGREEMENT_ID + 300;

    private static final long OTHER_CLIENT = 930000000000999L;

    private static final long LOCAL_PARTNER = 930000000000921L;

    /** 每例：四份合同，创建时间依次晚一天。 */
    @BeforeEach
    void setUpClassMocks() {
        agreements.mock(AgreementBuilder.template().status(AgreementStatus.ACTIVE).createTime(daysAgo(3)), 5);
        agreements.mock(AgreementBuilder.template().agreementId(DRAFT_ID).agreementNo("WRITE_AGR-2")
            .createTime(daysAgo(2)), 1);
        agreements.mock(AgreementBuilder.template().agreementId(OTHER_CLIENT_ID).agreementNo("OTHER-1")
            .clientId(OTHER_CLIENT).entityNo("AE001").status(AgreementStatus.PENDING_SIGNATURE).createTime(daysAgo(1)), 2);
        agreements.mock(partner(), 1).operatingEntity(PARTNER_ID + 1, AgreementBuilder.ENTITY_NO);
    }

    /** 无筛选：创建时间倒序、全部字段与 Root 版本、分页元数据。 */
    @Test
    void test_normalFlow() {
        CommercialWriteResponseChecker response = get(PATH).assertOk();

        JsonNode active = response.node("items").get(3);
        assertAll(() -> assertEquals(List.of(PARTNER_ID, OTHER_CLIENT_ID, DRAFT_ID, ACTIVE_ID), ids(response)),
            () -> assertEquals(4, response.node("total").asLong()), () -> assertEquals(1, response.node("page").asInt()),
            () -> assertEquals(10, response.node("size").asInt()),
            () -> assertEquals(AgreementBuilder.AGREEMENT_NO, active.path("agreementNo").asString()),
            () -> assertEquals(AgreementAgreementType.CLIENT.getCode(), active.path("agreementType").asString()),
            () -> assertEquals(AgreementStatus.ACTIVE.getCode(), active.path("status").asString()),
            () -> assertEquals(Long.toString(AgreementBuilder.CLIENT_ID), active.path("clientId").asString()),
            () -> assertEquals(AgreementBuilder.ENTITY_NO, active.path("entityNo").asString()),
            () -> assertEquals("2026-09-01T00:00:00Z", active.path("effectiveFrom").asString()),
            () -> assertEquals(5, active.path("changeVersion").asLong()),
            () -> assertEquals(CheckoutFixture.NOW.toString(), active.path("updateTime").asString()),
            () -> assertEquals(Long.toString(LOCAL_PARTNER), response.node("items").get(0).path("localPartnerId").asString()),
            () -> assertEquals(true, response.node("items").get(0).path("clientId").isNull()));
    }

    /** 各筛选条件单独与组合生效；entityNo 同时命中 PARTNER 覆盖 OE；状态可重复。 */
    @Test
    void test_filters() {
        assertAll(
            () -> assertEquals(List.of(DRAFT_ID, ACTIVE_ID), ids(get(PATH + "?clientId=" + AgreementBuilder.CLIENT_ID))),
            () -> assertEquals(List.of(PARTNER_ID), ids(get(PATH + "?localPartnerId=" + LOCAL_PARTNER))),
            () -> assertEquals(List.of(PARTNER_ID), ids(get(PATH + "?agreementType=PARTNER"))),
            () -> assertEquals(List.of(PARTNER_ID, DRAFT_ID, ACTIVE_ID), ids(get(PATH + "?status=DRAFT&status=ACTIVE"))),
            () -> assertEquals(List.of(PARTNER_ID, DRAFT_ID, ACTIVE_ID), ids(get(PATH + "?entityNo=AE993"))),
            () -> assertEquals(List.of(OTHER_CLIENT_ID), ids(get(PATH + "?entityNo=AE001"))),
            () -> assertEquals(List.of(DRAFT_ID), ids(get(PATH + "?clientId=" + AgreementBuilder.CLIENT_ID
                + "&status=DRAFT&agreementType=CLIENT&entityNo=AE993"))));
    }

    /** 合同编号按字面前缀匹配：下划线不是通配符。 */
    @Test
    void test_edgeCase_agreementNoLiteralPrefix() {
        assertAll(() -> assertEquals(List.of(DRAFT_ID, ACTIVE_ID), ids(get(PATH + "?agreementNo=WRITE"))),
            () -> assertEquals(List.of(DRAFT_ID), ids(get(PATH + "?agreementNo=WRITE_"))),
            () -> assertEquals(List.of(ACTIVE_ID), ids(get(PATH + "?agreementNo=" + AgreementBuilder.AGREEMENT_NO))));
    }

    /** 分页：第 2 页按同一排序续接，total 为全量条数；超出末页为空页且保留请求页码。 */
    @Test
    void test_paging() {
        for (int index = 1; index <= 8; index++) {
            agreements.mock(AgreementBuilder.template().agreementId(AgreementBuilder.AGREEMENT_ID + 1000 + index * 10L)
                .agreementNo("PAGE-" + index).clientId(OTHER_CLIENT).createTime(daysAgo(10 + index)), 1);
        }

        CommercialWriteResponseChecker second = get(PATH + "?page=2&size=10").assertOk();
        CommercialWriteResponseChecker beyond = get(PATH + "?page=3&size=10").assertOk();

        assertAll(() -> assertEquals(12, second.node("total").asLong()),
            () -> assertEquals(List.of(AgreementBuilder.AGREEMENT_ID + 1070, AgreementBuilder.AGREEMENT_ID + 1080),
                ids(second)),
            () -> assertEquals(0, beyond.node("items").size()), () -> assertEquals(3, beyond.node("page").asInt()),
            () -> assertEquals(12, beyond.node("total").asLong()));
    }

    /** 无匹配：空页、total 0。 */
    @Test
    void test_noDataFound() {
        CommercialWriteResponseChecker response = get(PATH + "?clientId=930000000000888").assertOk();
        assertAll(() -> assertEquals(0, response.node("items").size()),
            () -> assertEquals(0, response.node("total").asLong()));
    }

    /** 非法分页、未知状态 / 类型、非规范 ID、非法 OE 与合同编号格式均 400 INVALID_REQUEST。 */
    @Test
    void test_invalidParameter() {
        get(PATH + "?size=15").assertInvalidRequest();
        get(PATH + "?page=0").assertInvalidRequest();
        get(PATH + "?status=OPEN").assertInvalidRequest();
        get(PATH + "?agreementType=MERCHANT").assertInvalidRequest();
        get(PATH + "?clientId=01").assertInvalidRequest();
        get(PATH + "?entityNo=AE99").assertInvalidRequest();
        get(PATH + "?agreementNo=W%25").assertInvalidRequest();
    }

    /** PARTNER 合同：无 Client、OE 与业务模式三属性。 */
    private static AgreementBuilder partner() {
        return AgreementBuilder.template().agreementId(PARTNER_ID).agreementNo("PARTNER-1")
            .agreementType(AgreementAgreementType.PARTNER).clientId(null).localPartnerId(LOCAL_PARTNER).entityNo(null)
            .businessMode(null).fundMode(null).pricingAuthority(null).status(AgreementStatus.DRAFT)
            .createTime(CheckoutFixture.NOW);
    }

    /** 固定时点前若干天。 */
    private static Instant daysAgo(int days) {
        return CheckoutFixture.NOW.minus(Duration.ofDays(days));
    }

    /** 当前页合同 ID。 */
    private static List<Long> ids(CommercialWriteResponseChecker response) {
        return response.assertOk().node("items").valueStream()
            .map(item -> Long.parseLong(item.path("agreementId").asString())).toList();
    }
}
