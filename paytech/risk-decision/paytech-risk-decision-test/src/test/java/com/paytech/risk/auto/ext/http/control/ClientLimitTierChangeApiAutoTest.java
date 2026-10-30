package com.paytech.risk.auto.ext.http.control;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.control.core.enums.LimitTierApprovalLevel;
import com.paytech.risk.control.core.enums.LimitTierChangeStatus;
import com.paytech.risk.control.core.enums.LimitTierSource;
import com.paytech.risk.control.core.enums.RiskListEntryStatus;
import com.paytech.risk.control.core.enums.UnderwritingOutcome;
import com.paytech.risk.control.core.enums.UnderwritingRiskLevel;
import com.paytech.risk.decision.api.control.ClientLimitTierApi;
import com.paytech.risk.decision.api.control.request.DecideClientTierChangeRequestBody;
import com.paytech.risk.decision.api.control.request.RequestClientTierChangeRequestBody;
import com.paytech.risk.decision.api.control.request.WithdrawClientTierChangeRequestBody;
import com.paytech.risk.decision.api.enums.RiskLimitTierErrorCode;
import com.paytech.risk.test.base.AbstractLimitTierApiTest;
import com.paytech.risk.test.builder.control.LimitTierRequestBuilder;
import com.paytech.risk.test.builder.control.UnderwritingRequestBuilder;
import com.paytech.risk.test.checker.control.LimitTierResponseChecker;
import com.paytech.risk.test.checker.control.UnderwritingResponseChecker;

/**
 * 档位变更申请与四眼审批：申请、普通 / 高级审批与驳回、撤回，批准后档位与 TIER 名单投影同一事务生效
 * <li>有效承保经真实承保接口准备（首次批准即建 VERIFICATION）；OBSERVATION、RESTRICTED 等起点由档位 Mocker 插入。</li>
 * <li>审批级别按申请时有效承保的 CRR 冻结：LOW 普通审批，MEDIUM / HIGH / 未评只能高级审批；申请人不能审批自己的申请。</li>
 *
 * @see ClientLimitTierApi#requestClientTierChange(String, String, String, RequestClientTierChangeRequestBody)
 * @see ClientLimitTierApi#withdrawClientTierChange(String, String, String, WithdrawClientTierChangeRequestBody)
 * @see ClientLimitTierApi#approveClientTierChange(String, String, String, DecideClientTierChangeRequestBody)
 * @see ClientLimitTierApi#approveClientTierChangeElevated(String, String, String, DecideClientTierChangeRequestBody)
 * @see ClientLimitTierApi#rejectClientTierChange(String, String, String, DecideClientTierChangeRequestBody)
 * @see ClientLimitTierApi#rejectClientTierChangeElevated(String, String, String, DecideClientTierChangeRequestBody)
 * @see ClientLimitTierApi#listClientTierChanges(String, String, String)
 */
class ClientLimitTierChangeApiAutoTest extends AbstractLimitTierApiTest {

    /** CRR=LOW 上线复核：普通审批批准，档位进入 OBSERVATION，观察目标取承保，旧名单成员被替代、新名单成员生效。 */
    @Test
    void test_normalFlow_goLiveReviewStandardApproval() {
        long clientId = newClientId();
        approved(clientId, UnderwritingRequestBuilder.input(UnderwritingRiskLevel.LOW));

        LimitTierResponseChecker requested = request(clientId, LimitTierRequestBuilder.goLive(1))
            .assertChange(201, LimitTierChangeStatus.PENDING.getCode(), LimitTier.VERIFICATION.getCode(),
                LimitTier.OBSERVATION.getCode(), LimitTierApprovalLevel.STANDARD.getCode())
            .assertText("/crrSnapshot", UnderwritingRiskLevel.LOW.getCode())
            .assertText("/checklist/qualificationStatus", LimitTierRequestBuilder.QUALIFICATION_APPROVED)
            .assertText("/observationTargetDays", "30");
        tierChecker.assertTier(clientId, LimitTier.VERIFICATION, 1);

        approve(requested.changeId(), requested.version(), false)
            .assertChange(200, LimitTierChangeStatus.APPROVED.getCode(), LimitTier.VERIFICATION.getCode(),
                LimitTier.OBSERVATION.getCode(), LimitTierApprovalLevel.STANDARD.getCode())
            .assertText("/decidedBy", Long.toString(APPROVER));

        Map<String, Object> tier = tierChecker.assertTier(clientId, LimitTier.OBSERVATION, 2);
        List<Map<String, Object>> entries = tierStore.tierEntries(clientId);
        assertAll("观察期与名单替换",
            () -> assertEquals(LimitTierSource.REQUEST.getCode(), tier.get("source_type")),
            () -> assertNotNull(tier.get("observation_started_time")),
            () -> assertEquals(30, ((Number)tier.get("observation_target_days")).intValue()),
            () -> assertEquals(100, ((Number)tier.get("observation_target_transactions")).intValue()),
            () -> assertEquals(2, entries.size()),
            () -> assertEquals(RiskListEntryStatus.SUPERSEDED.getCode(), entries.get(0).get("status")),
            () -> assertEquals(LimitTier.OBSERVATION.listCode(), entries.get(1).get("list_code")),
            () -> assertEquals(entries.get(0).get("entry_id"), entries.get(1).get("supersedes_entry_id")),
            () -> assertEquals("change:" + requested.changeId(), entries.get(1).get("reason")));
        tierGet(REQUESTER, TIERS + "/clients/" + clientId + "/tier-changes")
            .assertItems("changeType", List.of(LimitTierSource.REQUEST.getCode(), LimitTierSource.UNDERWRITING_INIT.getCode()));
    }

    /** 上线复核清单：缺上线说明、人工项未勾、资格非 APPROVED、无开通项均 409，档位与申请不变。 */
    @Test
    void test_businessRuleViolation_goLiveChecklist() {
        long clientId = newClientId();
        approved(clientId, UnderwritingRequestBuilder.input(UnderwritingRiskLevel.LOW));
        RequestClientTierChangeRequestBody noNote = LimitTierRequestBuilder.goLive(1);
        noNote.setGoLiveNote(" ");
        RequestClientTierChangeRequestBody unchecked = LimitTierRequestBuilder.goLive(1);
        unchecked.getChecklist().setProductionVerified(Boolean.FALSE);
        RequestClientTierChangeRequestBody unqualified = LimitTierRequestBuilder.goLive(1);
        unqualified.getChecklist().setQualificationStatus("EXPIRED");
        RequestClientTierChangeRequestBody noCapability = LimitTierRequestBuilder.goLive(1);
        noCapability.getChecklist().setEnabledPaymentCapabilities("");
        RequestClientTierChangeRequestBody noChecklist = LimitTierRequestBuilder.goLive(1);
        noChecklist.setChecklist(null);

        for (RequestClientTierChangeRequestBody body : List.of(noNote, unchecked, unqualified, noCapability, noChecklist)) {
            request(clientId, body).assertProblem(409, RiskLimitTierErrorCode.RISK_LIMIT_TIER_CHECKLIST_NOT_SATISFIED.getCode());
        }
        assertEquals(1, tierStore.changes(clientId).size(), "只有承保建档记录");
        tierChecker.assertTier(clientId, LimitTier.VERIFICATION, 1);
    }

    /** 无有效承保（以本库事实为准）：清单满足也不能上线复核，且审批级别为高级。 */
    @Test
    void test_businessRuleViolation_goLiveWithoutUnderwriting() {
        long clientId = newClientId();
        tierMocker.mockTier(clientId, LimitTier.VERIFICATION);

        request(clientId, LimitTierRequestBuilder.goLive(1))
            .assertProblem(409, RiskLimitTierErrorCode.RISK_LIMIT_TIER_CHECKLIST_NOT_SATISFIED.getCode());
        request(clientId, LimitTierRequestBuilder.request(1, LimitTier.RESTRICTED))
            .assertChange(201, LimitTierChangeStatus.PENDING.getCode(), LimitTier.VERIFICATION.getCode(),
                LimitTier.RESTRICTED.getCode(), LimitTierApprovalLevel.ELEVATED.getCode())
            .assertText("/crrSnapshot", null);
    }

    /** CRR=MEDIUM：普通审批与普通驳回返回 ELEVATION_REQUIRED，高级审批通过。 */
    @Test
    void test_businessRuleViolation_elevationRequired() {
        long clientId = newClientId();
        approved(clientId, UnderwritingRequestBuilder.input(UnderwritingRiskLevel.MEDIUM));
        LimitTierResponseChecker requested = request(clientId, LimitTierRequestBuilder.goLive(1))
            .assertChange(201, LimitTierChangeStatus.PENDING.getCode(), LimitTier.VERIFICATION.getCode(),
                LimitTier.OBSERVATION.getCode(), LimitTierApprovalLevel.ELEVATED.getCode());

        approve(requested.changeId(), 1, false)
            .assertProblem(409, RiskLimitTierErrorCode.RISK_LIMIT_TIER_ELEVATION_REQUIRED.getCode());
        reject(requested.changeId(), 1, "额度不足", false)
            .assertProblem(409, RiskLimitTierErrorCode.RISK_LIMIT_TIER_ELEVATION_REQUIRED.getCode());
        approve(requested.changeId(), 1, true)
            .assertChange(200, LimitTierChangeStatus.APPROVED.getCode(), LimitTier.VERIFICATION.getCode(),
                LimitTier.OBSERVATION.getCode(), LimitTierApprovalLevel.ELEVATED.getCode());
        tierChecker.assertTier(clientId, LimitTier.OBSERVATION, 2);
    }

    /** CRR=HIGH（经复核批准的承保）：审批级别为高级；CRR=LOW 的申请高级接口同样可以处理。 */
    @Test
    void test_edgeCase_highCrrAndElevatedHandlesStandard() {
        long high = newClientId();
        UnderwritingResponseChecker draft = createDraft(high, UnderwritingRequestBuilder.input(UnderwritingRiskLevel.HIGH),
            UnderwritingRequestBuilder.decision());
        UnderwritingResponseChecker submitted = submit(draft.id(), draft.version(),
            UnderwritingRequestBuilder.input(UnderwritingRiskLevel.HIGH), UnderwritingRequestBuilder.decision(),
            UnderwritingOutcome.APPROVE, null);
        post(RISK_LEAD, ROOT + "/underwritings/" + submitted.id() + "/review",
            UnderwritingRequestBuilder.review(submitted.version(), UnderwritingOutcome.APPROVE, null)).assertStatus(200);
        request(high, LimitTierRequestBuilder.request(1, LimitTier.RESTRICTED))
            .assertText("/approvalLevel", LimitTierApprovalLevel.ELEVATED.getCode())
            .assertText("/crrSnapshot", UnderwritingRiskLevel.HIGH.getCode());

        long low = newClientId();
        approved(low, UnderwritingRequestBuilder.input(UnderwritingRiskLevel.LOW));
        LimitTierResponseChecker requested = request(low, LimitTierRequestBuilder.request(1, LimitTier.RESTRICTED));
        reject(requested.changeId(), requested.version(), "暂不受限", true)
            .assertChange(200, LimitTierChangeStatus.REJECTED.getCode(), LimitTier.VERIFICATION.getCode(),
                LimitTier.RESTRICTED.getCode(), LimitTierApprovalLevel.STANDARD.getCode())
            .assertText("/decisionComment", "暂不受限");
        tierChecker.assertTier(low, LimitTier.VERIFICATION, 1);
    }

    /** 四眼：申请人不能批准或驳回自己的申请。 */
    @Test
    void test_businessRuleViolation_selfApproval() {
        long clientId = newClientId();
        approved(clientId, UnderwritingRequestBuilder.input(UnderwritingRiskLevel.LOW));
        LimitTierResponseChecker requested = request(clientId, LimitTierRequestBuilder.goLive(1));

        tierPost(REQUESTER, TIERS + "/tier-changes/" + requested.changeId() + "/approval",
            LimitTierRequestBuilder.decide(1, null))
            .assertProblem(409, RiskLimitTierErrorCode.RISK_LIMIT_TIER_SELF_APPROVAL.getCode());
        tierPost(REQUESTER, TIERS + "/tier-changes/" + requested.changeId() + "/elevated-rejection",
            LimitTierRequestBuilder.decide(1, "自己驳回"))
            .assertProblem(409, RiskLimitTierErrorCode.RISK_LIMIT_TIER_SELF_APPROVAL.getCode());
        tierChecker.assertTier(clientId, LimitTier.VERIFICATION, 1);
    }

    /** 不允许的迁移、已有待审批、档位版本不符、目标档位非法。 */
    @Test
    void test_businessRuleViolation_transitionAndPending() {
        long clientId = newClientId();
        approved(clientId, UnderwritingRequestBuilder.input(UnderwritingRiskLevel.LOW));

        request(clientId, LimitTierRequestBuilder.request(1, LimitTier.STANDARD))
            .assertProblem(409, RiskLimitTierErrorCode.RISK_LIMIT_TIER_TRANSITION_INVALID.getCode());
        request(clientId, LimitTierRequestBuilder.request(1, LimitTier.VERIFICATION))
            .assertProblem(409, RiskLimitTierErrorCode.RISK_LIMIT_TIER_TRANSITION_INVALID.getCode());
        request(clientId, LimitTierRequestBuilder.goLive(2))
            .assertProblem(409, RiskLimitTierErrorCode.RISK_LIMIT_TIER_VERSION_CONFLICT.getCode());
        RequestClientTierChangeRequestBody unknown = LimitTierRequestBuilder.goLive(1);
        unknown.setToTier("PLATINUM");
        request(clientId, unknown).assertProblem(422, "VALIDATION_ERROR").assertFieldError("toTier");
        request(clientId, LimitTierRequestBuilder.goLive(1)).assertStatus(201);
        request(clientId, LimitTierRequestBuilder.request(1, LimitTier.RESTRICTED))
            .assertProblem(409, RiskLimitTierErrorCode.RISK_LIMIT_TIER_CHANGE_PENDING_EXISTS.getCode());
    }

    /** 驳回须写意见；撤回只能由申请人发起；已结束的申请不能再处理；撤回后可重新申请。 */
    @Test
    void test_edgeCase_rejectAndWithdraw() {
        long clientId = newClientId();
        approved(clientId, UnderwritingRequestBuilder.input(UnderwritingRiskLevel.LOW));
        LimitTierResponseChecker first = request(clientId, LimitTierRequestBuilder.goLive(1));

        reject(first.changeId(), 1, " ", false).assertProblem(422, "VALIDATION_ERROR").assertFieldError("comment");
        tierPost(APPROVER, TIERS + "/tier-changes/" + first.changeId() + "/withdrawal", LimitTierRequestBuilder.withdraw(1))
            .assertProblem(409, RiskLimitTierErrorCode.RISK_LIMIT_TIER_NOT_REQUESTER.getCode());
        tierPost(REQUESTER, TIERS + "/tier-changes/" + first.changeId() + "/withdrawal", LimitTierRequestBuilder.withdraw(1))
            .assertChange(200, LimitTierChangeStatus.WITHDRAWN.getCode(), LimitTier.VERIFICATION.getCode(),
                LimitTier.OBSERVATION.getCode(), LimitTierApprovalLevel.STANDARD.getCode())
            .assertText("/decidedBy", Long.toString(REQUESTER));
        approve(first.changeId(), 2, false)
            .assertProblem(409, RiskLimitTierErrorCode.RISK_LIMIT_TIER_CHANGE_STATE_INVALID.getCode());

        LimitTierResponseChecker second = request(clientId, LimitTierRequestBuilder.goLive(1)).assertStatus(201);
        reject(second.changeId(), 1, "生产验证截图缺失", false)
            .assertChange(200, LimitTierChangeStatus.REJECTED.getCode(), LimitTier.VERIFICATION.getCode(),
                LimitTier.OBSERVATION.getCode(), LimitTierApprovalLevel.STANDARD.getCode());
        approve(second.changeId(), 1, false)
            .assertProblem(409, RiskLimitTierErrorCode.RISK_LIMIT_TIER_CHANGE_STATE_INVALID.getCode());
        tierChecker.assertTier(clientId, LimitTier.VERIFICATION, 1);
    }

    /** 观察档降为受限：记住原档位；恢复不得高于原档位，恢复到 OBSERVATION 重新开始观察期。 */
    @Test
    void test_normalFlow_restrictAndRestore() {
        long clientId = newClientId();
        tierMocker.mockObservation(clientId, 12, 30, 100);
        LimitTierResponseChecker restrict = request(clientId, LimitTierRequestBuilder.request(1, LimitTier.RESTRICTED));
        approve(restrict.changeId(), 1, true).assertStatus(200);
        Map<String, Object> restricted = tierChecker.assertTier(clientId, LimitTier.RESTRICTED, 2);
        assertAll(() -> assertEquals(LimitTier.OBSERVATION.getCode(), restricted.get("previous_tier")),
            () -> assertNull(restricted.get("observation_started_time")));

        request(clientId, LimitTierRequestBuilder.request(2, LimitTier.STANDARD))
            .assertProblem(409, RiskLimitTierErrorCode.RISK_LIMIT_TIER_TRANSITION_INVALID.getCode());
        request(clientId, LimitTierRequestBuilder.request(2, LimitTier.RESTRICTED))
            .assertProblem(409, RiskLimitTierErrorCode.RISK_LIMIT_TIER_TRANSITION_INVALID.getCode());
        LimitTierResponseChecker restore = request(clientId, LimitTierRequestBuilder.request(2, LimitTier.OBSERVATION))
            .assertText("/observationTargetDays", "30");
        approve(restore.changeId(), 1, true).assertStatus(200);

        Map<String, Object> restored = tierChecker.assertTier(clientId, LimitTier.OBSERVATION, 3);
        assertAll(() -> assertNull(restored.get("previous_tier")),
            () -> assertEquals(Long.valueOf(restore.changeId()), ((Number)restored.get("observation_started_by_change_id")).longValue()),
            () -> assertEquals(3, tierStore.tierEntries(clientId).size(), "OBSERVATION → RESTRICTED → OBSERVATION 三个成员"));
    }

    /** 延长观察：可改目标，批准时重置开始时间，名单成员不变；申请时冻结观察期进度。 */
    @Test
    void test_edgeCase_extendObservation() {
        long clientId = newClientId();
        tierMocker.mockObservation(clientId, 40, 30, 100);
        long entryBefore = ((Number)tierStore.tierEntries(clientId).getFirst().get("entry_id")).longValue();
        RequestClientTierChangeRequestBody body = LimitTierRequestBuilder.request(1, LimitTier.OBSERVATION);
        body.setObservationTargetDays(45);

        LimitTierResponseChecker extend = request(clientId, body)
            .assertText("/observationTargetDays", "45")
            .assertText("/observationTargetTransactions", "100")
            .assertText("/observationDaysAtRequest", "40")
            .assertText("/observationAllowedAtRequest", "0");
        approve(extend.changeId(), 1, true).assertStatus(200);

        Map<String, Object> tier = tierChecker.assertTier(clientId, LimitTier.OBSERVATION, 2);
        List<Map<String, Object>> entries = tierStore.tierEntries(clientId);
        assertAll(() -> assertEquals(45, ((Number)tier.get("observation_target_days")).intValue()),
            () -> assertEquals(1, entries.size(), "延长观察不替换名单成员"),
            () -> assertEquals(entryBefore, ((Number)entries.getFirst().get("entry_id")).longValue()));
        tierGet(REQUESTER, TIERS + "/clients/" + clientId + "/tier")
            .assertText("/observation/daysElapsed", "0")
            .assertText("/observation/targetDays", "45");
    }

    /** 无档位与不存在的申请：404；无档位 Client 的详情 tier 为空。 */
    @Test
    void test_noDataFound() {
        long clientId = newClientId();

        request(clientId, LimitTierRequestBuilder.goLive(1))
            .assertProblem(404, RiskLimitTierErrorCode.RISK_LIMIT_TIER_NOT_FOUND.getCode());
        approve(987654321L, 1, true)
            .assertProblem(404, RiskLimitTierErrorCode.RISK_LIMIT_TIER_CHANGE_NOT_FOUND.getCode());
        tierPost(REQUESTER, TIERS + "/tier-changes/987654321/withdrawal", LimitTierRequestBuilder.withdraw(1))
            .assertProblem(404, RiskLimitTierErrorCode.RISK_LIMIT_TIER_CHANGE_NOT_FOUND.getCode());
        tierGet(REQUESTER, TIERS + "/clients/" + clientId + "/tier").assertStatus(200)
            .assertText("/clientId", Long.toString(clientId)).assertText("/tier", null)
            .assertText("/unconfiguredCurrencyPolicy", null);
        tierChecker.assertNoTier(clientId);
    }

    /** 申请人提交变更申请。 */
    private LimitTierResponseChecker request(long clientId, RequestClientTierChangeRequestBody body) {
        return tierPost(REQUESTER, TIERS + "/clients/" + clientId + "/tier-changes", body);
    }

    /** 审批人批准；elevated 选择高级接口。 */
    private LimitTierResponseChecker approve(long changeId, int version, boolean elevated) {
        return tierPost(APPROVER, TIERS + "/tier-changes/" + changeId + (elevated ? "/elevated-approval" : "/approval"),
            LimitTierRequestBuilder.decide(version, null));
    }

    /** 审批人驳回；elevated 选择高级接口。 */
    private LimitTierResponseChecker reject(long changeId, int version, String comment, boolean elevated) {
        return tierPost(APPROVER, TIERS + "/tier-changes/" + changeId + (elevated ? "/elevated-rejection" : "/rejection"),
            LimitTierRequestBuilder.decide(version, comment));
    }
}
