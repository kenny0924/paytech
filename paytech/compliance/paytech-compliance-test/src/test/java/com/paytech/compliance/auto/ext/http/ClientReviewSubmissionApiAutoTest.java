package com.paytech.risk.auto.ext.http.compliance;

import static com.paytech.foundation.common.error.CommonErrorCode.BUSINESS_CHECK_FAILED;
import static com.paytech.foundation.common.error.CommonErrorCode.CONCURRENT_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.SERVICE_UNAVAILABLE;
import static com.paytech.foundation.common.error.CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.risk.api.enums.MaterialQualificationType.INDIVIDUAL_KYC;
import static com.paytech.risk.api.enums.MaterialQualificationType.ORGANIZATION_KYB;
import static com.paytech.risk.api.enums.MaterialQualificationType.RELATED_PERSON_KYC;
import static com.paytech.risk.test.builder.compliance.ClientReviewRegistrationRequestBuilder.IDENTITY_DOCUMENT;
import static com.paytech.risk.test.builder.compliance.ClientReviewRegistrationRequestBuilder.PASSPORT;
import static com.paytech.risk.test.builder.compliance.ClientReviewRegistrationRequestBuilder.PROOF_OF_ADDRESS;
import static com.paytech.risk.test.builder.compliance.ClientReviewRegistrationRequestBuilder.UTILITY_BILL;
import static com.paytech.risk.test.builder.compliance.ClientReviewRegistrationRequestBuilder.individual;
import static com.paytech.risk.test.builder.compliance.ClientReviewRegistrationRequestBuilder.organization;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.api.compliance.ClientReviewRegistrationApi;
import com.paytech.risk.api.compliance.request.ClientReviewRegistrationRequestBody;
import com.paytech.risk.api.enums.MaterialQualificationType;
import com.paytech.risk.test.base.MockTestBase;
import com.paytech.risk.test.builder.compliance.ClientReviewRegistrationRequestBuilder;
import com.paytech.risk.test.builder.compliance.MaterialProgramBuilder;
import com.paytech.risk.test.checker.compliance.ClientReviewCommandResponseChecker;
import com.paytech.risk.test.checker.compliance.ClientReviewStoreChecker;
import com.paytech.risk.test.mocker.compliance.ClientReviewMocker;
import com.paytech.risk.test.mocker.compliance.MaterialProgramMocker;
import com.paytech.risk.test.mocker.compliance.MaterialProgramMocker.MockedProgram;

import tools.jackson.databind.node.ObjectNode;

/**
 * I01 Client 入驻审核登记
 * <li>正常流程：组织客户 + 两名关联人员、个人客户；主/子任务、主体、材料要求、证据、依赖与初始状态变更完整落库，不发布结果。</li>
 * <li>重放：同一来源提交编号同内容（材料顺序不同也一样）返回原主任务，不新增任何行。</li>
 * <li>边界：同一人员多种关系按依赖类型去重；同一要求多份材料；非必需材料未提交保持 PENDING。</li>
 * <li>参数：字段格式由入口校验拒绝（422 并定位字段）；主体/关系/材料结构关系、时间与比例格式在登记前拒绝（400）；
 * 无法解析的报文 400、非 JSON 415。</li>
 * <li>业务规则：材料不属于当前通用程序 400；通用程序不可用 503；同编号改内容、受审主体已有进行中任务 409；拒绝时整体不落库。</li>
 *
 * @see ClientReviewRegistrationApi#register(String, ClientReviewRegistrationRequestBody)
 */
class ClientReviewSubmissionApiAutoTest extends MockTestBase {
    private static final String PATH = "/client-onboarding-review/v1/submissions";

    @Autowired
    private ClientReviewMocker reviews;

    @Autowired
    private MaterialProgramMocker materials;

    @Autowired
    private ClientReviewStoreChecker store;

    private Map<MaterialQualificationType, MockedProgram> programs;

    /** 三类资格的通用程序已批准并映射。 */
    @BeforeEach
    void setUpClassMocks() {
        programs = reviews.mockPrograms();
    }

    /** 组织客户：主任务 + 两个关联人员子任务，甲的 UBO 与 DIRECTOR 关系各成一种依赖。 */
    @Test
    void test_normalFlow_organization() {
        ClientReviewRegistrationRequestBuilder builder = organization();
        ClientReviewRegistrationRequestBody request = builder.build();

        long caseId = register(request).assertRegistered(request);

        store.assertRegistered(request, caseId, programs,
            Map.of(builder.partyId(1), List.of("UBO_KYC", "DIRECTOR_KYC"), builder.partyId(2), List.of("SIGNATORY_KYC")));
    }

    /** 个人客户：只有主任务，没有子任务与依赖。 */
    @Test
    void test_normalFlow_individual() {
        ClientReviewRegistrationRequestBody request = individual().build();

        long caseId = register(request).assertRegistered(request);

        store.assertRegistered(request, caseId, programs, Map.of());
    }

    /** 同一来源提交编号同内容重放返回原主任务，材料顺序不同也视为同内容，不新增任何行。 */
    @Test
    void test_normalFlow_replay() {
        ClientReviewRegistrationRequestBuilder builder = organization();
        ClientReviewRegistrationRequestBody request = builder.build();
        long caseId = register(request).assertRegistered(request);

        register(request).assertRegistered(request, caseId);
        Collections.reverse(request.getDocuments());
        register(request).assertRegistered(request, caseId);

        store.assertRegistered(request, caseId, programs,
            Map.of(builder.partyId(1), List.of("UBO_KYC", "DIRECTOR_KYC"), builder.partyId(2), List.of("SIGNATORY_KYC")));
    }

    /** 同一人员的 UBO、OWNERSHIP、CONTROL 都归为 UBO_KYC，只登记一条依赖。 */
    @Test
    void test_edgeCase_dependencyTypesDeduplicated() {
        ClientReviewRegistrationRequestBuilder builder = organization();
        builder.addRelation(builder.partyId(1), "OWNERSHIP", "40").addRelation(builder.partyId(1), "CONTROL", null)
            .addRelation(builder.partyId(2), "UBO", "25.5");
        ClientReviewRegistrationRequestBody request = builder.build();

        long caseId = register(request).assertRegistered(request);

        store.assertRegistered(request, caseId, programs, Map.of(builder.partyId(1), List.of("UBO_KYC", "DIRECTOR_KYC"),
            builder.partyId(2), List.of("SIGNATORY_KYC", "UBO_KYC")));
    }

    /** 每种关系类型映射到唯一的依赖类型：UBO/OWNERSHIP/CONTROL 为 UBO_KYC，DIRECTOR 与 SIGNATORY 各自对应。 */
    @ParameterizedTest(name = "{0} → {1}")
    @org.junit.jupiter.params.provider.CsvSource({"UBO,UBO_KYC", "OWNERSHIP,UBO_KYC", "CONTROL,UBO_KYC",
        "DIRECTOR,DIRECTOR_KYC", "SIGNATORY,SIGNATORY_KYC"})
    void test_edgeCase_relationTypeMapping(String relationType, String dependencyType) {
        ClientReviewRegistrationRequestBuilder builder = organization();
        builder.relation(2, r -> r.setRelationType(relationType));
        ClientReviewRegistrationRequestBody request = builder.build();

        long caseId = register(request).assertRegistered(request);

        store.assertRegistered(request, caseId, programs,
            Map.of(builder.partyId(1), List.of("UBO_KYC", "DIRECTOR_KYC"), builder.partyId(2), List.of(dependencyType)));
    }

    /** 同一要求两份材料各成一条证据；非必需材料提交后也为 RECEIVED，未提交的保持 PENDING。 */
    @Test
    void test_edgeCase_multipleDocuments() {
        ClientReviewRegistrationRequestBuilder builder = organization();
        builder.addDocument(builder.partyId(1), IDENTITY_DOCUMENT, PASSPORT)
            .addDocument(builder.partyId(1), PROOF_OF_ADDRESS, UTILITY_BILL);
        ClientReviewRegistrationRequestBody request = builder.build();

        long caseId = register(request).assertRegistered(request);

        store.assertRegistered(request, caseId, programs,
            Map.of(builder.partyId(1), List.of("UBO_KYC", "DIRECTOR_KYC"), builder.partyId(2), List.of("SIGNATORY_KYC")));
    }

    /** 主体没有任何材料时，其全部要求保持 PENDING。 */
    @Test
    void test_edgeCase_subjectWithoutDocuments() {
        ClientReviewRegistrationRequestBuilder builder = organization();
        builder.with(body -> body.getDocuments().remove(2));
        ClientReviewRegistrationRequestBody request = builder.build();

        long caseId = register(request).assertRegistered(request);

        store.assertRegistered(request, caseId, programs,
            Map.of(builder.partyId(1), List.of("UBO_KYC", "DIRECTOR_KYC"), builder.partyId(2), List.of("SIGNATORY_KYC")));
    }

    /** 字段格式由入口校验拒绝并定位字段，不落库。 */
    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("invalidFields")
    void test_invalidParameter(String field, String scenario, Supplier<ClientReviewRegistrationRequestBuilder> request) {
        ClientReviewRegistrationRequestBody body = request.get().build();

        register(body).assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError(field);

        store.assertNotRegistered(Long.parseLong(body.getApplicationId()));
    }

    /** 字段格式合法但主体、关系与材料的结构关系不成立，或时间、比例无法解析，登记前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidStructures")
    void test_invalidParameter_structure(String scenario, Supplier<ClientReviewRegistrationRequestBuilder> request) {
        ClientReviewRegistrationRequestBody body = request.get().build();

        register(body).assertProblem(400, INVALID_REQUEST.getCode());

        store.assertNotRegistered(Long.parseLong(body.getApplicationId()));
    }

    /** 无法绑定到请求合同的报文。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedBodies")
    void test_invalidParameter_malformedBody(String scenario, Function<ClientReviewRegistrationRequestBody, String> raw) {
        ClientReviewRegistrationRequestBody body = organization().build();

        invoke(headers(), raw.apply(body)).assertProblem(400, INVALID_REQUEST.getCode());

        store.assertNotRegistered(Long.parseLong(body.getApplicationId()));
    }

    /** 非 JSON 请求以 415 拒绝。 */
    @Test
    void test_invalidParameter_unsupportedMediaType() {
        ClientReviewRegistrationRequestBody body = organization().build();
        HttpHeaders headers = headers();
        headers.setContentType(MediaType.TEXT_PLAIN);

        invoke(headers, objectMapper.writeValueAsString(body)).assertProblem(415, UNSUPPORTED_MEDIA_TYPE.getCode());

        store.assertNotRegistered(Long.parseLong(body.getApplicationId()));
    }

    /** 材料要求代码不在通用程序中，或材料类型与程序不符：整体不落库。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("documentsOutsideProgram")
    void test_businessRuleViolation_documentOutsideProgram(String scenario,
        Supplier<ClientReviewRegistrationRequestBuilder> request) {
        ClientReviewRegistrationRequestBody body = request.get().build();

        register(body).assertProblem(400, BUSINESS_CHECK_FAILED.getCode());

        store.assertNotRegistered(Long.parseLong(body.getApplicationId()));
    }

    /** 任一受审资格的通用程序不可用时 503，不视为无需材料，整体不落库。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("unavailablePrograms")
    void test_businessRuleViolation_programUnavailable(String scenario, MaterialQualificationType broken,
        Function<MaterialProgramMocker, MockedProgram> replacement) {
        Map<MaterialQualificationType, MockedProgram> mapped = new java.util.EnumMap<>(programs);
        mapped.put(broken, replacement.apply(materials));
        materials.mapPrograms(mapped.values().toArray(MockedProgram[]::new));
        ClientReviewRegistrationRequestBody body = organization().build();

        register(body).assertProblem(503, SERVICE_UNAVAILABLE.getCode());

        store.assertNotRegistered(Long.parseLong(body.getApplicationId()));
    }

    /** 映射缺少关联人员资格时，个人客户仍可登记，组织客户不可用。 */
    @Test
    void test_businessRuleViolation_programMappingMissing() {
        materials.mapPrograms(programs.get(ORGANIZATION_KYB), programs.get(INDIVIDUAL_KYC));
        ClientReviewRegistrationRequestBody organizationRequest = organization().build();
        ClientReviewRegistrationRequestBody individualRequest = individual().build();

        register(organizationRequest).assertProblem(503, SERVICE_UNAVAILABLE.getCode());
        long caseId = register(individualRequest).assertRegistered(individualRequest);

        store.assertNotRegistered(Long.parseLong(organizationRequest.getApplicationId()));
        store.assertRegistered(individualRequest, caseId, programs, Map.of());
    }

    /** 映射为空数组时任何资格都不可用，不视为无需材料。 */
    @Test
    void test_businessRuleViolation_programMappingEmpty() {
        materials.mapRaw("[]");
        ClientReviewRegistrationRequestBody request = individual().build();

        register(request).assertProblem(503, SERVICE_UNAVAILABLE.getCode());

        store.assertNotRegistered(Long.parseLong(request.getApplicationId()));
    }

    /** 同一来源提交编号改内容：409，原任务与证据保持不变。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("changedContents")
    void test_businessRuleViolation_sameSubmissionDifferentContent(String scenario,
        java.util.function.Consumer<ClientReviewRegistrationRequestBody> change) {
        ClientReviewRegistrationRequestBuilder builder = organization();
        ClientReviewRegistrationRequestBody original = builder.build();
        long caseId = register(original).assertRegistered(original);
        ClientReviewRegistrationRequestBody changed = copy(original);
        change.accept(changed);

        register(changed).assertProblem(409, CONCURRENT_REQUEST.getCode());

        store.assertRegistered(original, caseId, programs,
            Map.of(builder.partyId(1), List.of("UBO_KYC", "DIRECTOR_KYC"), builder.partyId(2), List.of("SIGNATORY_KYC")));
    }

    /** 关联人员或客户已有进行中的审核任务：409 整体回滚，先登记的任务保持不变。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("subjectsUnderReview")
    void test_businessRuleViolation_subjectAlreadyUnderReview(String scenario,
        java.util.function.BiConsumer<ClientReviewRegistrationRequestBuilder, ClientReviewRegistrationRequestBuilder> reuse) {
        ClientReviewRegistrationRequestBuilder first = organization();
        long caseId = register(first.build()).assertRegistered(first.build());
        ClientReviewRegistrationRequestBuilder second = organization();
        reuse.accept(first, second);

        register(second.build()).assertProblem(409, CONCURRENT_REQUEST.getCode());

        store.assertNotRegistered(Long.parseLong(second.build().getApplicationId()));
        store.assertRegistered(first.build(), caseId, programs,
            Map.of(first.partyId(1), List.of("UBO_KYC", "DIRECTOR_KYC"), first.partyId(2), List.of("SIGNATORY_KYC")));
    }

    /** 逐字段的缺失、格式、长度负例；每例只改一个字段。 */
    static Stream<Arguments> invalidFields() {
        return Stream.of(invalid("sourceSubmissionId", "缺失", () -> organization().with(b -> b.setSourceSubmissionId(null))),
            invalid("sourceSubmissionId", "大写 UUID",
                () -> organization().with(b -> b.setSourceSubmissionId(UUID.randomUUID().toString().toUpperCase()))),
            invalid("applicationNo", "超过 64 位", () -> organization().with(b -> b.setApplicationNo("A".repeat(65)))),
            invalid("clientId", "零", () -> organization().with(b -> b.setClientId("0"))),
            invalid("clientId", "20 位", () -> organization().with(b -> b.setClientId("1".repeat(20)))),
            invalid("clientType", "未知类型", () -> organization().with(b -> b.setClientType("PARTNER"))),
            invalid("clientType", "小写", () -> organization().with(b -> b.setClientType("organization"))),
            invalid("requestedByAccountId", "非数字", () -> organization().with(b -> b.setRequestedByAccountId("ops"))),
            invalid("submittedAt", "缺失", () -> organization().with(b -> b.setSubmittedAt(null))),
            invalid("submittedAt", "超过 32 位", () -> organization().with(b -> b.setSubmittedAt("2".repeat(33)))),
            invalid("business", "缺失", () -> organization().with(b -> b.setBusiness(null))),
            invalid("business.businessModelCode", "空白", () -> organization().business(b -> b.setBusinessModelCode(" "))),
            invalid("business.websiteUrl", "超过 2048 位", () -> organization().business(b -> b.setWebsiteUrl("w".repeat(2049)))),
            invalid("business.businessDescription", "超过 2000 位",
                () -> organization().business(b -> b.setBusinessDescription("d".repeat(2001)))),
            invalid("business.expectedVolume", "缺失", () -> organization().business(b -> b.setExpectedVolume(null))),
            invalid("business.expectedTicket", "超过 25 位", () -> organization().business(b -> b.setExpectedTicket("9".repeat(26)))),
            invalid("business.currency", "小写", () -> organization().business(b -> b.setCurrency("usd"))),
            invalid("subjects", "空集合", () -> organization().with(b -> b.getSubjects().clear())),
            invalid("subjects", "超过 101 个", ClientReviewSubmissionApiAutoTest::tooManySubjects),
            invalid("subjects[0].partyId", "前导零", () -> organization().subject(0, s -> s.setPartyId("012"))),
            invalid("subjects[0].profileId", "缺失", () -> organization().subject(0, s -> s.setProfileId(null))),
            invalid("subjects[1].subjectRole", "未知角色", () -> organization().subject(1, s -> s.setSubjectRole("UBO"))),
            invalid("subjects[1].qualificationType", "未知资格",
                () -> organization().subject(1, s -> s.setQualificationType("ENTITY_UNDERWRITING"))),
            invalid("subjects[1].givenName", "超过 64 位", () -> organization().subject(1, s -> s.setGivenName("g".repeat(65)))),
            invalid("subjects[1].middleName", "超过 64 位", () -> organization().subject(1, s -> s.setMiddleName("m".repeat(65)))),
            invalid("subjects[1].familyName", "超过 64 位", () -> organization().subject(1, s -> s.setFamilyName("f".repeat(65)))),
            invalid("subjects[1].birthDate", "非日期格式", () -> organization().subject(1, s -> s.setBirthDate("01/05/1990"))),
            invalid("subjects[1].nationalityCountry", "三位", () -> organization().subject(1, s -> s.setNationalityCountry("GBR"))),
            invalid("subjects[1].residenceCountry", "小写", () -> organization().subject(1, s -> s.setResidenceCountry("ae"))),
            invalid("subjects[0].legalName", "超过 64 位", () -> organization().subject(0, s -> s.setLegalName("l".repeat(65)))),
            invalid("subjects[0].tradeName", "超过 64 位", () -> organization().subject(0, s -> s.setTradeName("t".repeat(65)))),
            invalid("subjects[0].incorporationCountry", "小写",
                () -> organization().subject(0, s -> s.setIncorporationCountry("ae"))),
            invalid("subjects[0].organizationType", "超过 64 位",
                () -> organization().subject(0, s -> s.setOrganizationType("o".repeat(65)))),
            invalid("subjects[0].incorporationDate", "非日期格式",
                () -> organization().subject(0, s -> s.setIncorporationDate("2020-1-15"))),
            invalid("subjects[0].addressLine1", "空白", () -> organization().subject(0, s -> s.setAddressLine1(" "))),
            invalid("subjects[0].addressLine2", "超过 255 位",
                () -> organization().subject(0, s -> s.setAddressLine2("a".repeat(256)))),
            invalid("subjects[0].city", "超过 128 位", () -> organization().subject(0, s -> s.setCity("c".repeat(129)))),
            invalid("subjects[0].stateOrProvince", "超过 128 位",
                () -> organization().subject(0, s -> s.setStateOrProvince("p".repeat(129)))),
            invalid("subjects[0].postalCode", "超过 32 位", () -> organization().subject(0, s -> s.setPostalCode("9".repeat(33)))),
            invalid("subjects[0].addressCountry", "缺失", () -> organization().subject(0, s -> s.setAddressCountry(null))),
            invalid("subjects[0].identifierType", "空白", () -> organization().subject(0, s -> s.setIdentifierType(" "))),
            invalid("subjects[0].issuingCountry", "数字", () -> organization().subject(0, s -> s.setIssuingCountry("12"))),
            invalid("subjects[0].identifierValue", "超过 128 位",
                () -> organization().subject(0, s -> s.setIdentifierValue("v".repeat(129)))),
            invalid("subjects[0].identityKeyId", "零", () -> organization().subject(0, s -> s.setIdentityKeyId("0"))),
            invalid("subjects[0].validFrom", "非日期格式", () -> organization().subject(0, s -> s.setValidFrom("2020"))),
            invalid("subjects[0].validTo", "非日期格式", () -> organization().subject(0, s -> s.setValidTo("tomorrow"))),
            invalid("relations", "缺失", () -> organization().with(b -> b.setRelations(null))),
            invalid("relations[0].relationId", "负数", () -> organization().relation(0, r -> r.setRelationId("-1"))),
            invalid("relations[0].relatedPartyId", "缺失", () -> organization().relation(0, r -> r.setRelatedPartyId(null))),
            invalid("relations[0].relationType", "未知关系", () -> organization().relation(0, r -> r.setRelationType("SPOUSE"))),
            invalid("relations[0].ownershipPercentage", "超过 32 位",
                () -> organization().relation(0, r -> r.setOwnershipPercentage("1".repeat(33)))),
            invalid("relations[0].effectiveFrom", "缺失", () -> organization().relation(0, r -> r.setEffectiveFrom(null))),
            invalid("relations[0].effectiveTo", "非日期格式", () -> organization().relation(0, r -> r.setEffectiveTo("2025"))),
            invalid("documents", "缺失", () -> organization().with(b -> b.setDocuments(null))),
            invalid("documents[0].documentId", "非数字", () -> organization().document(0, d -> d.setDocumentId("doc"))),
            invalid("documents[0].subjectPartyId", "零", () -> organization().document(0, d -> d.setSubjectPartyId("0"))),
            invalid("documents[0].requirementCode", "小写",
                () -> organization().document(0, d -> d.setRequirementCode("registration_certificate"))),
            invalid("documents[0].documentType", "以数字开头", () -> organization().document(0, d -> d.setDocumentType("1DOC"))),
            invalid("documents[0].fileId", "缺失", () -> organization().document(0, d -> d.setFileId(null))),
            invalid("documents[0].contentHash", "大写十六进制",
                () -> organization().document(0, d -> d.setContentHash("C".repeat(64)))),
            invalid("documents[0].contentHash", "63 位", () -> organization().document(0, d -> d.setContentHash("c".repeat(63)))));
    }

    /** 每例只破坏一种结构关系，其余字段合法。 */
    static Stream<Arguments> invalidStructures() {
        return Stream.of(
            structure("组织客户主主体为个人资格",
                () -> organization().subject(0, s -> s.setQualificationType(INDIVIDUAL_KYC.getCode()))),
            structure("个人客户主主体为组织资格",
                () -> individual().subject(0, s -> s.setQualificationType(ORGANIZATION_KYB.getCode()))),
            structure("第一个主体不是 PRIMARY", () -> organization().subject(0, s -> s.setSubjectRole("RELATED_PERSON"))),
            structure("关联人员角色为 PRIMARY", () -> organization().subject(1, s -> s.setSubjectRole("PRIMARY"))),
            structure("关联人员资格不是 RELATED_PERSON_KYC",
                () -> organization().subject(1, s -> s.setQualificationType(INDIVIDUAL_KYC.getCode()))),
            structure("个人客户带关联人员", ClientReviewSubmissionApiAutoTest::individualWithRelatedPerson),
            structure("主体 Party 重复", () -> {
                ClientReviewRegistrationRequestBuilder builder = organization();
                return builder.subject(2, s -> s.setPartyId(builder.partyId(1)));
            }),
            structure("关系指向主主体", () -> {
                ClientReviewRegistrationRequestBuilder builder = organization();
                return builder.relation(2, r -> r.setRelatedPartyId(builder.partyId(0)));
            }),
            structure("关系指向受审范围外的人员", () -> organization().relation(0, r -> r.setRelatedPartyId("999999999999999"))),
            structure("关联人员缺少关系", () -> organization().with(b -> b.getRelations().remove(2))),
            structure("材料所属主体不在受审范围",
                () -> organization().document(0, d -> d.setSubjectPartyId("999999999999999"))),
            structure("提交时间不是 ISO-8601", () -> organization().with(b -> b.setSubmittedAt("2026-10-13 08:00:00"))),
            structure("持股比例不是数字", () -> organization().relation(0, r -> r.setOwnershipPercentage("forty"))),
            structure("clientId 超出 signed BIGINT", () -> organization().with(b -> b.setClientId("9223372036854775808"))),
            structure("材料 fileId 超出 signed BIGINT", () -> organization().document(0, d -> d.setFileId("9223372036854775808"))));
    }

    /** 报文本身无法绑定到请求合同。 */
    static Stream<Arguments> malformedBodies() {
        return Stream.of(Arguments.of("非法 JSON", raw(body -> "{\"sourceSubmissionId\":")),
            Arguments.of("空报文", raw(body -> "")), Arguments.of("顶层为数组", raw(body -> "[]")),
            Arguments.of("未知字段", raw(body -> withField(body, "riskScore", "LOW"))),
            Arguments.of("subjects 为对象", raw(body -> withField(body, "subjects", "{}"))));
    }

    /** 材料不属于当前通用程序。 */
    static Stream<Arguments> documentsOutsideProgram() {
        return Stream.of(structure("要求代码不在程序中", () -> organization().document(0, d -> d.setRequirementCode("TAX"))),
            structure("材料类型与程序不符", () -> organization().document(1, d -> d.setDocumentType("NATIONAL_ID"))),
            structure("组织材料用于关联人员程序", () -> {
                ClientReviewRegistrationRequestBuilder builder = organization();
                return builder.document(0, d -> d.setSubjectPartyId(builder.partyId(1)));
            }));
    }

    /** 目标资格的程序被替换为一个不可用的程序。 */
    static Stream<Arguments> unavailablePrograms() {
        return Stream.of(
            Arguments.of("组织程序未批准", ORGANIZATION_KYB,
                program(m -> m.mock(MaterialProgramBuilder.approved(ORGANIZATION_KYB).approvedBy(null)))),
            Arguments.of("关联人员程序为 DRAFT", RELATED_PERSON_KYC,
                program(m -> m.mock(MaterialProgramBuilder.approved(RELATED_PERSON_KYC).programStatus("DRAFT")))),
            Arguments.of("关联人员程序没有材料要求", RELATED_PERSON_KYC,
                program(m -> m.mock(MaterialProgramBuilder.approved(RELATED_PERSON_KYC).requirements(List.of())))),
            Arguments.of("组织程序要求代码损坏", ORGANIZATION_KYB,
                program(m -> m.mock(MaterialProgramBuilder.approved(ORGANIZATION_KYB)
                    .addRequirement("registration_certificate", "COMPANY_REGISTRY_EXTRACT", 1)))));
    }

    /** 同一来源提交编号下改变受审主体或证据集合。 */
    static Stream<Arguments> changedContents() {
        return Stream.of(change("材料文件摘要不同", body -> body.getDocuments().getFirst().setContentHash("d".repeat(64))),
            change("材料文件不同", body -> body.getDocuments().getFirst().setFileId("999999999999999")),
            change("少一份材料", body -> body.getDocuments().removeLast()),
            change("客户 ID 不同", body -> body.setClientId("999999999999999")),
            change("少一名关联人员", body -> {
                String party = body.getSubjects().removeLast().getPartyId();
                body.getRelations().removeIf(relation -> relation.getRelatedPartyId().equals(party));
                body.getDocuments().removeIf(document -> document.getSubjectPartyId().equals(party));
            }));
    }

    /** 第二次提交复用第一次提交中仍在审核的主体。 */
    static Stream<Arguments> subjectsUnderReview() {
        return Stream.of(Arguments.of("关联人员已在审核",
            (java.util.function.BiConsumer<ClientReviewRegistrationRequestBuilder, ClientReviewRegistrationRequestBuilder>)(
                first, second) -> {
                String reused = first.partyId(1);
                String original = second.partyId(1);
                second.subject(1, s -> s.setPartyId(reused));
                second.with(b -> b.getRelations().stream().filter(r -> r.getRelatedPartyId().equals(original))
                    .forEach(r -> r.setRelatedPartyId(reused)));
                second.with(b -> b.getDocuments().stream().filter(d -> d.getSubjectPartyId().equals(original))
                    .forEach(d -> d.setSubjectPartyId(reused)));
            }),
            Arguments.of("同一客户已在审核",
                (java.util.function.BiConsumer<ClientReviewRegistrationRequestBuilder, ClientReviewRegistrationRequestBuilder>)(
                    first, second) -> second.with(b -> b.setClientId(first.build().getClientId()))));
    }

    /** 102 个主体，超过 1 + 100 名关联人员上限。 */
    private static ClientReviewRegistrationRequestBuilder tooManySubjects() {
        ClientReviewRegistrationRequestBuilder builder = organization();
        for (int i = 0; i < 99; i++) {
            String party = builder.addSubject("RELATED_PERSON", RELATED_PERSON_KYC.getCode(), false);
            builder.addRelation(party, "DIRECTOR", null);
        }
        return builder;
    }

    /** 个人客户再带一名关联人员及其关系。 */
    private static ClientReviewRegistrationRequestBuilder individualWithRelatedPerson() {
        ClientReviewRegistrationRequestBuilder builder = individual();
        String party = builder.addSubject("RELATED_PERSON", RELATED_PERSON_KYC.getCode(), false);
        return builder.addRelation(party, "DIRECTOR", null);
    }

    private static Arguments invalid(String field, String scenario, Supplier<ClientReviewRegistrationRequestBuilder> request) {
        return Arguments.of(field, scenario, request);
    }

    private static Arguments structure(String scenario, Supplier<ClientReviewRegistrationRequestBuilder> request) {
        return Arguments.of(scenario, request);
    }

    private static Arguments change(String scenario, java.util.function.Consumer<ClientReviewRegistrationRequestBody> change) {
        return Arguments.of(scenario, change);
    }

    private static Function<ClientReviewRegistrationRequestBody, String> raw(
        Function<ClientReviewRegistrationRequestBody, String> body) {
        return body;
    }

    private static Function<MaterialProgramMocker, MockedProgram> program(Function<MaterialProgramMocker, MockedProgram> f) {
        return f;
    }

    /** 合法请求序列化后设置一个字段：字符串值，或以 { 开头的原始 JSON。 */
    private static String withField(ClientReviewRegistrationRequestBody body, String name, String value) {
        ObjectNode node = new tools.jackson.databind.json.JsonMapper().valueToTree(body);
        if (value.startsWith("{")) {
            node.putObject(name);
        } else {
            node.put(name, value);
        }
        return node.toString();
    }

    /** 请求的深拷贝，用于构造同编号的改动内容。 */
    private ClientReviewRegistrationRequestBody copy(ClientReviewRegistrationRequestBody body) {
        return objectMapper.readValue(objectMapper.writeValueAsString(body), ClientReviewRegistrationRequestBody.class);
    }

    /** 以 CCT 私网调用方发送登记请求，申请 ID 合法时登记清理。 */
    private ClientReviewCommandResponseChecker register(ClientReviewRegistrationRequestBody body) {
        return invoke(headers(), body);
    }

    private ClientReviewCommandResponseChecker invoke(HttpHeaders headers, Object body) {
        if (body instanceof ClientReviewRegistrationRequestBody request && request.getApplicationId() != null
            && request.getApplicationId().matches("[1-9][0-9]{0,17}")) {
            reviews.track(Long.parseLong(request.getApplicationId()));
        }
        String requestId = headers.getFirst(ApiHeaderNames.REQUEST_ID);
        return new ClientReviewCommandResponseChecker(http.post(PATH, headers, body), objectMapper, requestId);
    }

    /** CCT 私网调用：内部 JSON 请求头加调用方标识。 */
    private static HttpHeaders headers() {
        HttpHeaders headers = TestRequestHeaders.internal("risk-review-" + UUID.randomUUID());
        headers.set("X-Caller-Service", "client-contract-service");
        return headers;
    }
}
