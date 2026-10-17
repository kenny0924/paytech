package com.paytech.platform.support.auto.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.paytech.platform.support.api.enums.ApiFieldDataType;
import com.paytech.platform.support.api.enums.ApiFieldLocation;
import com.paytech.platform.support.api.enums.ScriptExecutionStage;
import com.paytech.platform.support.integration.domain.ApiContract;
import com.paytech.platform.support.integration.domain.FieldDefinition;
import com.paytech.platform.support.integration.domain.ParameterDeclaration;
import com.paytech.platform.support.integration.domain.ScriptSignature;
import com.paytech.platform.support.integration.domain.ValidationIssue;
import com.paytech.platform.support.integration.domain.execution.CardValue;
import com.paytech.platform.support.integration.domain.execution.ExecutionErrorException;
import com.paytech.platform.support.integration.domain.execution.JsonValues;
import com.paytech.platform.support.integration.domain.execution.JsonValues.SecretRendering;
import com.paytech.platform.support.integration.domainservice.expression.ExpressionEngine;
import com.paytech.platform.support.integration.domainservice.validator.FlowDefinitionValidator;
import com.paytech.platform.support.integration.domainservice.validator.IntegrationApiDefinitionValidator;
import com.paytech.platform.support.integration.domainservice.validator.ValueBindingValidator;
import com.paytech.platform.support.test.builder.ExternalApiFixtures;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 卡字段的纯规则（PayerMax 卡直付对接技术方案第 3.2、3.3 节）：卡值只在发送字节中展开、轨迹写遮蔽文本、其他位置拒绝；
 * 原子 API cardFields 的保存校验；流程中卡值只能以 PATH 赋给卡字段。完整执行链路见 StandardApiCardPaymentTest。
 *
 * @see JsonValues#toJson(Object, boolean, SecretRendering)
 * @see IntegrationApiDefinitionValidator#validate(tools.jackson.databind.JsonNode, List, Set, Map)
 * @see FlowDefinitionValidator#validate(tools.jackson.databind.JsonNode, List, List, Map)
 */
class CardFieldRulesTest {

    private static final String PAN = "4111111111111111";
    private static final String CVV = "737";

    private final ValueBindingValidator bindings = new ValueBindingValidator(new ExpressionEngine());
    private final IntegrationApiDefinitionValidator definitions =
        new IntegrationApiDefinitionValidator(JsonMapper.builder().build(), bindings);
    private final FlowDefinitionValidator flows = new FlowDefinitionValidator(JsonMapper.builder().build(), bindings);

    /** 发送字节写原值，轨迹写遮蔽文本（卡号前 6 后 4），默认拒绝；toString 不含原值。 */
    @Test
    void test_cardRendering() {
        var body = new LinkedHashMap<String, Object>();
        body.put("number", CardValue.pan(PAN));
        body.put("cvv", CardValue.of(CVV));
        Map<String, Object> envelope = Map.of("data", body);

        assertAll(() -> assertEquals("{\"number\":\"" + PAN + "\",\"cvv\":\"" + CVV + "\"}",
                JsonValues.toJson(body, false, SecretRendering.WIRE).toString()),
            () -> assertEquals("{\"number\":\"411111******1111\",\"cvv\":\"[CARD]\"}",
                JsonValues.toJson(body, false, SecretRendering.MASKED).toString()),
            () -> assertThrows(ExecutionErrorException.class, () -> JsonValues.toJson(body, false)),
            () -> assertTrue(JsonValues.containsCard(envelope)),
            () -> assertFalse(JsonValues.containsCard(Map.of("data", Map.of("number", PAN)))),
            () -> assertEquals(CardValue.MASK, CardValue.pan(PAN).toString()));
    }

    /** 卡字段只能是 JSON 编码下无固定值的 BODY 字符串字段，且不能重复。 */
    @Test
    void test_definitionCardFields() {
        ObjectNode definition = ExternalApiFixtures.definition("card-create-order");
        assertEquals(List.of(), codes(definitions.validate(definition, apiFields(), Set.of("DEMO_GATEWAY"), scripts())
            .getIssues()));

        ArrayNode cardFields = definition.putArray("cardFields");
        cardFields.add("BODY:data.totalAmount").add("BODY:version").add("BODY:data.card.cvv").add("BODY:data.card.cvv")
            .add("QUERY:token");

        assertEquals(List.of("CARD_FIELD_DUPLICATED@definition.cardFields[3]", "CARD_FIELD_FIXED@definition.cardFields[1]",
            "CARD_FIELD_INVALID@definition.cardFields[0]", "CARD_FIELD_INVALID@definition.cardFields[4]"),
            codes(definitions.validate(definition, apiFields(), Set.of("DEMO_GATEWAY"), scripts()).getIssues()));
    }

    /** 合法流程通过；卡字段绑定输入、非卡字段或出口读取卡值都被拒绝。 */
    @Test
    void test_flowCardBindings() {
        var valid = flows.validate(ExternalApiFixtures.flow("call-card-create-order"), standardRequest(), standardResponse(),
            apis());
        var invalid = flows.validate(ExternalApiFixtures.flow("card-binding-invalid"), standardRequest(),
            standardResponse(), apis());

        assertAll(() -> assertEquals(List.of(), codes(valid.getIssues())),
            () -> assertEquals(List.of("CARD_BINDING_INVALID@flowDefinition.nodes[0].request.BODY:data.card.number",
                "REFERENCE_NOT_ALLOWED@flowDefinition.nodes[0].request.BODY:data.outTradeNo.path",
                "REFERENCE_NOT_ALLOWED@flowDefinition.returns[0].outputs.transactionRef.expression"),
                codes(invalid.getIssues())));
    }

    private static Map<String, ApiContract> apis() {
        return Map.of("CALL", new ApiContract("CALL", "卡下单", apiFields(), Set.of("BODY:version"),
            List.of(new ParameterDeclaration("privateKey", "PROTECTED_HANDLE", true)),
            Map.of("SUCCESS", List.of(new ParameterDeclaration("transactionRef", "STRING", true)), "FAIL", List.of()),
            Set.of("BODY:data.card.number", "BODY:data.card.expMonth", "BODY:data.card.expYear", "BODY:data.card.cvv",
                "BODY:data.card.holder"), Set.of()));
    }

    private static Map<String, ScriptSignature> scripts() {
        return Map.of("RSA_SIGN", new ScriptSignature("RSA_SIGN", ScriptExecutionStage.REQUEST,
            List.of(new ParameterDeclaration("privateKey", "PROTECTED_HANDLE", true))));
    }

    /** 卡下单原子 API 的请求字段。 */
    static List<FieldDefinition> apiFields() {
        return List.of(field("version", ApiFieldDataType.STRING, true), field("requestTime", ApiFieldDataType.STRING, false),
            field("data", ApiFieldDataType.OBJECT, false), field("data.outTradeNo", ApiFieldDataType.STRING, true),
            field("data.totalAmount", ApiFieldDataType.DECIMAL, true), field("data.card", ApiFieldDataType.OBJECT, false),
            field("data.card.number", ApiFieldDataType.STRING, true), field("data.card.expMonth", ApiFieldDataType.STRING, true),
            field("data.card.expYear", ApiFieldDataType.STRING, true), field("data.card.cvv", ApiFieldDataType.STRING, true),
            field("data.card.holder", ApiFieldDataType.STRING, true));
    }

    private static List<FieldDefinition> standardRequest() {
        return List.of(field("paymentContext", ApiFieldDataType.OBJECT, true),
            field("paymentContext.attemptId", ApiFieldDataType.STRING, true),
            field("paymentContext.amount", ApiFieldDataType.DECIMAL, true),
            field("paymentContext.vaultToken", ApiFieldDataType.STRING, true),
            field("paymentContext.instrumentToken", ApiFieldDataType.STRING, true));
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
