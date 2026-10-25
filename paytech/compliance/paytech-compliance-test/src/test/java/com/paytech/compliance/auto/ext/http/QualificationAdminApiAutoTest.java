package com.paytech.compliance.auto.ext.http;

import static com.paytech.compliance.api.enums.QualificationErrorCode.QUALIFICATION_CHANGED;
import static com.paytech.compliance.api.enums.QualificationErrorCode.QUALIFICATION_INVALID;
import static com.paytech.compliance.api.enums.QualificationErrorCode.QUALIFICATION_NOT_ACTIVE;
import static com.paytech.compliance.api.enums.QualificationErrorCode.QUALIFICATION_NOT_FOUND;
import static com.paytech.compliance.core.enums.QualificationRoleType.CLIENT;
import static com.paytech.compliance.core.enums.QualificationRoleType.LOCAL_PARTNER;
import static com.paytech.compliance.core.enums.QualificationRoleType.OPERATING_ENTITY;
import static com.paytech.compliance.test.builder.ClientReviewRegistrationRequestBuilder.randomId;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.paytech.compliance.api.QualificationAdminApi;
import com.paytech.compliance.api.request.QualificationManualDecisionRequestBody;
import com.paytech.compliance.api.request.QualificationRevocationRequestBody;
import com.paytech.compliance.core.enums.QualificationDecisionSource;
import com.paytech.compliance.core.enums.QualificationEvaluationStatus;
import com.paytech.compliance.core.enums.QualificationRoleType;
import com.paytech.compliance.core.enums.QualificationStatus;
import com.paytech.compliance.core.enums.QualificationType;
import com.paytech.compliance.service.processor.RecordManualQualificationProcessor;
import com.paytech.compliance.test.base.ComplianceMockTestBase;
import com.paytech.compliance.test.builder.QualificationManualDecisionRequestBuilder;
import com.paytech.compliance.test.builder.QualificationRevocationRequestBuilder;
import com.paytech.compliance.test.builder.RoleQualificationBuilder;
import com.paytech.compliance.test.checker.QualificationResponseChecker;
import com.paytech.compliance.test.checker.QualificationResponseChecker.ExpectedChange;
import com.paytech.compliance.test.checker.QualificationStoreChecker;
import com.paytech.compliance.test.mapper.QualificationTestMapper.QualificationRow;
import com.paytech.compliance.test.mapper.QualificationTestMapper.StateChangeRow;
import com.paytech.compliance.test.mocker.QualificationMocker;
import com.paytech.compliance.test.mocker.QualificationMocker.MockedQualification;
import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

/**
 * Operations 资格登记页：分页列表、历史、手工登记与撤销
 * <li>登记：首次新建版本 1；已有资格按页面版本条件切换为 APPROVED（NOT_REQUIRED），写 MANUAL 决定（case_id 为空、依据与原因代码）与
 * OPERATIONS_MANUAL 状态变更。版本缺失或不一致 409 QUALIFICATION_CHANGED；组合、标识格式或有效期不合法 422 QUALIFICATION_INVALID。</li>
 * <li>撤销：只允许 APPROVED，结果 REVOKED / COMPLETED、valid_to 为撤销时间；其他状态 409 NOT_ACTIVE，版本不符 409 CHANGED，不存在 404。</li>
 * <li>列表按更新时间倒序，附实时 effectiveStatus（含 Client 结算冻结）与当前决定来源；历史按版本倒序附决定的来源、操作人与依据。</li>
 * <li>被拒绝的命令不写任何行。</li>
 *
 * @see QualificationAdminApi#listQualifications(String, String, String, String, String, String, Integer, Integer)
 * @see QualificationAdminApi#getQualificationHistory(String, String, String)
 * @see QualificationAdminApi#recordManualDecision(String, String, QualificationManualDecisionRequestBody)
 * @see QualificationAdminApi#revokeQualification(String, String, String, QualificationRevocationRequestBody)
 */
class QualificationAdminApiAutoTest extends ComplianceMockTestBase {
    private static final String BASE = "/compliance-admin/v1/qualifications";

    private static final long ACTOR = 930000002L;

    private static final String MANUAL = QualificationDecisionSource.MANUAL.getCode();

    private static final String TRIGGER = RecordManualQualificationProcessor.TRIGGER_TYPE;

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private QualificationMocker qualifications;

    @Autowired
    private QualificationStoreChecker store;

    /** 三种角色首次登记：新建版本 1，MANUAL 决定不关联案件，响应与落库一致。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("allowedCombinations")
    void test_normalFlow_firstRegistration(QualificationRoleType roleType, String roleId, QualificationType type) {
        QualificationManualDecisionRequestBody request = QualificationManualDecisionRequestBuilder.of(roleType, roleId, type)
            .build();
        Instant started = Instant.now();

        QualificationResponseChecker response = record(request);

        QualificationRow row = store.assertChanged(manualApproved(request, null, 1, started));
        response.assertQualification(row, QualificationEvaluationStatus.ELIGIBLE, MANUAL);
    }

    /** 没有预定结束时间、生效时间在未来：登记成功，实时状态 NOT_YET_VALID。 */
    @Test
    void test_normalFlow_futureOpenEndedRegistration() {
        String validFrom = NOW.plus(3, ChronoUnit.DAYS).toString();
        QualificationManualDecisionRequestBody request = QualificationManualDecisionRequestBuilder.template().validFrom(validFrom)
            .validTo(null).build();

        QualificationResponseChecker response = record(request);

        QualificationRow row = store.assertChanged(manualApproved(request, null, 1, Instant.now().minusSeconds(5)));
        response.assertQualification(row, QualificationEvaluationStatus.NOT_YET_VALID, MANUAL);
    }

    /** 已有被撤销的资格：带当前版本重新登记，切换为 APPROVED，版本 +1，原状态记为 REVOKED。 */
    @Test
    void test_normalFlow_reRegisterExisting() {
        MockedQualification existing = qualifications.mock(RoleQualificationBuilder.template().status(QualificationStatus.REVOKED)
            .version(2));
        QualificationManualDecisionRequestBody request = QualificationManualDecisionRequestBuilder.of(CLIENT,
            existing.qualification().roleId(), QualificationType.ORGANIZATION_KYB).expectedVersion("2").build();
        Instant started = Instant.now();

        QualificationResponseChecker response = record(request);

        QualificationRow row = store.assertChanged(manualApproved(request, QualificationStatus.REVOKED, 3, started));
        assertEquals(existing.qualificationId(), row.qualificationId(), "沿用原资格行");
        response.assertQualification(row, QualificationEvaluationStatus.ELIGIBLE, MANUAL);
    }

    /** 已有资格但页面没有版本、版本过旧，或首次登记却带了版本：409，不写行。 */
    @Test
    void test_businessRuleViolation_versionConflict() {
        MockedQualification existing = qualifications.mock(RoleQualificationBuilder.template().version(3));
        String roleId = existing.qualification().roleId();
        long rows = store.rowCount();

        record(QualificationManualDecisionRequestBuilder.of(CLIENT, roleId, QualificationType.ORGANIZATION_KYB).build())
            .assertQualificationError(409, QUALIFICATION_CHANGED);
        record(QualificationManualDecisionRequestBuilder.of(CLIENT, roleId, QualificationType.ORGANIZATION_KYB)
            .expectedVersion("2").build()).assertQualificationError(409, QUALIFICATION_CHANGED);
        record(QualificationManualDecisionRequestBuilder.template().expectedVersion("1").build())
            .assertQualificationError(409, QUALIFICATION_CHANGED);

        store.assertUnchanged(existing);
        assertEquals(rows, store.rowCount(), "被拒绝的命令不写行");
    }

    /** 角色与资格类型组合不允许、标识格式不符或有效期倒置：422 QUALIFICATION_INVALID，不写行。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRegistrations")
    void test_businessRuleViolation_invalidRegistration(String scenario, QualificationManualDecisionRequestBuilder request) {
        record(request.build()).assertQualificationError(422, QUALIFICATION_INVALID);

        assertEquals(0, store.rowCount(), "被拒绝的命令不写行");
    }

    /** 请求字段格式非法：422 VALIDATION_ERROR 并定位字段；时间文本非法 400。 */
    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("invalidFields")
    void test_invalidParameter_registrationFields(String field, String scenario, QualificationManualDecisionRequestBuilder request) {
        record(request.build()).assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError(field);

        assertEquals(0, store.rowCount(), "被拒绝的命令不写行");
    }

    /** 时间不是 UTC ISO-8601 或超过六位小数：400 INVALID_REQUEST。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTimes")
    void test_invalidParameter_registrationTime(String value) {
        record(QualificationManualDecisionRequestBuilder.template().validFrom(value).build())
            .assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 撤销已批准资格：REVOKED / COMPLETED，valid_to 为撤销时间，版本 +1，实时状态 REVOKED。 */
    @Test
    void test_normalFlow_revoke() {
        MockedQualification existing = qualifications.mock(RoleQualificationBuilder.of(OPERATING_ENTITY, "AE001",
            QualificationType.ENTITY_UNDERWRITING).version(2));
        QualificationRevocationRequestBody request = QualificationRevocationRequestBuilder.template(2).build();
        Instant started = Instant.now();

        QualificationResponseChecker response = revoke(Long.toString(existing.qualificationId()), request);

        QualificationRow before = existing.qualification();
        QualificationRow row = store.assertChanged(new QualificationStoreChecker.ExpectedChange(before.roleType(), before.roleId(),
            before.qualificationType(), QualificationStatus.REVOKED, MANUAL, null, before.validFrom(), null,
            request.getReasonCode(), request.getBasisNote(), ACTOR, TRIGGER, QualificationStatus.APPROVED, 3, started));
        response.assertQualification(row, QualificationEvaluationStatus.REVOKED, MANUAL);
    }

    /** 撤销非 APPROVED 资格 409 NOT_ACTIVE；版本不符 409 CHANGED；不存在 404；均不写行。 */
    @Test
    void test_businessRuleViolation_revocation() {
        MockedQualification rejected = qualifications.mock(RoleQualificationBuilder.template().status(QualificationStatus.REJECTED));
        MockedQualification approved = qualifications.mock(RoleQualificationBuilder.template().version(4));

        revoke(Long.toString(rejected.qualificationId()), QualificationRevocationRequestBuilder.template(1).build())
            .assertQualificationError(409, QUALIFICATION_NOT_ACTIVE);
        revoke(Long.toString(approved.qualificationId()), QualificationRevocationRequestBuilder.template(3).build())
            .assertQualificationError(409, QUALIFICATION_CHANGED);
        revoke("999999999999999", QualificationRevocationRequestBuilder.template(1).build())
            .assertQualificationError(404, QUALIFICATION_NOT_FOUND);

        store.assertUnchanged(rejected);
        store.assertUnchanged(approved);
    }

    /** 撤销请求与路径参数非法：422 字段错误 / 400。 */
    @Test
    void test_invalidParameter_revocation() {
        MockedQualification approved = qualifications.mock(RoleQualificationBuilder.template());
        String id = Long.toString(approved.qualificationId());

        revoke(id, QualificationRevocationRequestBuilder.template(1).expectedVersion(null).build())
            .assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError("expectedVersion");
        revoke(id, QualificationRevocationRequestBuilder.template(1).basisNote(" ").build())
            .assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError("basisNote");
        revoke(id, QualificationRevocationRequestBuilder.template(1).reasonCode("revoked").build())
            .assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError("reasonCode");
        revoke("abc", QualificationRevocationRequestBuilder.template(1).build()).assertProblem(400, INVALID_REQUEST.getCode());

        store.assertUnchanged(approved);
    }

    /** 列表按更新时间倒序，筛选与分页；effectiveStatus 叠加 Client 结算冻结，附当前决定来源。 */
    @Test
    void test_normalFlow_list() {
        MockedQualification oldest = qualifications.mock(RoleQualificationBuilder.template().updateTime(NOW.minus(3, ChronoUnit.HOURS)));
        MockedQualification blocked = qualifications.mock(RoleQualificationBuilder.template()
            .decisionSource(QualificationDecisionSource.REVIEWER).updateTime(NOW.minus(2, ChronoUnit.HOURS)));
        MockedQualification expired = qualifications.mock(RoleQualificationBuilder.of(OPERATING_ENTITY, "AE001",
            QualificationType.ENTITY_UNDERWRITING).validFrom(NOW.minus(5, ChronoUnit.DAYS)).validTo(NOW.minus(1, ChronoUnit.DAYS))
            .updateTime(NOW.minus(1, ChronoUnit.HOURS)));
        MockedQualification revoked = qualifications.mock(RoleQualificationBuilder.of(LOCAL_PARTNER, Long.toString(randomId()),
            QualificationType.PARTNER_DUE_DILIGENCE).status(QualificationStatus.REVOKED).updateTime(NOW.minus(1, ChronoUnit.MINUTES)));
        qualifications.mockSettlementControl(blocked.qualification().roleId(), "SETTLEMENT_BLOCK", "ACTIVE",
            NOW.minus(1, ChronoUnit.DAYS), null);

        list("").assertPage(1, 10, 4, List.of(revoked.qualificationId(), expired.qualificationId(), blocked.qualificationId(),
            oldest.qualificationId())).assertPageItem(0, revoked.qualification(), QualificationEvaluationStatus.REVOKED, MANUAL)
            .assertPageItem(1, expired.qualification(), QualificationEvaluationStatus.EXPIRED, MANUAL)
            .assertPageItem(2, blocked.qualification(), QualificationEvaluationStatus.SETTLEMENT_BLOCKED,
                QualificationDecisionSource.REVIEWER.getCode())
            .assertPageItem(3, oldest.qualification(), QualificationEvaluationStatus.ELIGIBLE, MANUAL);
        list("?roleType=CLIENT&status=APPROVED&page=1&size=20").assertPage(1, 20, 2,
            List.of(blocked.qualificationId(), oldest.qualificationId()));
        list("?roleType=OPERATING_ENTITY&roleId=AE001&qualificationType=ENTITY_UNDERWRITING")
            .assertPage(1, 10, 1, List.of(expired.qualificationId()));
        list("?page=2&size=10").assertPage(2, 10, 4, List.of());
    }

    /** 列表参数非法：roleId 缺少 roleType 或格式不符 422；未知 Code 与非法分页 400。 */
    @Test
    void test_invalidParameter_list() {
        list("?roleId=AE001").assertQualificationError(422, QUALIFICATION_INVALID);
        list("?roleType=OPERATING_ENTITY&roleId=123").assertQualificationError(422, QUALIFICATION_INVALID);
        list("?roleType=RELATED_PERSON").assertProblem(400, INVALID_REQUEST.getCode());
        list("?status=approved").assertProblem(400, INVALID_REQUEST.getCode());
        list("?qualificationType=RELATED_PERSON_KYC").assertProblem(400, INVALID_REQUEST.getCode());
        list("?size=15").assertProblem(400, INVALID_REQUEST.getCode());
        list("?page=0").assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 登记后撤销：历史按版本倒序，附决定来源、操作人、依据、原因码与时间；不存在 404。 */
    @Test
    void test_normalFlow_history() {
        QualificationManualDecisionRequestBody request = QualificationManualDecisionRequestBuilder.template().build();
        long qualificationId = record(request).qualificationId();
        revoke(Long.toString(qualificationId), QualificationRevocationRequestBuilder.template(1).build())
            .assertQualification(store.qualification(qualificationId), QualificationEvaluationStatus.REVOKED, MANUAL);

        List<StateChangeRow> changes = store.stateChanges(qualificationId);
        assertEquals(2, changes.size(), "登记与撤销各一条状态变更");
        history(Long.toString(qualificationId)).assertHistory(qualificationId, List.of(
            new ExpectedChange(changes.get(1), store.decision(changes.get(1).decisionId())),
            new ExpectedChange(changes.get(0), store.decision(changes.get(0).decisionId()))));
        history("999999999999999").assertQualificationError(404, QUALIFICATION_NOT_FOUND);
        history("0").assertProblem(400, INVALID_REQUEST.getCode());
    }

    static Stream<Arguments> allowedCombinations() {
        return Stream.of(Arguments.of(CLIENT, Long.toString(randomId()), QualificationType.INDIVIDUAL_KYC),
            Arguments.of(CLIENT, Long.toString(randomId()), QualificationType.ORGANIZATION_KYB),
            Arguments.of(OPERATING_ENTITY, "AE001", QualificationType.ENTITY_UNDERWRITING),
            Arguments.of(LOCAL_PARTNER, Long.toString(randomId()), QualificationType.PARTNER_DUE_DILIGENCE));
    }

    static Stream<Arguments> invalidRegistrations() {
        String client = Long.toString(randomId());
        return Stream.of(
            Arguments.of("Client 尽调", QualificationManualDecisionRequestBuilder.of(CLIENT, client,
                QualificationType.PARTNER_DUE_DILIGENCE)),
            Arguments.of("OE KYB", QualificationManualDecisionRequestBuilder.of(OPERATING_ENTITY, "AE001",
                QualificationType.ORGANIZATION_KYB)),
            Arguments.of("Partner 承保", QualificationManualDecisionRequestBuilder.of(LOCAL_PARTNER, client,
                QualificationType.ENTITY_UNDERWRITING)),
            Arguments.of("OE 数值标识", QualificationManualDecisionRequestBuilder.of(OPERATING_ENTITY, "12345",
                QualificationType.ENTITY_UNDERWRITING)),
            Arguments.of("Client 字母标识", QualificationManualDecisionRequestBuilder.of(CLIENT, "AE001",
                QualificationType.ORGANIZATION_KYB)),
            Arguments.of("结束早于开始", QualificationManualDecisionRequestBuilder.template().validTo("2025-12-31T00:00:00Z")),
            Arguments.of("结束等于开始", QualificationManualDecisionRequestBuilder.template()
                .validTo(QualificationManualDecisionRequestBuilder.VALID_FROM)));
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
            Arguments.of("roleType", "未知角色", QualificationManualDecisionRequestBuilder.template().roleType("RELATED_PERSON")),
            Arguments.of("roleId", "缺失", QualificationManualDecisionRequestBuilder.template().roleId(null)),
            Arguments.of("qualificationType", "未知类型",
                QualificationManualDecisionRequestBuilder.template().qualificationType("RELATED_PERSON_KYC")),
            Arguments.of("validFrom", "缺失", QualificationManualDecisionRequestBuilder.template().validFrom(null)),
            Arguments.of("reasonCode", "小写", QualificationManualDecisionRequestBuilder.template().reasonCode("verified")),
            Arguments.of("basisNote", "缺失", QualificationManualDecisionRequestBuilder.template().basisNote(null)),
            Arguments.of("basisNote", "超过 1000 位",
                QualificationManualDecisionRequestBuilder.template().basisNote("b".repeat(1001))),
            Arguments.of("expectedVersion", "零", QualificationManualDecisionRequestBuilder.template().expectedVersion("0")));
    }

    static Stream<String> invalidTimes() {
        return Stream.of("2026-01-01", "2026-01-01T00:00:00", "2026-01-01T00:00:00.0000001Z");
    }

    /** 手工登记的期望：结论 APPROVED，复审 NOT_REQUIRED。 */
    private static QualificationStoreChecker.ExpectedChange manualApproved(QualificationManualDecisionRequestBody request,
        QualificationStatus fromStatus, long version, Instant started) {
        return new QualificationStoreChecker.ExpectedChange(request.getRoleType(), request.getRoleId(),
            request.getQualificationType(), QualificationStatus.APPROVED, MANUAL, null, Instant.parse(request.getValidFrom()),
            request.getValidTo() == null ? null : Instant.parse(request.getValidTo()), request.getReasonCode(),
            request.getBasisNote(), ACTOR, TRIGGER, fromStatus, version, started);
    }

    private QualificationResponseChecker record(QualificationManualDecisionRequestBody body) {
        HttpHeaders headers = operations();
        return checker(http.post(BASE + "/manual-decisions", headers, body), headers);
    }

    private QualificationResponseChecker revoke(String qualificationId, QualificationRevocationRequestBody body) {
        HttpHeaders headers = operations();
        return checker(http.post(BASE + "/" + qualificationId + "/revocation", headers, body), headers);
    }

    private QualificationResponseChecker list(String query) {
        HttpHeaders headers = operations();
        return checker(http.get(BASE + query, headers), headers);
    }

    private QualificationResponseChecker history(String qualificationId) {
        HttpHeaders headers = operations();
        return checker(http.get(BASE + "/" + qualificationId + "/history", headers), headers);
    }

    private QualificationResponseChecker checker(org.springframework.test.web.servlet.client.EntityExchangeResult<byte[]> result,
        HttpHeaders headers) {
        return new QualificationResponseChecker(result, objectMapper, headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }

    /** Gateway 转发的 Operations 可信身份，操作者即决定人。 */
    private static HttpHeaders operations() {
        HttpHeaders headers = TestRequestHeaders.internal("qualification-admin-" + UUID.randomUUID());
        headers.set("X-Gateway-Actor-Subject", Long.toString(ACTOR));
        headers.set("X-Gateway-Session-Id", "qualification-test-session");
        return headers;
    }
}
