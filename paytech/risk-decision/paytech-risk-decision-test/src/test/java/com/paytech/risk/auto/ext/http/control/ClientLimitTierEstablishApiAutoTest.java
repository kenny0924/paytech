package com.paytech.risk.auto.ext.http.control;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.control.core.enums.LimitTierChangeStatus;
import com.paytech.risk.control.core.enums.LimitTierSource;
import com.paytech.risk.control.core.enums.RiskListEntrySource;
import com.paytech.risk.control.core.enums.UnderwritingOutcome;
import com.paytech.risk.control.core.enums.UnderwritingRiskLevel;
import com.paytech.risk.decision.api.control.ClientLimitTierApi;
import com.paytech.risk.decision.api.control.ClientUnderwritingApi;
import com.paytech.risk.decision.api.control.request.AutoUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.request.MigrateClientTiersRequestBody;
import com.paytech.risk.decision.api.control.request.ReviewUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.request.SubmitUnderwritingRequestBody;
import com.paytech.risk.decision.api.enums.RiskLimitTierErrorCode;
import com.paytech.risk.test.base.AbstractLimitTierApiTest;
import com.paytech.risk.test.builder.control.LimitTierRequestBuilder;
import com.paytech.risk.test.builder.control.UnderwritingRequestBuilder;
import com.paytech.risk.test.checker.control.UnderwritingResponseChecker;

/**
 * 档位建档：承保批准（提交即决定、HIGH 复核、自动承保；首次或修订）时 Client 尚无档位则同一事务建 VERIFICATION；存量迁移设 STANDARD
 * <li>已有档位时批准（含修订）不改档位，承保拒绝不建档；建档记录来源与承保版本、名单成员来源与原因可追溯。</li>
 * <li>迁移只接受白名单调用方，已有档位跳过，重复执行幂等。</li>
 *
 * @see ClientUnderwritingApi#submitUnderwriting(String, String, String, SubmitUnderwritingRequestBody)
 * @see ClientUnderwritingApi#reviewUnderwriting(String, String, String, ReviewUnderwritingRequestBody)
 * @see ClientUnderwritingApi#autoUnderwrite(String, String, String, AutoUnderwritingRequestBody)
 * @see ClientLimitTierApi#migrateClientTiers(String, MigrateClientTiersRequestBody)
 */
class ClientLimitTierEstablishApiAutoTest extends AbstractLimitTierApiTest {
    /** 测试配置中迁移白名单的调用方。 */
    private static final String MIGRATION_CALLER = "release-operator";

    /** 迁移执行人账号。 */
    private static final long OPERATOR = 70301L;

    /** 承保员提交即批准：建 VERIFICATION，建档记录直接 APPROVED 并记承保版本，名单成员来源 TIER。 */
    @Test
    void test_normalFlow_submitApprovalEstablishesVerification() {
        long clientId = newClientId();

        long underwritingId = approved(clientId, UnderwritingRequestBuilder.input(UnderwritingRiskLevel.LOW)).id();

        Map<String, Object> tier = tierChecker.assertTier(clientId, LimitTier.VERIFICATION, 1);
        Map<String, Object> change = tierStore.changes(clientId).getFirst();
        Map<String, Object> entry = tierStore.tierEntries(clientId).getFirst();
        assertAll("建档事实",
            () -> assertEquals(LimitTierSource.UNDERWRITING_INIT.getCode(), tier.get("source_type")),
            () -> assertEquals("Acme Trading LLC", tier.get("client_display_name")),
            () -> assertEquals(UNDERWRITER, ((Number)tier.get("created_by")).longValue()),
            () -> assertEquals(change.get("change_id"), tier.get("source_change_id")),
            () -> assertEquals(LimitTierChangeStatus.APPROVED.getCode(), change.get("status")),
            () -> assertNull(change.get("from_tier")),
            () -> assertNull(change.get("requested_by")),
            () -> assertNull(change.get("pending_slot")),
            () -> assertEquals(underwritingId, ((Number)change.get("underwriting_id")).longValue()),
            () -> assertEquals(UnderwritingRiskLevel.LOW.getCode(), change.get("crr_snapshot")),
            () -> assertEquals(RiskListEntrySource.TIER.getCode(), entry.get("source_type")),
            () -> assertEquals("change:" + change.get("change_id"), entry.get("reason")));
    }

    /** HIGH 提交只进入待复核，不建档；复核批准时建档。 */
    @Test
    void test_normalFlow_highReviewEstablishesVerification() {
        long clientId = newClientId();
        UnderwritingResponseChecker draft = createDraft(clientId, UnderwritingRequestBuilder.input(UnderwritingRiskLevel.HIGH),
            UnderwritingRequestBuilder.decision());
        UnderwritingResponseChecker submitted = submit(draft.id(), draft.version(),
            UnderwritingRequestBuilder.input(UnderwritingRiskLevel.HIGH), UnderwritingRequestBuilder.decision(),
            UnderwritingOutcome.APPROVE, null);
        tierChecker.assertNoTier(clientId);

        post(RISK_LEAD, ROOT + "/underwritings/" + submitted.id() + "/review",
            UnderwritingRequestBuilder.review(submitted.version(), UnderwritingOutcome.APPROVE, null)).assertStatus(200);

        Map<String, Object> tier = tierChecker.assertTier(clientId, LimitTier.VERIFICATION, 1);
        assertEquals(RISK_LEAD, ((Number)tier.get("created_by")).longValue(), "建档人为复核人");
    }

    /** 自动承保生成 APPROVED 时同一事务建档。 */
    @Test
    void test_normalFlow_autoApprovalEstablishesVerification() {
        long clientId = newClientId();

        post(UNDERWRITER, ROOT + "/clients/" + clientId + "/auto-underwritings",
            UnderwritingRequestBuilder.auto(UnderwritingRequestBuilder.input())).assertStatus(201);

        tierChecker.assertTier(clientId, LimitTier.VERIFICATION, 1);
    }

    /** 修订批准（替代上一有效承保）时已有档位：不改档位，也不新增建档记录。 */
    @Test
    void test_edgeCase_revisionKeepsTier() {
        long clientId = newClientId();
        UnderwritingResponseChecker first = approved(clientId, UnderwritingRequestBuilder.input(UnderwritingRiskLevel.LOW));
        UnderwritingResponseChecker revision = post(UNDERWRITER, ROOT + "/underwritings/" + first.id() + "/revision",
            UnderwritingRequestBuilder.revise(first.version(), "年度复评", UnderwritingRequestBuilder.input()));
        submit(revision.id(), revision.version(), UnderwritingRequestBuilder.input(), UnderwritingRequestBuilder.decision(),
            UnderwritingOutcome.APPROVE, null).assertStatus(200);

        tierChecker.assertTier(clientId, LimitTier.VERIFICATION, 1);
        assertEquals(1, tierStore.changes(clientId).size(), "只有首次批准的建档记录");
    }

    /** REQ-04 上线前已批准、没有档位的 Client：修订批准时建 VERIFICATION，建档记录记修订版本。 */
    @Test
    void test_edgeCase_revisionEstablishesTierWhenAbsent() {
        long clientId = newClientId();
        UnderwritingResponseChecker first = approved(clientId, UnderwritingRequestBuilder.input(UnderwritingRiskLevel.LOW));
        tierMocker.mockApprovedBeforeTiers(clientId);
        UnderwritingResponseChecker revision = post(UNDERWRITER, ROOT + "/underwritings/" + first.id() + "/revision",
            UnderwritingRequestBuilder.revise(first.version(), "年度复评", UnderwritingRequestBuilder.input()));

        submit(revision.id(), revision.version(), UnderwritingRequestBuilder.input(), UnderwritingRequestBuilder.decision(),
            UnderwritingOutcome.APPROVE, null).assertStatus(200);

        Map<String, Object> tier = tierChecker.assertTier(clientId, LimitTier.VERIFICATION, 1);
        List<Map<String, Object>> changes = tierStore.changes(clientId);
        assertAll("修订批准建档",
            () -> assertEquals(LimitTierSource.UNDERWRITING_INIT.getCode(), tier.get("source_type")),
            () -> assertEquals(1, changes.size(), "只有本次建档记录"),
            () -> assertEquals(revision.id(), ((Number)changes.getFirst().get("underwriting_id")).longValue()),
            () -> assertEquals(List.of(LimitTier.VERIFICATION.listCode()), tierStore.activeTierLists(clientId)));
    }

    /** 承保拒绝不建档。 */
    @Test
    void test_businessRuleViolation_declineEstablishesNothing() {
        long clientId = newClientId();
        UnderwritingResponseChecker draft = createDraft(clientId, UnderwritingRequestBuilder.input(UnderwritingRiskLevel.LOW),
            UnderwritingRequestBuilder.decision());

        submit(draft.id(), draft.version(), UnderwritingRequestBuilder.input(UnderwritingRiskLevel.LOW),
            UnderwritingRequestBuilder.decision(), UnderwritingOutcome.DECLINE, "资料不全").assertStatus(200);

        tierChecker.assertNoTier(clientId);
    }

    /** 已迁移为 STANDARD 的 Client 首次承保批准：档位不变，承保照常批准。 */
    @Test
    void test_edgeCase_existingTierNotReinitialized() {
        long clientId = newClientId();
        migrate(MIGRATION_CALLER, LimitTierRequestBuilder.migrate(OPERATOR, LimitTierRequestBuilder.client(clientId)))
            .assertStatus(200);

        approved(clientId, UnderwritingRequestBuilder.input(UnderwritingRiskLevel.LOW)).assertStatus(200);

        tierChecker.assertTier(clientId, LimitTier.STANDARD, 1);
        assertEquals(1, tierStore.changes(clientId).size(), "只有迁移记录");
    }

    /** 迁移：新 Client 设为 STANDARD 并记执行人与理由；重复执行全部跳过；已有档位不改。 */
    @Test
    void test_normalFlow_migration() {
        long first = newClientId();
        long second = newClientId();
        long existing = newClientId();
        tierMocker.mockTier(existing, LimitTier.VERIFICATION);
        MigrateClientTiersRequestBody body = LimitTierRequestBuilder.migrate(OPERATOR, LimitTierRequestBuilder.client(first),
            LimitTierRequestBuilder.client(second), LimitTierRequestBuilder.client(existing));

        migrate(MIGRATION_CALLER, body).assertStatus(200)
            .assertText("/results/0/outcome", "CREATED")
            .assertText("/results/1/outcome", "CREATED")
            .assertText("/results/2/outcome", "SKIPPED_EXISTING");
        migrate(MIGRATION_CALLER, body).assertStatus(200)
            .assertText("/results/0/outcome", "SKIPPED_EXISTING")
            .assertText("/results/1/outcome", "SKIPPED_EXISTING");

        Map<String, Object> tier = tierChecker.assertTier(first, LimitTier.STANDARD, 1);
        Map<String, Object> change = tierStore.changes(first).getFirst();
        assertAll("迁移事实",
            () -> assertEquals(LimitTierSource.MIGRATION.getCode(), tier.get("source_type")),
            () -> assertEquals("Migrated Client " + first, tier.get("client_display_name")),
            () -> assertEquals(OPERATOR, ((Number)tier.get("created_by")).longValue()),
            () -> assertEquals(LimitTierSource.MIGRATION.getCode(), change.get("change_type")),
            () -> assertEquals(OPERATOR, ((Number)change.get("requested_by")).longValue()),
            () -> assertEquals("上线前存量可交易 Client 迁移", change.get("reason")),
            () -> assertEquals(1, tierStore.changes(first).size(), "重复执行不新增记录"));
        tierChecker.assertTier(second, LimitTier.STANDARD, 1);
        tierChecker.assertTier(existing, LimitTier.VERIFICATION, 1);
    }

    /** 迁移调用方缺失或不在白名单：403，不写任何档位。 */
    @Test
    void test_invalidParameter_migrationCallerForbidden() {
        long clientId = newClientId();
        MigrateClientTiersRequestBody body = LimitTierRequestBuilder.migrate(OPERATOR, LimitTierRequestBuilder.client(clientId));

        migrate(null, body).assertProblem(403, RiskLimitTierErrorCode.RISK_LIMIT_TIER_MIGRATION_FORBIDDEN.getCode());
        migrate("payment-service", body)
            .assertProblem(403, RiskLimitTierErrorCode.RISK_LIMIT_TIER_MIGRATION_FORBIDDEN.getCode());
        MigrateClientTiersRequestBody empty = LimitTierRequestBuilder.migrate(OPERATOR);
        migrate(MIGRATION_CALLER, empty).assertProblem(422, "VALIDATION_ERROR").assertFieldError("clients");

        tierChecker.assertNoTier(clientId);
        assertEquals(List.of(), tierStore.changes(clientId));
    }
}
