package com.paytech.risk.auto.ext.http.compliance;

import static com.paytech.risk.test.builder.compliance.MaterialProgramBuilder.approved;
import static com.paytech.risk.test.mocker.compliance.MaterialProgramMocker.row;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.api.compliance.MaterialRequirementsApi;
import com.paytech.risk.test.base.MockTestBase;
import com.paytech.risk.test.builder.compliance.MaterialProgramBuilder;
import com.paytech.risk.test.builder.compliance.MaterialProgramBuilder.Requirement;
import com.paytech.risk.test.checker.compliance.MaterialRequirementsResponseChecker;
import com.paytech.risk.test.mocker.compliance.MaterialProgramMocker;
import com.paytech.risk.test.mocker.compliance.MaterialProgramMocker.MockedProgram;

/**
 * 通用材料目录查询
 * <li>正常流程：三种资格类型各自读取映射程序的唯一生效修订，材料要求按要求代码排序且禁止缓存。</li>
 * <li>未配置、映射非法、程序/修订不是已批准生效的权威、材料闭包损坏，一律 503，不回退为无需材料。</li>
 * <li>参数：资格类型缺失或非法、未知/重复参数、携带 Body 返回 400。</li>
 * <li>每个 503/400 负例都为目标资格准备了合法程序与映射，只引入一个缺陷，避免被其他规则掩盖。</li>
 *
 * @see MaterialRequirementsApi#read(String, String)
 */
class MaterialRequirementsApiAutoTest extends MockTestBase {
    private static final String PATH = "/internal/due-diligence/material-requirements";

    private static final String CALLER = "client-contract-service";

    /** 模板要求按要求代码排序后的期望输出。 */
    private static final List<Requirement> TEMPLATE_ITEMS = List.of(new Requirement("IDENTITY_DOCUMENT", "PASSPORT", 1),
        new Requirement("PROOF_OF_ADDRESS", "UTILITY_BILL", 0));

    @Autowired
    private MaterialProgramMocker materials;

    /** 每种资格类型读取其映射程序的完整目录。 */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"INDIVIDUAL_KYC", "ORGANIZATION_KYB", "RELATED_PERSON_KYC"})
    void test_normalFlow(String qualificationType) {
        MockedProgram program = materials.mock(approved(qualificationType));
        materials.mapPrograms(program);

        read(qualificationType).assertCatalog(program, TEMPLATE_ITEMS);
    }

    /** 三种资格同时配置时互不串读，各自返回自己的程序。 */
    @Test
    void test_normalFlow_eachTypeReadsItsOwnProgram() {
        MockedProgram individual = materials.mock(approved("INDIVIDUAL_KYC"));
        MockedProgram organization = materials.mock(approved("ORGANIZATION_KYB")
            .requirements(List.of(new Requirement("REGISTRATION_CERTIFICATE", "COMPANY_REGISTRY_EXTRACT", 1))));
        MockedProgram related = materials.mock(approved("RELATED_PERSON_KYC")
            .requirements(List.of(new Requirement("IDENTITY_DOCUMENT", "NATIONAL_ID", 0))));
        materials.mapPrograms(individual, organization, related);

        read("INDIVIDUAL_KYC").assertCatalog(individual, TEMPLATE_ITEMS);
        read("ORGANIZATION_KYB").assertCatalog(organization, organization.requirements());
        read("RELATED_PERSON_KYC").assertCatalog(related, related.requirements());
    }

    /** 历史修订与非 1 序号的生效修订：只返回唯一 ACTIVE 修订。 */
    @Test
    void test_edgeCase_retiredHistoryIsIgnored() {
        MockedProgram program = materials.mock(approved("INDIVIDUAL_KYC").versionNo(2));
        materials.mockExtraVersion(program, 1, "RETIRED", null);
        materials.mapPrograms(program);

        read("INDIVIDUAL_KYC").assertCatalog(program, TEMPLATE_ITEMS);
    }

    /** 材料要求达到 1024 条上限时完整返回并保持排序。 */
    @Test
    void test_edgeCase_maximumRequirements() {
        List<Requirement> sorted = requirements(1024);
        MockedProgram program = materials.mock(approved("INDIVIDUAL_KYC").requirements(sorted.reversed()));
        materials.mapPrograms(program);

        read("INDIVIDUAL_KYC").assertCatalog(program, sorted);
    }

    /** 映射缺失、指向不存在的程序，或程序资格类型与映射不一致时不可用。 */
    @Test
    void test_noDataFound() {
        MockedProgram individual = materials.mock(approved("INDIVIDUAL_KYC"));
        MockedProgram organization = materials.mock(approved("ORGANIZATION_KYB"));

        materials.mapPrograms(organization);
        read("INDIVIDUAL_KYC").assertUnavailable();

        materials.mapRaw(json(List.of(row("INDIVIDUAL_KYC", "MOCK_MATERIAL_MISSING"))));
        read("INDIVIDUAL_KYC").assertUnavailable();

        materials.mapRaw(json(List.of(row("INDIVIDUAL_KYC", organization.programCode()))));
        read("INDIVIDUAL_KYC").assertUnavailable();

        materials.mapPrograms(individual);
        read("INDIVIDUAL_KYC").assertCatalog(individual, TEMPLATE_ITEMS);
    }

    /** 映射配置任一处非法都使整份映射失效，即便目标资格本身配置正确。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidMappings")
    void test_invalidMapping(String scenario, Function<String, String> mapping) {
        MockedProgram program = materials.mock(approved("INDIVIDUAL_KYC"));
        materials.mapRaw(mapping.apply(program.programCode()));

        read("INDIVIDUAL_KYC").assertUnavailable();
    }

    /** 程序或修订不是已批准且已生效的唯一权威时不可用。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPrograms")
    void test_businessRuleViolation_invalidProgram(String scenario, Consumer<MaterialProgramBuilder> change) {
        MaterialProgramBuilder builder = approved("INDIVIDUAL_KYC");
        change.accept(builder);
        materials.mapPrograms(materials.mock(builder));

        read("INDIVIDUAL_KYC").assertUnavailable();
    }

    /** 同一程序存在两个 ACTIVE 修订时不选第一条掩盖目录损坏。 */
    @Test
    void test_businessRuleViolation_ambiguousActiveVersion() {
        MockedProgram program = materials.mock(approved("INDIVIDUAL_KYC"));
        materials.mockExtraVersion(program, 2, "ACTIVE", null);
        materials.mapPrograms(program);

        read("INDIVIDUAL_KYC").assertUnavailable();
    }

    /** 材料闭包为空、超限或任一条损坏时不可用。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequirements")
    void test_businessRuleViolation_invalidRequirements(String scenario, List<Requirement> requirements) {
        materials.mapPrograms(materials.mock(approved("INDIVIDUAL_KYC").requirements(requirements)));

        read("INDIVIDUAL_KYC").assertUnavailable();
    }

    /** 查询参数不合法或携带 Body 时拒绝；目标资格本身已正确配置。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidQueries")
    void test_invalidParameter(String scenario, String query, String body) {
        materials.mapPrograms(materials.mock(approved("INDIVIDUAL_KYC")));

        invoke(query, body).assertProblem(400, "INVALID_REQUEST");
    }

    /** 每个用例都含目标资格的合法映射行，只额外引入一个缺陷；纯结构用例天然没有可用行。 */
    static Stream<Arguments> invalidMappings() {
        return Stream.of(mapping("空字符串", code -> ""), mapping("JSON null", code -> "null"),
            mapping("空对象", code -> "{}"), mapping("空数组", code -> "[]"),
            mapping("顶层为对象（值是合法行）", code -> "{\"row\":" + validRow(code) + "}"),
            mapping("数组元素为数字", code -> "[" + validRow(code) + ",1]"),
            mapping("数组元素为空对象", code -> "[" + validRow(code) + ",{}]"),
            mapping("其他行缺少 programCode",
                code -> "[" + validRow(code) + ",{\"qualificationType\":\"RELATED_PERSON_KYC\"}]"),
            mapping("其他行 qualificationType 非字符串",
                code -> "[" + validRow(code) + ",{\"qualificationType\":1,\"programCode\":\"OTHER_PROGRAM\"}]"),
            mapping("其他行 programCode 非字符串",
                code -> "[" + validRow(code) + ",{\"qualificationType\":\"RELATED_PERSON_KYC\",\"programCode\":5}]"),
            mapping("目标行多出 countryCode",
                code -> "[{\"qualificationType\":\"INDIVIDUAL_KYC\",\"programCode\":\"" + code
                    + "\",\"countryCode\":\"AE\"}]"),
            mapping("重复资格类型", code -> "[" + validRow(code) + "," + validRow(code) + "]"),
            mapping("其他行为未知资格类型",
                code -> "[" + validRow(code) + ",{\"qualificationType\":\"ENTITY_UNDERWRITING\","
                    + "\"programCode\":\"OTHER_PROGRAM\"}]"),
            mapping("其他行 programCode 小写",
                code -> "[" + validRow(code) + ",{\"qualificationType\":\"RELATED_PERSON_KYC\","
                    + "\"programCode\":\"other_program\"}]"),
            mapping("其他行 programCode 65 位",
                code -> "[" + validRow(code) + ",{\"qualificationType\":\"RELATED_PERSON_KYC\"," + "\"programCode\":\"P"
                    + "0".repeat(64) + "\"}]"),
            mapping("超过 65536 字符", code -> validArray(code) + " ".repeat(65537)),
            mapping("尾随内容", code -> validArray(code) + "{}"),
            mapping("重复键",
                code -> "[{\"qualificationType\":\"INDIVIDUAL_KYC\",\"qualificationType\":\"INDIVIDUAL_KYC\","
                    + "\"programCode\":\"" + code + "\"}]"));
    }

    /** 每例只改程序或修订的一个属性。 */
    static Stream<Arguments> invalidPrograms() {
        return Stream.of(program("程序为 DRAFT", builder -> builder.programStatus("DRAFT")),
            program("程序已 RETIRED", builder -> builder.programStatus("RETIRED")),
            program("修订为 DRAFT 且未占位", builder -> builder.versionStatus("DRAFT").activeSlot(null)),
            program("修订已 RETIRED 且未占位", builder -> builder.versionStatus("RETIRED").activeSlot(null)),
            program("ACTIVE 修订未占生效位", builder -> builder.activeSlot(null)),
            program("未记录批准人", builder -> builder.approvedBy(null)),
            program("批准人 ID 为 0", builder -> builder.approvedBy(0L)),
            program("未记录批准时间", builder -> builder.approvedTime(null)),
            program("批准时间在未来", builder -> builder.approvedTime(Instant.now().plusSeconds(3600))),
            program("内容摘要为大写十六进制",
                builder -> builder.contentHash(MaterialProgramBuilder.VALID_CONTENT_HASH.toUpperCase())),
            program("内容摘要不足 64 位", builder -> builder.contentHash("a".repeat(63))),
            program("内容摘要含非十六进制字符", builder -> builder.contentHash("g".repeat(64))),
            program("修订序号为 0", builder -> builder.versionNo(0)));
    }

    /** 每例只让材料闭包出现一个缺陷，其余要求合法。 */
    static Stream<Arguments> invalidRequirements() {
        Requirement valid = TEMPLATE_ITEMS.get(0);
        return Stream.of(Arguments.of("没有任何材料要求", List.of()), Arguments.of("超过 1024 条", requirements(1025)),
            Arguments.of("要求代码小写", List.of(valid, new Requirement("proof_of_address", "UTILITY_BILL", 0))),
            Arguments.of("要求代码以数字开头", List.of(valid, new Requirement("1_PROOF", "UTILITY_BILL", 0))),
            Arguments.of("材料类型为空", List.of(valid, new Requirement("PROOF_OF_ADDRESS", "", 0))),
            Arguments.of("材料类型小写", List.of(valid, new Requirement("PROOF_OF_ADDRESS", "utility_bill", 0))),
            Arguments.of("必需标记为 2", List.of(valid, new Requirement("PROOF_OF_ADDRESS", "UTILITY_BILL", 2))));
    }

    /** 查询串与可选 Body。 */
    static Stream<Arguments> invalidQueries() {
        return Stream.of(Arguments.of("缺少 qualificationType", "", null),
            Arguments.of("qualificationType 为空", "qualificationType=", null),
            Arguments.of("未知资格类型", "qualificationType=ENTITY_UNDERWRITING", null),
            Arguments.of("资格类型小写", "qualificationType=individual_kyc", null),
            Arguments.of("重复 qualificationType", "qualificationType=INDIVIDUAL_KYC&qualificationType=INDIVIDUAL_KYC",
                null),
            Arguments.of("多出 countryCode 参数", "qualificationType=INDIVIDUAL_KYC&countryCode=AE", null),
            Arguments.of("携带 Body", "qualificationType=INDIVIDUAL_KYC", "{}"));
    }

    /** 按 ai_ci 排序也稳定的定长要求代码 REQ_0000…，按期望输出顺序返回。 */
    private static List<Requirement> requirements(int count) {
        return IntStream.range(0, count).mapToObj(i -> new Requirement("REQ_%04d".formatted(i), "DOCUMENT", i % 2))
            .toList();
    }

    private static Arguments mapping(String scenario, Function<String, String> mapping) {
        return Arguments.of(scenario, mapping);
    }

    private static Arguments program(String scenario, Consumer<MaterialProgramBuilder> change) {
        return Arguments.of(scenario, change);
    }

    /** 目标资格 INDIVIDUAL_KYC 的合法映射行。 */
    private static String validRow(String programCode) {
        return "{\"qualificationType\":\"INDIVIDUAL_KYC\",\"programCode\":\"" + programCode + "\"}";
    }

    private static String validArray(String programCode) {
        return "[" + validRow(programCode) + "]";
    }

    private String json(List<Map<String, String>> rows) {
        return objectMapper.writeValueAsString(new ArrayList<>(rows));
    }

    /** 以合法调用方读取指定资格类型。 */
    private MaterialRequirementsResponseChecker read(String qualificationType) {
        return invoke("qualificationType=" + qualificationType, null);
    }

    /** 以私网调用方发送真实 GET，查询串原样拼接，每次使用独立 Request ID。 */
    private MaterialRequirementsResponseChecker invoke(String query, String body) {
        String requestId = "risk-material-requirements-" + UUID.randomUUID();
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Caller-Service", CALLER);
        String uri = query.isEmpty() ? PATH : PATH + "?" + query;
        return new MaterialRequirementsResponseChecker(http.exchange(HttpMethod.GET, uri, headers, body), objectMapper,
            requestId);
    }
}
