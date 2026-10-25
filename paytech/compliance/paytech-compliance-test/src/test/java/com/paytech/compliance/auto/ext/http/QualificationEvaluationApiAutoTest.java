package com.paytech.compliance.auto.ext.http;

import static com.paytech.compliance.api.enums.QualificationErrorCode.QUALIFICATION_INVALID;
import static com.paytech.compliance.api.enums.QualificationErrorCode.QUALIFICATION_NOT_FOUND;
import static com.paytech.compliance.core.enums.QualificationRoleType.CLIENT;
import static com.paytech.compliance.core.enums.QualificationRoleType.LOCAL_PARTNER;
import static com.paytech.compliance.core.enums.QualificationRoleType.OPERATING_ENTITY;
import static com.paytech.compliance.test.builder.ClientReviewRegistrationRequestBuilder.randomId;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;

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

import com.paytech.compliance.api.QualificationEvaluationApi;
import com.paytech.compliance.api.request.QualificationEvaluationRequestBody;
import com.paytech.compliance.core.enums.QualificationDecisionSource;
import com.paytech.compliance.core.enums.QualificationEvaluationStatus;
import com.paytech.compliance.core.enums.QualificationStatus;
import com.paytech.compliance.core.enums.QualificationType;
import com.paytech.compliance.test.base.ComplianceMockTestBase;
import com.paytech.compliance.test.builder.QualificationEvaluationRequestBuilder;
import com.paytech.compliance.test.builder.RoleQualificationBuilder;
import com.paytech.compliance.test.checker.QualificationResponseChecker;
import com.paytech.compliance.test.checker.QualificationResponseChecker.ExpectedResult;
import com.paytech.compliance.test.mocker.QualificationMocker;
import com.paytech.compliance.test.mocker.QualificationMocker.MockedQualification;
import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

/**
 * commercial Checkout 解析使用的批量资格判定
 * <li>七种实时状态：ELIGIBLE、NOT_REGISTERED、REJECTED、REVOKED、EXPIRED（status=EXPIRED 或 valid_to ≤ now）、NOT_YET_VALID、
 * SETTLEMENT_BLOCKED（仅 Client，ACTIVE 且在生效期的 SETTLEMENT_BLOCK）。</li>
 * <li>每种角色只看要求的资格类型；Client 两类资格择优；结果按请求顺序、重复主体只判定一次。</li>
 * <li>调用方：只允许 commercial-service，其他或缺失 404；主体 0 个或超过 50 个 422 VALIDATION_ERROR；角色标识格式不符 422。</li>
 *
 * @see QualificationEvaluationApi#evaluate(String, QualificationEvaluationRequestBody)
 */
class QualificationEvaluationApiAutoTest extends ComplianceMockTestBase {
    private static final String PATH = "/compliance/v1/qualifications/evaluations";

    private static final String COMMERCIAL = "commercial-service";

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private QualificationMocker qualifications;

    /** 三种角色都合格，未登记的主体返回 NOT_REGISTERED；结果顺序与请求一致。 */
    @Test
    void test_normalFlow_eligibleAndNotRegistered() {
        MockedQualification client = qualifications.mock(RoleQualificationBuilder.template());
        MockedQualification entity = qualifications.mock(RoleQualificationBuilder.of(OPERATING_ENTITY, "AE001",
            QualificationType.ENTITY_UNDERWRITING).validTo(NOW.plus(30, ChronoUnit.DAYS)));
        MockedQualification partner = qualifications.mock(RoleQualificationBuilder.of(LOCAL_PARTNER, Long.toString(randomId()),
            QualificationType.PARTNER_DUE_DILIGENCE));
        String unknownClient = Long.toString(randomId());
        Instant started = Instant.now();

        evaluate(QualificationEvaluationRequestBuilder.template().subject(CLIENT.getCode(), client.qualification().roleId())
            .subject(OPERATING_ENTITY.getCode(), "AE001").subject(LOCAL_PARTNER.getCode(), partner.qualification().roleId())
            .subject(CLIENT.getCode(), unknownClient).subject(OPERATING_ENTITY.getCode(), "AE999").build())
            .assertEvaluated(started, List.of(eligible(client), eligible(entity), eligible(partner),
                ExpectedResult.notRegistered(CLIENT.getCode(), unknownClient),
                ExpectedResult.notRegistered(OPERATING_ENTITY.getCode(), "AE999")));
    }

    /** 拒绝、撤销、过期（两种）、未生效各自返回对应状态，eligible=false。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("ineligibleStatuses")
    void test_businessRuleViolation_ineligibleStatus(String scenario, RoleQualificationBuilder builder,
        QualificationEvaluationStatus expected) {
        MockedQualification row = qualifications.mock(builder);

        Instant started = Instant.now();
        evaluate(QualificationEvaluationRequestBuilder.template().subject(row.qualification().roleType(),
            row.qualification().roleId()).build())
            .assertEvaluated(started, List.of(ExpectedResult.of(row.qualification(), expected)));
    }

    /** 资格有效的 Client 存在生效中的结算冻结：SETTLEMENT_BLOCKED；已释放、未生效或已过期的冻结不影响。 */
    @Test
    void test_businessRuleViolation_settlementBlocked() {
        MockedQualification blocked = qualifications.mock(RoleQualificationBuilder.template());
        MockedQualification released = qualifications.mock(RoleQualificationBuilder.template());
        MockedQualification future = qualifications.mock(RoleQualificationBuilder.template());
        MockedQualification ended = qualifications.mock(RoleQualificationBuilder.template());
        MockedQualification deposit = qualifications.mock(RoleQualificationBuilder.template());
        qualifications.mockSettlementControl(blocked.qualification().roleId(), "SETTLEMENT_BLOCK", "ACTIVE",
            NOW.minus(1, ChronoUnit.DAYS), null);
        qualifications.mockSettlementControl(released.qualification().roleId(), "SETTLEMENT_BLOCK", "RELEASED",
            NOW.minus(1, ChronoUnit.DAYS), null);
        qualifications.mockSettlementControl(future.qualification().roleId(), "SETTLEMENT_BLOCK", "ACTIVE",
            NOW.plus(1, ChronoUnit.DAYS), null);
        qualifications.mockSettlementControl(ended.qualification().roleId(), "SETTLEMENT_BLOCK", "ACTIVE",
            NOW.minus(2, ChronoUnit.DAYS), NOW.minus(1, ChronoUnit.DAYS));
        qualifications.mockSettlementControl(deposit.qualification().roleId(), "MIN_SECURITY_DEPOSIT", "ACTIVE",
            NOW.minus(1, ChronoUnit.DAYS), null);

        Instant started = Instant.now();
        evaluate(subjects(blocked, released, future, ended, deposit)).assertEvaluated(started, List.of(
            ExpectedResult.of(blocked.qualification(), QualificationEvaluationStatus.SETTLEMENT_BLOCKED), eligible(released),
            eligible(future), eligible(ended), eligible(deposit)));
    }

    /** 冻结只叠加在合格资格上：被撤销的 Client 即使有冻结也返回 REVOKED。 */
    @Test
    void test_edgeCase_blockDoesNotMaskIneligibleQualification() {
        MockedQualification revoked = qualifications.mock(RoleQualificationBuilder.template().status(QualificationStatus.REVOKED));
        qualifications.mockSettlementControl(revoked.qualification().roleId(), "SETTLEMENT_BLOCK", "ACTIVE",
            NOW.minus(1, ChronoUnit.DAYS), null);

        Instant started = Instant.now();
        evaluate(subjects(revoked)).assertEvaluated(started,
            List.of(ExpectedResult.of(revoked.qualification(), QualificationEvaluationStatus.REVOKED)));
    }

    /** Client 有两类资格时取合格的一类；角色不要求的资格类型不参与判定；重复主体只返回一次。 */
    @Test
    void test_edgeCase_selectionAndDeduplication() {
        String clientId = Long.toString(randomId());
        qualifications.mock(RoleQualificationBuilder.of(CLIENT, clientId, QualificationType.INDIVIDUAL_KYC)
            .status(QualificationStatus.REJECTED).updateTime(NOW.minus(1, ChronoUnit.MINUTES)));
        MockedQualification kyb = qualifications.mock(RoleQualificationBuilder.of(CLIENT, clientId,
            QualificationType.ORGANIZATION_KYB).decisionSource(QualificationDecisionSource.REVIEWER)
            .updateTime(NOW.minus(2, ChronoUnit.HOURS)));
        String partnerId = Long.toString(randomId());
        qualifications.mock(RoleQualificationBuilder.of(LOCAL_PARTNER, partnerId, QualificationType.ENTITY_UNDERWRITING));

        Instant started = Instant.now();
        evaluate(QualificationEvaluationRequestBuilder.template().subject(CLIENT.getCode(), clientId)
            .subject(LOCAL_PARTNER.getCode(), partnerId).subject(CLIENT.getCode(), clientId).build())
            .assertEvaluated(started, List.of(eligible(kyb), ExpectedResult.notRegistered(LOCAL_PARTNER.getCode(), partnerId)));
    }

    /** 多类资格都不合格时取最近更新的一行。 */
    @Test
    void test_edgeCase_latestIneligibleSelected() {
        String clientId = Long.toString(randomId());
        qualifications.mock(RoleQualificationBuilder.of(CLIENT, clientId, QualificationType.INDIVIDUAL_KYC)
            .status(QualificationStatus.REJECTED).updateTime(NOW.minus(2, ChronoUnit.HOURS)));
        MockedQualification latest = qualifications.mock(RoleQualificationBuilder.of(CLIENT, clientId,
            QualificationType.ORGANIZATION_KYB).status(QualificationStatus.REVOKED).updateTime(NOW.minus(1, ChronoUnit.MINUTES)));

        Instant started = Instant.now();
        evaluate(QualificationEvaluationRequestBuilder.template().subject(CLIENT.getCode(), clientId).build())
            .assertEvaluated(started, List.of(ExpectedResult.of(latest.qualification(), QualificationEvaluationStatus.REVOKED)));
    }

    /** 50 个主体是上限内的合法请求。 */
    @Test
    void test_edgeCase_fiftySubjects() {
        QualificationEvaluationRequestBuilder request = QualificationEvaluationRequestBuilder.template();
        List<ExpectedResult> expected = new java.util.ArrayList<>();
        for (int index = 0; index < 50; index++) {
            String clientId = Long.toString(randomId());
            request.subject(CLIENT.getCode(), clientId);
            expected.add(ExpectedResult.notRegistered(CLIENT.getCode(), clientId));
        }

        Instant started = Instant.now();
        evaluate(request.build()).assertEvaluated(started, expected);
    }

    /** 非 commercial-service 或缺少调用方：404，与资格不存在同一拒绝合同。 */
    @ParameterizedTest(name = "caller={0}")
    @MethodSource("rejectedCallers")
    void test_invalidParameter_caller(String caller) {
        HttpHeaders headers = TestRequestHeaders.internal(requestId());
        if (caller != null) {
            headers.set("X-Caller-Service", caller);
        }

        invoke(headers, QualificationEvaluationRequestBuilder.template().subject(CLIENT.getCode(), "1").build())
            .assertQualificationError(404, QUALIFICATION_NOT_FOUND);
    }

    /** 主体为空、超过 50 个、角色类型未知或标识为空：422 VALIDATION_ERROR。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void test_invalidParameter_body(String scenario, QualificationEvaluationRequestBody body) {
        evaluate(body).assertProblem(422, VALIDATION_ERROR.getCode());
    }

    /** 角色标识格式与角色不符：422 QUALIFICATION_INVALID。 */
    @ParameterizedTest(name = "{0}/{1}")
    @MethodSource("invalidRoleIds")
    void test_invalidParameter_roleIdFormat(String roleType, String roleId) {
        evaluate(QualificationEvaluationRequestBuilder.template().subject(roleType, roleId).build())
            .assertQualificationError(422, QUALIFICATION_INVALID);
    }

    static Stream<Arguments> ineligibleStatuses() {
        return Stream.of(
            Arguments.of("拒绝", RoleQualificationBuilder.template().status(QualificationStatus.REJECTED),
                QualificationEvaluationStatus.REJECTED),
            Arguments.of("撤销", RoleQualificationBuilder.template().status(QualificationStatus.REVOKED),
                QualificationEvaluationStatus.REVOKED),
            Arguments.of("状态已过期", RoleQualificationBuilder.template().status(QualificationStatus.EXPIRED),
                QualificationEvaluationStatus.EXPIRED),
            Arguments.of("有效期已结束", RoleQualificationBuilder.of(OPERATING_ENTITY, "AE002", QualificationType.ENTITY_UNDERWRITING)
                .validFrom(NOW.minus(10, ChronoUnit.DAYS)).validTo(NOW.minus(1, ChronoUnit.SECONDS)),
                QualificationEvaluationStatus.EXPIRED),
            Arguments.of("尚未生效", RoleQualificationBuilder.template().validFrom(NOW.plus(1, ChronoUnit.DAYS)),
                QualificationEvaluationStatus.NOT_YET_VALID));
    }

    static Stream<String> rejectedCallers() {
        return Stream.of(null, "operations-console-bff", "Commercial-Service");
    }

    static Stream<Arguments> invalidBodies() {
        QualificationEvaluationRequestBuilder tooMany = QualificationEvaluationRequestBuilder.template();
        for (int index = 0; index < 51; index++) {
            tooMany.subject(CLIENT.getCode(), Long.toString(index + 1));
        }
        QualificationEvaluationRequestBody empty = QualificationEvaluationRequestBuilder.template().build();
        QualificationEvaluationRequestBody missing = QualificationEvaluationRequestBuilder.template().build();
        missing.setSubjects(null);
        return Stream.of(Arguments.of("主体为空", empty), Arguments.of("主体缺失", missing), Arguments.of("51 个主体", tooMany.build()),
            Arguments.of("未知角色", QualificationEvaluationRequestBuilder.template().subject("RELATED_PERSON", "1").build()),
            Arguments.of("标识为空", QualificationEvaluationRequestBuilder.template().subject(CLIENT.getCode(), " ").build()));
    }

    static Stream<Arguments> invalidRoleIds() {
        return Stream.of(Arguments.of(OPERATING_ENTITY.getCode(), "123"), Arguments.of(OPERATING_ENTITY.getCode(), "ae001"),
            Arguments.of(OPERATING_ENTITY.getCode(), "AE0001"), Arguments.of(CLIENT.getCode(), "AE001"),
            Arguments.of(CLIENT.getCode(), "0"), Arguments.of(CLIENT.getCode(), "0123"),
            Arguments.of(LOCAL_PARTNER.getCode(), "9".repeat(19)), Arguments.of(LOCAL_PARTNER.getCode(), "-5"));
    }

    private static ExpectedResult eligible(MockedQualification row) {
        return ExpectedResult.of(row.qualification(), QualificationEvaluationStatus.ELIGIBLE);
    }

    private static QualificationEvaluationRequestBody subjects(MockedQualification... rows) {
        QualificationEvaluationRequestBuilder request = QualificationEvaluationRequestBuilder.template();
        for (MockedQualification row : rows) {
            request.subject(row.qualification().roleType(), row.qualification().roleId());
        }
        return request.build();
    }

    private QualificationResponseChecker evaluate(QualificationEvaluationRequestBody body) {
        HttpHeaders headers = TestRequestHeaders.internal(requestId());
        headers.set("X-Caller-Service", COMMERCIAL);
        return invoke(headers, body);
    }

    private QualificationResponseChecker invoke(HttpHeaders headers, Object body) {
        return new QualificationResponseChecker(http.post(PATH, headers, body), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }

    private static String requestId() {
        return "qualification-eval-" + UUID.randomUUID();
    }
}
