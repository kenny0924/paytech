package com.paytech.platform.support.auto.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.paytech.platform.support.api.enums.ApiFieldDataType;
import com.paytech.platform.support.api.enums.ApiFieldLocation;
import com.paytech.platform.support.integration.domain.ApiContract;
import com.paytech.platform.support.integration.domain.FieldDefinition;
import com.paytech.platform.support.integration.domain.ParameterDeclaration;
import com.paytech.platform.support.integration.domain.ValidationIssue;
import com.paytech.platform.support.integration.domainservice.expression.ExpressionEngine;
import com.paytech.platform.support.integration.domainservice.validator.FlowDefinitionValidator;
import com.paytech.platform.support.integration.domainservice.validator.ValueBindingValidator;
import com.paytech.platform.support.test.builder.ExternalApiFixtures;
import com.paytech.platform.support.test.builder.ExternalApiSamples;

import tools.jackson.databind.json.JsonMapper;

/**
 * 流程静态校验的图规则：支配节点决定 steps 引用是否必经，菱形汇合、条件读起点自身、节点读自身、
 * 初始化项依赖环与摘要。流程数据在 src/test/resources/externalapi/flow；HTTP 入口的错误映射由实现管理测试覆盖，
 * 本类只直接调用公开校验器。
 *
 * @see FlowDefinitionValidator#validate(tools.jackson.databind.JsonNode, List, List, Map)
 */
class FlowDefinitionValidatorTest {

    private static final List<FieldDefinition> REQUEST = List.of(field("orderId", true));
    private static final List<FieldDefinition> RESPONSE = List.of(field("status", true), field("ref", false));

    private final FlowDefinitionValidator validator =
        new FlowDefinitionValidator(JsonMapper.builder().build(), new ValueBindingValidator(new ExpressionEngine()));

    /** 菱形：a 分两路到 b、c 再汇合到 d；d 之后的出口可以读 a 与 d，不能读只在一条分支上的 b。 */
    @Test
    void test_diamondDominators() {
        var issues = validate(ExternalApiFixtures.flow("diamond"));

        assertEquals(List.of("STEP_NOT_ON_ALL_PATHS@flowDefinition.returns[0].outputs.ref"), issues);
    }

    /** 连线条件可以读起点自身结果；节点请求不能读自身结果。 */
    @Test
    void test_selfReference() {
        var issues = validate(ExternalApiFixtures.flow("self-reference"));

        assertEquals(List.of("STEP_NOT_ON_ALL_PATHS@flowDefinition.nodes[0].request.BODY:id"), issues);
    }

    /** 初始化项互相依赖形成环被拒绝；引用自身结果既是非法引用，也构成自环。 */
    @Test
    void test_initializerDependencies() {
        var cycle = validate(ExternalApiFixtures.flow("initializer-cycle"));
        var self = validate(ExternalApiFixtures.flow("initializer-self"));

        assertEquals(List.of("INITIALIZER_CYCLE@flowDefinition.initializers"), cycle);
        assertEquals(List.of("INITIALIZER_CYCLE@flowDefinition.initializers",
            "REFERENCE_NOT_ALLOWED@flowDefinition.initializers[0].args.vaultToken.path"), self);
    }

    /** 合法的样例支付流程通过，摘要给出节点数、出口数、初始化项、预算与使用的原子 API。 */
    @Test
    void test_validFlowSummary() {
        var createOrder = new ApiContract(ExternalApiSamples.CREATE_ORDER, "下单",
            List.of(field(ApiFieldLocation.PATH, "outletRef"), field(ApiFieldLocation.HEADER, "X-Api-Version"),
                field(ApiFieldLocation.BODY, "orderRef"), field(ApiFieldLocation.BODY, "amount")),
            Set.of("HEADER:x-api-version"), List.of(new ParameterDeclaration("signingKey", "PROTECTED_HANDLE", true)),
            Map.of("SUCCESS", List.of(new ParameterDeclaration("transactionRef", "STRING", true))), Set.of());
        var request = List.of(new FieldDefinition(ApiFieldLocation.BODY, "paymentContext", "上下文",
            ApiFieldDataType.OBJECT, true, null), field("paymentContext.attemptId", true),
            field("paymentContext.amount", true), field("paymentContext.vaultToken", true));
        var response = List.of(field("paymentStatus", true), field("transactionRef", false));

        var result = validator.validate(ExternalApiSamples.payFlow(), request, response,
            Map.of(ExternalApiSamples.CREATE_ORDER, createOrder));

        assertTrue(result.getIssues().isEmpty(), () -> codes(result.getIssues()).toString());
        var summary = result.getSummary();
        assertAll(() -> assertEquals(1, summary.getNodeCount()), () -> assertEquals(3, summary.getReturnCount()),
            () -> assertEquals(List.of("channelAccount"), summary.getInitializerIds()),
            () -> assertEquals(20000, summary.getTimeoutMs()),
            () -> assertEquals(Set.of(ExternalApiSamples.CREATE_ORDER), summary.getIntegrationApis()));
    }

    /** 有阻断问题时只返回问题，不返回摘要。 */
    @Test
    void test_invalidHasNoSummary() {
        var result = validator.validate(ExternalApiSamples.object("{\"timeoutMs\":0}"), REQUEST, RESPONSE, apis());

        assertNull(result.getSummary());
        assertTrue(codes(result.getIssues()).contains("TIMEOUT_INVALID@flowDefinition.timeoutMs"));
    }

    private List<String> validate(tools.jackson.databind.JsonNode flow) {
        return codes(validator.validate(flow, REQUEST, RESPONSE, apis()).getIssues());
    }

    /** 单个原子 API：请求只有 BODY id，SUCCESS 输出 value。 */
    private static Map<String, ApiContract> apis() {
        return Map.of("API", new ApiContract("API", "示例", List.of(field("id", true)), Set.of(), List.of(),
            Map.of("SUCCESS", List.of(new ParameterDeclaration("value", "STRING", false))), Set.of()));
    }


    private static List<String> codes(List<ValidationIssue> issues) {
        return issues.stream().map(issue -> issue.getCode() + "@" + issue.getLocation()).sorted().toList();
    }

    private static FieldDefinition field(String path, boolean required) {
        return new FieldDefinition(ApiFieldLocation.BODY, path, path, ApiFieldDataType.STRING, required, null);
    }

    private static FieldDefinition field(ApiFieldLocation location, String path) {
        return new FieldDefinition(location, path, path, ApiFieldDataType.STRING, true, null);
    }
}
