package com.paytech.commercial.auto.ext.http.history;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.commercial.api.agreement.request.AgreementTransitionRequestBody;
import com.paytech.commercial.api.history.BusinessChangeQueryApi;
import com.paytech.commercial.domain.BusinessChangeSnapshot;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.BusinessChangeOperation;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.test.base.write.AbstractCommercialWriteApiTest;
import com.paytech.commercial.test.builder.AgreementAcceptanceBodyBuilder;
import com.paytech.commercial.test.builder.AgreementPartyBuilder;
import com.paytech.commercial.test.builder.CreateAgreementBodyBuilder;
import com.paytech.commercial.test.checker.write.CommercialWriteResponseChecker;

import tools.jackson.databind.JsonNode;

/**
 * 变更历史（运营控制台第二切片 technique.md §2）：合同经正式写入 API 创建、提交签署、双方签署、激活后的 BCR 时间线，分页、空聚合与参数校验
 *
 * @see BusinessChangeQueryApi#listChanges
 */
class BusinessChangeHistoryHttpTest extends AbstractCommercialWriteApiTest {
    private static final String PATH = "/business-changes/v1?aggregateRootType=AGREEMENT&aggregateRootId=";

    /** 创建 → 提交签署 → Client 接受 → 平台签署 → 激活：五个版本倒序，原因、操作者、触发信息与 data 状态逐条对应。 */
    @Test
    void test_normalFlow_agreementTimeline() {
        long agreementId = activatedAgreement();

        CommercialWriteResponseChecker response = get(PATH + agreementId).assertOk();

        List<JsonNode> items = response.node("items").valueStream().toList();
        assertAll(() -> assertEquals(5, response.node("total").asLong()),
            () -> assertEquals(List.of(5L, 4L, 3L, 2L, 1L), items.stream().map(item -> item.path("versionNo").asLong()).toList()),
            () -> assertEquals(List.of(BusinessChangeReason.AGREEMENT_ACTIVATED.getCode(),
                BusinessChangeReason.AGREEMENT_PARTY_ACCEPTED.getCode(), BusinessChangeReason.AGREEMENT_PARTY_ACCEPTED.getCode(),
                BusinessChangeReason.AGREEMENT_SUBMITTED_FOR_SIGNATURE.getCode(), BusinessChangeReason.AGREEMENT_CREATED.getCode()),
                items.stream().map(item -> item.path("meta").path("reasonCode").asString()).toList()),
            () -> assertEquals(List.of(AgreementStatus.ACTIVE.getCode(), AgreementStatus.SIGNED.getCode(),
                AgreementStatus.PENDING_SIGNATURE.getCode(), AgreementStatus.PENDING_SIGNATURE.getCode(),
                AgreementStatus.DRAFT.getCode()), items.stream().map(item -> item.path("data").path("status").asString()).toList()),
            () -> assertTrue(items.stream().allMatch(item -> BusinessChangeRecordRecordType.AGREEMENT.getCode()
                .equals(item.path("recordType").asString()) && Long.toString(agreementId).equals(item.path("recordId").asString())
                && Long.toString(ACTOR_ID).equals(item.path("createBy").asString())
                && BusinessChangeSnapshot.TRIGGER_COMMAND.equals(item.path("meta").path("triggerType").asString())
                && item.path("meta").path("triggerId").asString().matches("[1-9][0-9]*")
                && item.path("meta").size() == 3)),
            () -> assertEquals(BusinessChangeOperation.CREATE.getCode(), items.get(4).path("operationType").asString()),
            () -> assertEquals(BusinessChangeOperation.UPDATE.getCode(), items.get(0).path("operationType").asString()),
            () -> assertEquals(CreateAgreementBodyBuilder.AGREEMENT_NO, items.get(0).path("data").path("agreementNo").asString()),
            () -> assertEquals("2026-09-11T12:00:00Z", items.get(0).path("createTime").asString()));
    }

    /** 分页：size=10 page=1 全部 5 条；第 2 页空页仍报告总数。 */
    @Test
    void test_paging() {
        long agreementId = activatedAgreement();

        CommercialWriteResponseChecker second = get(PATH + agreementId + "&page=2&size=10").assertOk();

        assertAll(() -> assertEquals(0, second.node("items").size()), () -> assertEquals(5, second.node("total").asLong()),
            () -> assertEquals(2, second.node("page").asInt()));
    }

    /** 没有历史的聚合根：空页、total 0。 */
    @Test
    void test_noDataFound() {
        CommercialWriteResponseChecker response = get(PATH + "930000000000777").assertOk();
        assertAll(() -> assertEquals(0, response.node("items").size()),
            () -> assertEquals(0, response.node("total").asLong()));
    }

    /** 缺少必填参数、聚合根类型不属于 commercial、非规范 ID、非法分页均 400 INVALID_REQUEST。 */
    @Test
    void test_invalidParameter() {
        get("/business-changes/v1?aggregateRootType=AGREEMENT").assertInvalidRequest();
        get("/business-changes/v1?aggregateRootId=1").assertInvalidRequest();
        get("/business-changes/v1?aggregateRootType=CLIENT&aggregateRootId=1").assertInvalidRequest();
        get(PATH + "01").assertInvalidRequest();
        get(PATH + "1&size=30").assertInvalidRequest();
    }

    /** 经正式写入 API 把合同推进到 ACTIVE（Root 版本 5），返回合同 ID。 */
    private long activatedAgreement() {
        long agreementId = post("/agreements/v1", CreateAgreementBodyBuilder.client().build()).assertOk().id("agreementId");
        String base = "/agreements/v1/" + agreementId;
        post(base + "/submit-for-signature", transition(1)).assertOk();
        post(base + "/parties/" + AgreementPartyBuilder.PARTY_ID + "/acceptance",
            AgreementAcceptanceBodyBuilder.click(2, "ACC-HISTORY-1").build()).assertOk();
        post(base + "/parties/" + CreateAgreementBodyBuilder.PLATFORM_PARTY_ID + "/acceptance",
            AgreementAcceptanceBodyBuilder.eSignature(3, "ACC-HISTORY-2").build()).assertOk()
            .assertText("status", AgreementStatus.SIGNED.getCode());
        post(base + "/activate", transition(4)).assertOk().assertText("status", AgreementStatus.ACTIVE.getCode());
        return agreementId;
    }

    /** 版本条件。 */
    private static AgreementTransitionRequestBody transition(long expectedVersion) {
        AgreementTransitionRequestBody body = new AgreementTransitionRequestBody();
        body.setExpectedChangeVersion(expectedVersion);
        return body;
    }
}
