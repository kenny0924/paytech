package com.paytech.commercial.auto.ext.http.agreement;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.commercial.api.agreement.AgreementApi;
import com.paytech.commercial.api.agreement.request.ResumeAgreementCapabilityRequestBody;
import com.paytech.commercial.api.agreement.request.SuspendAgreementCapabilityRequestBody;
import com.paytech.commercial.api.agreement.request.UpdateAgreementCapabilityRequestBody;
import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.AgreementCapabilityExecutionStatus;
import com.paytech.commercial.api.enums.AgreementCapabilityStatus;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.service.handler.AgreementCapabilityStatusChangedHandler;
import com.paytech.commercial.service.handler.CapabilityPrerequisiteCheckHandler;
import com.paytech.commercial.test.base.write.AbstractCommercialWriteApiTest;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.builder.AgreementCapabilityBodyBuilder;
import com.paytech.commercial.test.mocker.local.agreement.AgreementWriteMocker;
import com.paytech.commercial.test.mocker.local.agreement.CapabilityGateMocker;

import tools.jackson.databind.JsonNode;

/**
 * 开通项执行状态的运营控制与成员资格规则（开通项前置门禁 technique.md §9.1、§9.2、§10、§15.1）
 * <li>暂停：ENABLED → SUSPENDED_MANUALLY，快照带原因，事件触发来源 MANUAL；重复暂停、关闭项、PARTNER 合同 400，版本不符 409。</li>
 * <li>恢复：SUSPENDED_MANUALLY → PENDING_PREREQUISITES 并登记前置检查，提交后门禁核对为 ENABLED；不是手动暂停 400。</li>
 * <li>新增：CLIENT 合同等前置并登记检查（提交后核对为 ENABLED）；PARTNER 合同直接 ENABLED、不查资格。</li>
 * <li>PUT 成员资格：关闭清空执行状态；重新纳入回到等前置并核对；Body 传执行状态值 400。</li>
 *
 * @see AgreementApi#suspendCapability(String, String, String, SuspendAgreementCapabilityRequestBody)
 * @see AgreementApi#resumeCapability(String, String, String, ResumeAgreementCapabilityRequestBody)
 * @see AgreementApi#addCapability(String, String, com.paytech.commercial.api.agreement.request.AddAgreementCapabilityRequestBody)
 * @see AgreementApi#updateCapability(String, String, String, UpdateAgreementCapabilityRequestBody)
 */
class CapabilityExecutionControlHttpTest extends AbstractCommercialWriteApiTest {
    private static final String BASE = "/agreements/v1/" + AgreementBuilder.AGREEMENT_ID;

    /** CARD 开通项 ID。 */
    private static final long CARD_ID = 930000000000511L;

    @Autowired
    private CapabilityGateMocker gate;

    private AgreementWriteMocker.Mocker agreement;

    /** 每例：目录、画像、ACTIVE 合同（Root 版本 5）、ACTIVE 版本与覆盖 CARD/USD 的价格。 */
    @BeforeEach
    void setUpClassMocks() {
        agreement = gate.mockCoveredAgreement();
    }

    /** 已开通 → 手动暂停：版本 6，快照原因与暂停原因，事件 ENABLED → SUSPENDED_MANUALLY（MANUAL）。 */
    @Test
    void test_suspend_normalFlow() {
        gate.mockCapability(agreement, CARD_ID, CommercialCodes.Capability.CARD, AgreementCapabilityExecutionStatus.ENABLED);

        post(capabilityPath("suspension"), suspension(5, "风控复核")).assertOk().assertVersion(6)
            .assertText("executionStatus", AgreementCapabilityExecutionStatus.SUSPENDED_MANUALLY.getCode())
            .assertText("status", AgreementCapabilityStatus.ACTIVE.getCode());

        history.record(BusinessChangeRecordRecordType.AGREEMENT_CAPABILITY.getCode(), CARD_ID)
            .assertVersions("5:CREATE", "6:UPDATE").assertLastReason(BusinessChangeReason.AGREEMENT_CAPABILITY_SUSPENDED.getCode())
            .assertLastData("suspensionReason", "风控复核").assertLastActor(ACTOR_ID).assertIntegrity();
        assertChange(statusChange(6), AgreementCapabilityExecutionStatus.ENABLED,
            AgreementCapabilityExecutionStatus.SUSPENDED_MANUALLY, "MANUAL");
    }

    /** 重复暂停、关闭的开通项、PARTNER 合同为业务拒绝；过期版本 409；缺少原因为字段错误；状态不变。 */
    @Test
    void test_suspend_businessRuleViolation() {
        gate.mockCapability(agreement, CARD_ID, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.SUSPENDED_MANUALLY);
        long disabledId = CARD_ID + 1;
        gate.mockCapability(agreement, disabledId, CommercialCodes.Capability.APPLE_PAY, null);
        testMapper.overwriteCapabilityStatus(disabledId, AgreementCapabilityStatus.DISABLED);
        long partnerId = mockPartnerAgreementWithCapability();

        post(capabilityPath("suspension"), suspension(5, "重复")).assertBusinessCheckFailed("已是手动暂停");
        post(BASE + "/capabilities/" + disabledId + "/suspension", suspension(5, "关闭项"))
            .assertBusinessCheckFailed("开通项已关闭");
        post("/agreements/v1/" + partnerId + "/capabilities/" + (CARD_ID + 2) + "/suspension", suspension(5, "伙伴"))
            .assertBusinessCheckFailed("PARTNER 合同的开通项不能暂停");
        post(capabilityPath("suspension"), suspension(4, "过期")).assertConflict();
        post(capabilityPath("suspension"), suspension(5, " ")).assertValidationError("reason");

        assertEquals(AgreementCapabilityExecutionStatus.SUSPENDED_MANUALLY.getCode(), executionStatus(CARD_ID));
    }

    /** 手动暂停 → 恢复：版本 6 回到等前置（MANUAL），提交后门禁核对为 ENABLED（版本 7，PREREQUISITE）。 */
    @Test
    void test_resume_normalFlow() {
        gate.mockCapability(agreement, CARD_ID, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.SUSPENDED_MANUALLY);
        mockAgreementQualified();

        post(capabilityPath("resumption"), resumption(5)).assertOk().assertVersion(6)
            .assertText("executionStatus", AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES.getCode());

        Map<Long, JsonNode> changes = statusChanges(6, 7);
        assertChange(changes.get(6L), AgreementCapabilityExecutionStatus.SUSPENDED_MANUALLY,
            AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES, "MANUAL");
        assertChange(changes.get(7L), AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES,
            AgreementCapabilityExecutionStatus.ENABLED, "PREREQUISITE");
        assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), executionStatus(CARD_ID));
        history.record(BusinessChangeRecordRecordType.AGREEMENT_CAPABILITY.getCode(), CARD_ID)
            .assertVersions("5:CREATE", "6:UPDATE", "7:UPDATE").assertIntegrity();
    }

    /** 不是手动暂停（门禁暂停）的开通项不能恢复；版本不变。 */
    @Test
    void test_resume_notManuallySuspended() {
        gate.mockCapability(agreement, CARD_ID, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.SUSPENDED_BY_PREREQUISITE);

        post(capabilityPath("resumption"), resumption(5)).assertBusinessCheckFailed("只有手动暂停的开通项可以恢复");

        history.record(BusinessChangeRecordRecordType.AGREEMENT.getCode(), AgreementBuilder.AGREEMENT_ID).assertCurrentVersion(5);
    }

    /** CLIENT 合同新增：ACTIVE + 等前置，事件 MEMBERSHIP；同事务登记的前置检查提交后执行，开通项变为 ENABLED。 */
    @Test
    void test_add_clientPendingThenChecked() {
        mockAgreementQualified();

        long capabilityId = post(BASE + "/capabilities", AgreementCapabilityBodyBuilder.template()
            .conditions(AgreementCapabilityBodyBuilder.condition(PricingDimension.CURRENCY, "USD")).buildAdd(5)).assertOk()
            .assertText("executionStatus", AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES.getCode()).assertVersion(6)
            .id("agreementCapabilityId");

        Map<Long, JsonNode> changes = statusChanges(6, 7);
        JsonNode added = changes.get(6L);
        assertAll(() -> assertTrue(added.path("fromExecutionStatus").isNull()),
            () -> assertEquals(AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES.getCode(),
                added.path("toExecutionStatus").asString()),
            () -> assertEquals("MEMBERSHIP", added.path("trigger").asString()));
        assertChange(changes.get(7L), AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES,
            AgreementCapabilityExecutionStatus.ENABLED, "PREREQUISITE");
        assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), executionStatus(capabilityId));
    }

    /** PARTNER 合同新增：直接 ENABLED，不登记前置检查、不查资格；事件 MEMBERSHIP。 */
    @Test
    void test_add_partnerEnabledWithoutCheck() {
        long partnerId = mockPartnerAgreementWithCapability();

        long capabilityId = post("/agreements/v1/" + partnerId + "/capabilities", AgreementCapabilityBodyBuilder.template()
            .capabilityCode(CommercialCodes.Capability.APPLE_PAY).buildAdd(5)).assertOk()
            .assertText("executionStatus", AgreementCapabilityExecutionStatus.ENABLED.getCode()).id("agreementCapabilityId");

        JsonNode payload = events.awaitEnvelope(AgreementCapabilityStatusChangedHandler.EVENT_TYPE,
            AgreementCapabilityStatusChangedHandler.buildEvent(partnerId, 6).eventKey()).path("payload");
        assertAll(() -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), executionStatus(capabilityId)),
            () -> assertEquals("MEMBERSHIP", payload.path("changes").get(0).path("trigger").asString()),
            () -> assertEquals(0, testMapper.countCompensationEvents(CapabilityPrerequisiteCheckHandler.EVENT_TYPE)),
            () -> assertEquals(0, compliance.count()));
    }

    /** PUT 关闭：执行状态清空（事件 ENABLED → 空）；再纳入：回到等前置并核对为 ENABLED；Body 传 ENABLED 为字段错误。 */
    @Test
    void test_membership_disableAndReinclude() {
        gate.mockCapability(agreement, CARD_ID, CommercialCodes.Capability.CARD, AgreementCapabilityExecutionStatus.ENABLED);
        mockAgreementQualified();

        put(capabilityPath(null), membership(5, AgreementCapabilityStatus.DISABLED.getCode())).assertOk().assertVersion(6)
            .assertText("status", AgreementCapabilityStatus.DISABLED.getCode());
        JsonNode disabled = statusChange(6);
        String disabledExecution = executionStatus(CARD_ID);
        put(capabilityPath(null), membership(6, AgreementCapabilityStatus.ACTIVE.getCode())).assertOk().assertVersion(7)
            .assertText("executionStatus", AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES.getCode());
        put(capabilityPath(null), membership(7, AgreementCapabilityExecutionStatus.ENABLED.getCode()))
            .assertValidationError("status");

        assertAll(() -> assertNull(disabledExecution),
            () -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), disabled.path("fromExecutionStatus").asString()),
            () -> assertTrue(disabled.path("toExecutionStatus").isNull()),
            () -> assertEquals("MEMBERSHIP", disabled.path("trigger").asString()));
        assertChange(statusChange(8), AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES,
            AgreementCapabilityExecutionStatus.ENABLED, "PREREQUISITE");
    }

    /** ACTIVE PARTNER 合同（覆盖 AE993，Root 版本 5，已订阅 ACQUIRING）及其 ENABLED 的 CARD；返回合同 ID。 */
    long mockPartnerAgreementWithCapability() {
        long partnerId = AgreementBuilder.AGREEMENT_ID + 700;
        AgreementWriteMocker.Mocker partner = gate.mockAgreement(AgreementBuilder.template().agreementId(partnerId)
            .agreementNo("GATE-PARTNER-2").agreementType(AgreementAgreementType.PARTNER).businessMode(null).fundMode(null)
            .pricingAuthority(null).clientId(null).entityNo(null).localPartnerId(930000000000985L)
            .status(AgreementStatus.ACTIVE)).operatingEntity(partnerId + 1, AgreementBuilder.ENTITY_NO);
        gate.mockCapability(partner, CARD_ID + 2, AgreementBuilder.ENTITY_NO, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.ENABLED);
        return partnerId;
    }

    /** 默认 CARD 开通项路径；action 为空时是改写路径。 */
    static String capabilityPath(String action) {
        return BASE + "/capabilities/" + CARD_ID + (action == null ? "" : "/" + action);
    }

    /** 暂停请求。 */
    static SuspendAgreementCapabilityRequestBody suspension(long expectedVersion, String reason) {
        SuspendAgreementCapabilityRequestBody body = new SuspendAgreementCapabilityRequestBody();
        body.setExpectedChangeVersion(expectedVersion);
        body.setReason(reason);
        return body;
    }

    /** 恢复请求。 */
    static ResumeAgreementCapabilityRequestBody resumption(long expectedVersion) {
        ResumeAgreementCapabilityRequestBody body = new ResumeAgreementCapabilityRequestBody();
        body.setExpectedChangeVersion(expectedVersion);
        return body;
    }

    /** 与存量开通项条款一致、只改成员资格的改写请求（CURRENCY=USD）。 */
    static UpdateAgreementCapabilityRequestBody membership(long expectedVersion, String status) {
        UpdateAgreementCapabilityRequestBody body = AgreementCapabilityBodyBuilder.template()
            .conditions(AgreementCapabilityBodyBuilder.condition(PricingDimension.CURRENCY, "USD")).buildUpdate(expectedVersion);
        body.setStatus(status);
        return body;
    }

    /** 等待默认合同该版本的执行状态变更事件，返回唯一一条变化。 */
    JsonNode statusChange(long changeVersion) {
        return statusChanges(changeVersion).get(changeVersion);
    }

    /** 一次读取默认合同多个版本的执行状态变更事件，返回各版本唯一一条变化。 */
    Map<Long, JsonNode> statusChanges(long... changeVersions) {
        String[] keys = Arrays.stream(changeVersions).mapToObj(version -> AgreementCapabilityStatusChangedHandler
            .buildEvent(AgreementBuilder.AGREEMENT_ID, version).eventKey()).toArray(String[]::new);
        Map<String, JsonNode> envelopes = events.awaitEnvelopes(AgreementCapabilityStatusChangedHandler.EVENT_TYPE, keys);
        Map<Long, JsonNode> changes = new LinkedHashMap<>();
        for (int index = 0; index < keys.length; index++) {
            JsonNode items = envelopes.get(keys[index]).path("payload").path("changes");
            assertEquals(1, items.size(), "应只有一条变化：" + keys[index]);
            changes.put(changeVersions[index], items.get(0));
        }
        return changes;
    }

    /** 断言一条变化的前后状态与触发来源。 */
    static void assertChange(JsonNode change, AgreementCapabilityExecutionStatus from, AgreementCapabilityExecutionStatus to,
        String trigger) {
        assertEquals(Map.of("from", from.getCode(), "to", to.getCode(), "trigger", trigger), Map.of("from",
            change.path("fromExecutionStatus").asString(), "to", change.path("toExecutionStatus").asString(), "trigger",
            change.path("trigger").asString()));
    }

    /** 数据库中的执行状态。 */
    String executionStatus(long agreementCapabilityId) {
        return (String) testMapper.agreementCapability(agreementCapabilityId).get("execution_status");
    }
}
