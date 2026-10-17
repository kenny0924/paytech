package com.paytech.platform.support.auto.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.paytech.platform.support.api.enums.ApiFieldDataType;
import com.paytech.platform.support.api.enums.ApiFieldLocation;
import com.paytech.platform.support.integration.domain.ApiContract;
import com.paytech.platform.support.integration.domain.FieldDefinition;
import com.paytech.platform.support.integration.domain.ParameterDeclaration;
import com.paytech.platform.support.integration.domain.ValidationIssue;
import com.paytech.platform.support.integration.domain.execution.ExecutionErrorException;
import com.paytech.platform.support.integration.domain.execution.JsonValues;
import com.paytech.platform.support.integration.domain.execution.JsonValues.SecretRendering;
import com.paytech.platform.support.integration.domain.execution.ProtectedHandle;
import com.paytech.platform.support.integration.domainservice.execution.HttpWork;
import com.paytech.platform.support.integration.domainservice.expression.ExpressionEngine;
import com.paytech.platform.support.integration.domainservice.validator.FlowDefinitionValidator;
import com.paytech.platform.support.integration.domainservice.validator.IntegrationApiDefinitionValidator;
import com.paytech.platform.support.integration.domainservice.validator.ValueBindingValidator;
import com.paytech.platform.support.test.builder.ExternalApiFixtures;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 凭据字段的纯规则（Toss Pay 接入技术方案 T1）：受控句柄在发送字节中展开、轨迹写遮蔽文本、默认拒绝；含凭据的请求体不能 sealJsonBody；
 * 原子 API credentialFields 的保存校验；
 * 流程中凭据字段只能以 PATH 绑定初始化项的 credentials。完整执行链路见 StandardApiBodyCredentialTest。
 *
 * @see JsonValues#toJson(Object, boolean, SecretRendering)
 * @see HttpWork#sealJsonBody()
 * @see IntegrationApiDefinitionValidator#validate(tools.jackson.databind.JsonNode, List, Set, Map)
 * @see FlowDefinitionValidator#validate(tools.jackson.databind.JsonNode, List, List, Map)
 */
class CredentialFieldRulesTest {

    private static final String API_KEY = "synthetic-body-key";

    private final ValueBindingValidator bindings = new ValueBindingValidator(new ExpressionEngine());
    private final IntegrationApiDefinitionValidator definitions =
        new IntegrationApiDefinitionValidator(JsonMapper.builder().build(), bindings);
    private final FlowDefinitionValidator flows = new FlowDefinitionValidator(JsonMapper.builder().build(), bindings);

    /** 发送字节写明文，轨迹写 [REDACTED]，默认拒绝；toString 不含明文。 */
    @Test
    void test_credentialRendering() {
        var body = new LinkedHashMap<String, Object>();
        body.put("apiKey", new ProtectedHandle(API_KEY));
        body.put("orderNo", "ORDER-1");

        assertAll(() -> assertEquals("{\"apiKey\":\"" + API_KEY + "\",\"orderNo\":\"ORDER-1\"}",
                JsonValues.toJson(body, false, SecretRendering.WIRE).toString()),
            () -> assertEquals("{\"apiKey\":\"" + ProtectedHandle.MASK + "\",\"orderNo\":\"ORDER-1\"}",
                JsonValues.toJson(body, false, SecretRendering.MASKED).toString()),
            () -> assertThrows(ExecutionErrorException.class, () -> JsonValues.toJson(body, false)),
            () -> assertTrue(JsonValues.containsHandle(Map.of("data", List.of(body)))),
            () -> assertFalse(JsonValues.containsHandle(Map.of("apiKey", API_KEY))),
            () -> assertFalse(new ProtectedHandle(API_KEY).toString().contains(API_KEY)));
    }

    /** 请求体含受控句柄时不能锁定为脚本可读的文本；不含时正常锁定。 */
    @Test
    void test_sealRejectsCredentialBody() {
        var withKey = new LinkedHashMap<String, Object>(Map.of("apiKey", new ProtectedHandle(API_KEY)));
        var plain = new LinkedHashMap<String, Object>(Map.of("orderNo", "ORDER-1"));
        Function<Object, byte[]> encoder =
            body -> JsonValues.toJson(body, false, SecretRendering.WIRE).toString().getBytes(StandardCharsets.UTF_8);

        var failure = assertThrows(ExecutionErrorException.class,
            () -> HttpWork.request(Map.of(), withKey, encoder).sealJsonBody());
        assertAll(() -> assertEquals("请求体含凭据字段，不能锁定为脚本可读的文本", failure.getMessage()),
            () -> assertEquals("{\"orderNo\":\"ORDER-1\"}", HttpWork.request(Map.of(), plain, encoder).sealJsonBody()));
    }

    /** 凭据字段只能是 JSON 编码下无固定值的 BODY 字符串字段，不能重复，也不能同时是卡字段。 */
    @Test
    void test_definitionCredentialFields() {
        ObjectNode definition = ExternalApiFixtures.definition("body-credential-order");
        assertEquals(List.of(), codes(definitions.validate(definition, apiFields(), Set.of("DEMO_GATEWAY"), Map.of())
            .getIssues()));

        definition.putObject("fixedValues").putObject("BODY:currency").put("kind", "EXPRESSION").put("expression", "'KRW'");
        definition.putArray("cardFields").add("BODY:secret");
        definition.putArray("credentialFields").add("BODY:amount").add("BODY:currency").add("BODY:secret").add("BODY:apiKey");
        var invalid = codes(definitions.validate(definition, apiFields(), Set.of("DEMO_GATEWAY"), Map.of()).getIssues());
        definition.putArray("credentialFields").add("BODY:apiKey").add("BODY:apiKey");
        var duplicated = codes(definitions.validate(definition, apiFields(), Set.of("DEMO_GATEWAY"), Map.of()).getIssues());
        definition.put("requestEncoding", "FORM_URLENCODED");
        var form = codes(definitions.validate(definition, apiFields(), Set.of("DEMO_GATEWAY"), Map.of()).getIssues());

        assertAll(() -> assertEquals(List.of("CREDENTIAL_FIELD_CONFLICT@definition.credentialFields[2]",
                "CREDENTIAL_FIELD_FIXED@definition.credentialFields[1]",
                "CREDENTIAL_FIELD_INVALID@definition.credentialFields[0]"), invalid),
            () -> assertEquals(List.of("CREDENTIAL_FIELD_DUPLICATED@definition.credentialFields[1]"), duplicated),
            () -> assertTrue(form.contains("CREDENTIAL_FIELD_ENCODING@definition.credentialFields"), form.toString()));
    }

    /** 合法流程通过；凭据字段绑定输入、非凭据字段读取凭据都被拒绝。 */
    @Test
    void test_flowCredentialBindings() {
        var valid = flows.validate(ExternalApiFixtures.flow("call-body-credential-order"), standardRequest(),
            standardResponse(), apis());
        var invalid = flows.validate(ExternalApiFixtures.flow("credential-binding-invalid"), standardRequest(),
            standardResponse(), apis());

        assertAll(() -> assertEquals(List.of(), codes(valid.getIssues())),
            () -> assertEquals(List.of("CREDENTIAL_BINDING_INVALID@flowDefinition.nodes[0].request.BODY:apiKey",
                "REFERENCE_NOT_ALLOWED@flowDefinition.nodes[0].request.BODY:orderNo.path"), codes(invalid.getIssues())));
    }

    private static Map<String, ApiContract> apis() {
        return Map.of("CALL", new ApiContract("CALL", "下单", apiFields(), Set.of(),
            List.of(new ParameterDeclaration("apiKey", "PROTECTED_HANDLE", false)),
            Map.of("SUCCESS", List.of(new ParameterDeclaration("transactionRef", "STRING", true)), "FAIL", List.of()),
            Set.of(), Set.of("BODY:apiKey")));
    }

    /** 带 body apiKey 的下单原子 API 请求字段。 */
    static List<FieldDefinition> apiFields() {
        return List.of(field("apiKey", ApiFieldDataType.STRING, true), field("orderNo", ApiFieldDataType.STRING, true),
            field("amount", ApiFieldDataType.DECIMAL, true), field("currency", ApiFieldDataType.STRING, false),
            field("secret", ApiFieldDataType.STRING, false));
    }

    private static List<FieldDefinition> standardRequest() {
        return List.of(field("paymentContext", ApiFieldDataType.OBJECT, true),
            field("paymentContext.attemptId", ApiFieldDataType.STRING, true),
            field("paymentContext.amount", ApiFieldDataType.DECIMAL, true),
            field("paymentContext.vaultToken", ApiFieldDataType.STRING, true));
    }

    private static List<FieldDefinition> standardResponse() {
        return List.of(field("paymentStatus", ApiFieldDataType.STRING, true),
            field("transactionRef", ApiFieldDataType.STRING, false));
    }

    private static List<String> codes(List<ValidationIssue> issues) {
        return issues.stream().map(issue -> issue.getCode() + "@" + issue.getLocation()).sorted().toList();
    }

    private static FieldDefinition field(String path, ApiFieldDataType type, boolean required) {
        return new FieldDefinition(ApiFieldLocation.BODY, path, path, type, required, null);
    }
}
