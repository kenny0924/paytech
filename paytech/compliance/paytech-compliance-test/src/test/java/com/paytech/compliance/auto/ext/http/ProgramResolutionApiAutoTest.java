package com.paytech.compliance.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.paytech.compliance.api.ProgramResolutionApi;
import com.paytech.compliance.test.base.ComplianceMockTestBase;
import com.paytech.compliance.test.builder.ProgramBuilder;
import com.paytech.compliance.test.checker.JsonResponseChecker;
import com.paytech.compliance.test.mapper.ProgramTestMapper;
import com.paytech.compliance.test.mocker.ProgramPackMocker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.JsonNode;

/**
 * CP-1 程序解析（AC-01 材料槽来源、AC-05 解析维度、AC-12 版本冻结前提、S-10 夹具与初始化 DML 同源）
 * <li>AE / SA / EG 程序来自仓库初始化 DML；解析结果的材料槽与允许类型和 DML 落库一致。</li>
 * <li>优先级：责任 PayFac 定制 &gt; 平台；辖区精确 &gt; 全局；同级多个、未批准或没有程序 503，不回退。</li>
 *
 * @see ProgramResolutionApi#resolve(String, String, String, String, String, String)
 */
class ProgramResolutionApiAutoTest extends ComplianceMockTestBase {
    @Autowired
    private ProgramPackMocker programs;

    @Autowired
    private ProgramTestMapper programMapper;

    /** 三国九个程序都能解析，材料槽与允许类型和初始化 DML 一致。 */
    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("countryPrograms")
    void test_normalFlow_countryPacks(String jurisdiction, String partyType, String roleType, String programCode) {
        programs.mockCountryPacks();

        JsonNode body = resolve(Map.of("jurisdiction", jurisdiction, "partyType", partyType, "roleType", roleType)).ok();

        Map<String, List<String>> docTypes = new HashMap<>();
        body.path("materials").forEach(material -> docTypes.put(material.path("requirementCode").asString(),
            listOf(material.path("documentTypes"))));
        assertAll(() -> assertEquals(programCode, body.path("programCode").asString()),
            () -> assertEquals(jurisdiction, body.path("jurisdictionCode").asString()),
            () -> assertEquals("STANDARD", body.path("tier").asString()),
            () -> assertTrue(body.path("fourEyesRequired").asBoolean()),
            () -> assertEquals(36, body.path("reviewIntervalMonths").asInt()),
            () -> assertEquals(programMapper.docTypes(programMapper.activeVersionId(programCode)), docTypes, "材料槽与 DML 不一致"),
            () -> assertEquals(Long.toString(programMapper.activeVersionId(programCode)), body.path("programVersionId").asString()),
            () -> assertEquals(3, body.path("screenings").size()),
            () -> assertTrue(body.path("checks").size() >= 1),
            () -> body.path("checks").forEach(check -> assertEquals("MANUAL", check.path("checkMode").asString())));
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> countryPrograms() {
        return Stream.of("AE", "SA", "EG").flatMap(country -> Stream.of(
            org.junit.jupiter.params.provider.Arguments.of(country, "ORGANIZATION", "CLIENT", country + "_CLIENT_ORGANIZATION_STANDARD"),
            org.junit.jupiter.params.provider.Arguments.of(country, "INDIVIDUAL", "CLIENT", country + "_CLIENT_INDIVIDUAL_STANDARD"),
            org.junit.jupiter.params.provider.Arguments.of(country, "INDIVIDUAL", "RELATED_PERSON", country + "_RELATED_PERSON_STANDARD")));
    }

    /** 机构 Client 程序的必需材料与可豁免性按 design 9.5：执照 / 登记、银行证明、线上展示必需；条件性材料可豁免。 */
    @Test
    void test_normalFlow_saOrganizationMaterials() {
        programs.mockCountryPacks();

        JsonNode body = resolve(Map.of("jurisdiction", "SA", "partyType", "ORGANIZATION", "roleType", "CLIENT", "tier", "STANDARD")).ok();

        Map<String, JsonNode> byCode = new HashMap<>();
        body.path("materials").forEach(material -> byCode.put(material.path("requirementCode").asString(), material));
        assertAll(() -> assertTrue(byCode.get("REGISTRATION").path("required").asBoolean()),
            () -> assertFalse(byCode.get("REGISTRATION").path("waivable").asBoolean()),
            () -> assertEquals(List.of("COMMERCIAL_REGISTRATION"), listOf(byCode.get("REGISTRATION").path("documentTypes"))),
            () -> assertTrue(byCode.get("BANK").path("required").asBoolean()),
            () -> assertEquals(180, byCode.get("BANK").path("maxAgeDays").asInt()),
            () -> assertFalse(byCode.get("CONSTITUTION").path("required").asBoolean()),
            () -> assertTrue(byCode.get("CONSTITUTION").path("waivable").asBoolean()),
            () -> assertEquals("PRIMARY", byCode.get("BANK").path("subjectRole").asString()));
    }

    /** 辖区精确程序优先于全局程序；没有辖区程序时回落全局程序。 */
    @Test
    void test_normalFlow_jurisdictionPrecedence() {
        programs.mockCountryPacks();
        programs.mock(ProgramBuilder.globalOrganization());

        assertEquals("SA_CLIENT_ORGANIZATION_STANDARD", organization("SA", null).ok().path("programCode").asString());
        JsonNode fallback = organization("ZZ", null).ok();
        assertAll(() -> assertEquals("GLOBAL_CLIENT_ORGANIZATION", fallback.path("programCode").asString()),
            () -> assertTrue(fallback.path("jurisdictionCode").isNull()));
    }

    /** 责任 PayFac 定制程序优先；其他 PayFac 或平台自营使用平台程序。 */
    @Test
    void test_normalFlow_ownerPrecedence() {
        programs.mockCountryPacks();
        programs.mock(ProgramBuilder.globalOrganization().code("SA_PAYFAC_ORGANIZATION").jurisdiction("SA").owner(700000000000001L));

        JsonNode owned = organization("SA", "700000000000001").ok();
        assertAll(() -> assertEquals("SA_PAYFAC_ORGANIZATION", owned.path("programCode").asString()),
            () -> assertEquals("700000000000001", owned.path("ownerPartnerId").asString()),
            () -> assertEquals("SA_CLIENT_ORGANIZATION_STANDARD", organization("SA", "700000000000002").ok().path("programCode").asString()),
            () -> assertEquals("SA_CLIENT_ORGANIZATION_STANDARD", organization("SA", null).ok().path("programCode").asString()));
    }

    /** 没有程序、同级歧义、版本未批准：503，不回退到其他程序。 */
    @Test
    void test_noDataFound() {
        organization("ZZ", null).assertProblem(503, "SERVICE_UNAVAILABLE");

        programs.mock(ProgramBuilder.zzOrganization().unapproved());
        organization("ZZ", null).assertProblem(503, "SERVICE_UNAVAILABLE");
    }

    /** 同一优先级两个全局程序：配置歧义 503。 */
    @Test
    void test_edgeCase_ambiguous() {
        programs.mock(ProgramBuilder.globalOrganization());
        programs.mock(ProgramBuilder.globalOrganization().code("GLOBAL_CLIENT_ORGANIZATION_2"));

        organization("ZZ", null).assertProblem(503, "SERVICE_UNAVAILABLE");
    }

    /** 参数非法、未知 Query、关联人员却是机构：400。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidQueries")
    void test_invalidParameter(Map<String, String> query) {
        programs.mockCountryPacks();

        resolve(query).assertStatus(400);
    }

    static Stream<Map<String, String>> invalidQueries() {
        return Stream.of(Map.of("jurisdiction", "sa", "partyType", "ORGANIZATION", "roleType", "CLIENT"),
            Map.of("jurisdiction", "SA", "partyType", "COMPANY", "roleType", "CLIENT"),
            Map.of("jurisdiction", "SA", "partyType", "ORGANIZATION", "roleType", "PARTNER"),
            Map.of("jurisdiction", "SA", "partyType", "ORGANIZATION", "roleType", "RELATED_PERSON"),
            Map.of("jurisdiction", "SA", "partyType", "ORGANIZATION", "roleType", "CLIENT", "tier", "PREMIUM"),
            Map.of("jurisdiction", "SA", "partyType", "ORGANIZATION", "roleType", "CLIENT", "responsiblePartnerId", "0"),
            Map.of("jurisdiction", "SA", "partyType", "ORGANIZATION", "roleType", "CLIENT", "country", "SA"));
    }

    /** 机构 Client 解析。 */
    JsonResponseChecker organization(String jurisdiction, String partner) {
        Map<String, String> query = new HashMap<>(Map.of("jurisdiction", jurisdiction, "partyType", "ORGANIZATION", "roleType", "CLIENT"));
        if (partner != null) {
            query.put("responsiblePartnerId", partner);
        }
        return resolve(query);
    }

    /** 调用解析接口。 */
    JsonResponseChecker resolve(Map<String, String> query) {
        StringBuilder path = new StringBuilder("/compliance/v1/programs/resolve?");
        query.forEach((key, value) -> path.append(key).append('=').append(value).append('&'));
        String requestId = "compliance-program-" + java.util.UUID.randomUUID();
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Caller-Service", "client-master-service");
        return new JsonResponseChecker(http.get(path.substring(0, path.length() - 1), headers), objectMapper, requestId);
    }

    /** JSON 数组转字符串列表。 */
    static List<String> listOf(JsonNode array) {
        List<String> values = new java.util.ArrayList<>();
        array.forEach(item -> values.add(item.asString()));
        return values;
    }
}
