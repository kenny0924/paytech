package com.paytech.operations.console.bff.auto;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.paytech.foundation.web.error.MvcProblemExceptionMapper;
import com.paytech.foundation.web.error.RequestBodyFieldErrorProjector;
import com.paytech.operations.console.bff.configuration.RiskRequestJsonConfiguration;
import com.paytech.operations.console.bff.service.model.ManagementModels.DisableAccountRequestBody;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.*;
import com.paytech.operations.console.bff.service.model.RiskTestCandidateModels;
import com.paytech.operations.console.bff.service.model.RiskTestCandidateModels.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RequestBody;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 通过真实转换器、Jakarta 和 Foundation 分类器冻结请求协议；HTTP 授权链由后续路由测试验证。 */
class RiskDecisionConfigurationModelAutoTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ValidatorFactory VALIDATION = Validation.buildDefaultValidatorFactory();
    private static final String HASH = "a".repeat(64);
    private static final String CREATE = """
        {"pointCode":"CHECKOUT_PREPARE","ruleSetCode":"BASELINE","ruleSetName":"Baseline",
         "baseRuleSetVersionId":null,"baseContentHash":null,"changeReason":"Policy review",
         "rules":[{"ruleCode":"COUNTRY_CHECK","ruleName":"Country check","priorityNo":10,
          "enabledFlag":true,"reasonCode":null,"conditionNodes":[
           {"nodeKey":"root","parentNodeKey":null,"sequenceNo":1,"nodeType":"PREDICATE",
            "groupOperator":null,"comparisonOperator":"EQ","operands":[
             {"operandRole":"LEFT","valueSequence":1,"sourceType":"CONTEXT","fieldCode":"country_code",
              "valueType":"STRING","literalValue":null},
             {"operandRole":"RIGHT","valueSequence":1,"sourceType":"LITERAL","fieldCode":null,
              "valueType":"STRING","literalValue":"US"}]}],
          "action":{"actionCode":"ALLOW","actionVersionNo":1}}]}
        """;
    private static final String RULE = "/rules/0";
    private static final String NODE = RULE + "/conditionNodes/0";
    private static final String OPERAND = NODE + "/operands/0";
    private static final List<String> TEXT_PATHS = List.of("/pointCode", "/ruleSetCode", "/ruleSetName",
        "/baseRuleSetVersionId", "/baseContentHash", "/changeReason", RULE + "/ruleCode", RULE + "/ruleName",
        RULE + "/reasonCode", NODE + "/nodeKey", NODE + "/parentNodeKey", NODE + "/nodeType",
        NODE + "/groupOperator", NODE + "/comparisonOperator", OPERAND + "/operandRole", OPERAND + "/sourceType",
        OPERAND + "/fieldCode", OPERAND + "/valueType", RULE + "/action/actionCode");
    private static final List<String> INTEGER_PATHS = List.of(RULE + "/priorityNo", NODE + "/sequenceNo",
        OPERAND + "/valueSequence", RULE + "/action/actionVersionNo");
    private static final List<String> LIST_PATHS = List.of("/rules", RULE + "/conditionNodes", NODE + "/operands");
    private static final Set<String> NULLABLE_PATHS = Set.of("/baseRuleSetVersionId", "/baseContentHash",
        RULE + "/reasonCode", RULE + "/action", NODE + "/parentNodeKey", NODE + "/groupOperator", NODE + "/comparisonOperator",
        OPERAND + "/fieldCode", OPERAND + "/literalValue");

    @AfterAll
    static void closeValidation() {
        VALIDATION.close();
    }

    @Test
    void test_requestListsSnapshotWithoutErasingNullValidationEvidence() throws Exception {
        var valid = decode(CREATE, CreateRuleSetVersionRequestBody.class);
        var rules = new ArrayList<>(valid.rules());
        var nodes = new ArrayList<>(rules.getFirst().conditionNodes());
        var operands = new ArrayList<>(nodes.getFirst().operands());
        var request = new CreateRuleSetVersionRequestBody("CHECKOUT_PREPARE", "BASELINE", "Baseline", null, null,
            "Policy review", rules);
        var rule = new RuleRequestBody("COUNTRY_CHECK", "Country check", 10, true, null, nodes,
            valid.rules().getFirst().action());
        var node = new ConditionNodeRequestBody("root", null, 1, "PREDICATE", null, "EQ", operands);
        rules.clear();
        nodes.clear();
        operands.clear();
        assertEquals(1, request.rules().size());
        assertEquals(1, rule.conditionNodes().size());
        assertEquals(2, node.operands().size());
        assertThrows(UnsupportedOperationException.class, () -> request.rules().clear());
        assertThrows(UnsupportedOperationException.class, () -> rule.conditionNodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> node.operands().clear());
        assertNull(new CreateRuleSetVersionRequestBody("A", "A", "A", null, null, "A", null).rules());
        assertNull(new RuleRequestBody("A", "A", 1, true, null, null, null).conditionNodes());
        assertNull(new ConditionNodeRequestBody("a", null, 1, "GROUP", "AND", null, null).operands());
        var nullElement = new ArrayList<RuleRequestBody>();
        nullElement.add(null);
        var preserved = new CreateRuleSetVersionRequestBody("A", "A", "A", null, null, "A", nullElement);
        nullElement.clear();
        assertEquals(1, preserved.rules().size());
        assertNull(preserved.rules().getFirst());
        assertThrows(UnsupportedOperationException.class, () -> preserved.rules().clear());
    }

    @ParameterizedTest
    @MethodSource("commands")
    void test_fourCompleteCommandsDecodeAndValidate(Class<?> type, String payload) throws Exception {
        assertTrue(VALIDATION.getValidator().validate(decode(payload, type)).isEmpty());
    }

    static Stream<Arguments> commands() {
        return Stream.of(Arguments.of(CreateRuleSetVersionRequestBody.class, CREATE),
            Arguments.of(FreezeRuleSetVersionRequestBody.class, "{\"expectedContentHash\":\"" + HASH + "\"}"),
            Arguments.of(CreateTestCandidateRequestBody.class,
                "{\"ruleSetVersionId\":\"1\",\"expectedRuleSetContentHash\":\"" + HASH + "\"}"),
            Arguments.of(ValidateTestCandidateRequestBody.class, "{\"expectedVersion\":1}"));
    }

    @ParameterizedTest
    @MethodSource("commandProperties")
    void test_everyCommandPropertyIsRequiredEvenWhenNullable(Class<?> type, String payload, String path) {
        ObjectNode root = (ObjectNode) JSON.readTree(payload);
        parent(root, path).remove(leaf(path));
        assertShapeError(root.toString(), type);
    }

    static Stream<Arguments> commandProperties() {
        return commands().flatMap(command -> {
            Class<?> type = (Class<?>) command.get()[0];
            String payload = (String) command.get()[1];
            if (type == CreateRuleSetVersionRequestBody.class) {
                return createPaths().stream().map(path -> Arguments.of(type, payload, path));
            }
            return JSON.readTree(payload).propertyNames().stream()
                .map(name -> Arguments.of(type, payload, "/" + name));
        });
    }

    @ParameterizedTest
    @MethodSource("unknownProperties")
    void test_unknownPropertiesAreShapeErrorsAtEveryObject(Class<?> type, String payload, String path) {
        ObjectNode root = (ObjectNode) JSON.readTree(payload);
        ((ObjectNode) root.at(path)).put("privateUnknown", "must-not-leak");
        assertShapeError(root.toString(), type);
    }

    static Stream<Arguments> unknownProperties() {
        return Stream.concat(commands().map(command -> Arguments.of(command.get()[0], command.get()[1], "")),
            Stream.of(RULE, NODE, OPERAND, RULE + "/action")
                .map(path -> Arguments.of(CreateRuleSetVersionRequestBody.class, CREATE, path)));
    }

    @ParameterizedTest
    @MethodSource("wrongTokens")
    void test_wrongNonNullTokensCannotBeCoerced(Class<?> type, String payload) {
        assertShapeError(payload, type);
    }

    static Stream<Arguments> wrongTokens() {
        List<Arguments> cases = new ArrayList<>();
        for (String path : TEXT_PATHS) {
            for (String value : List.of("1", "true", "[]", "{}")) {
                cases.add(Arguments.of(CreateRuleSetVersionRequestBody.class, replace(CREATE, path, value)));
            }
        }
        for (String path : INTEGER_PATHS) {
            for (String value : List.of("\"1\"", "1.0", "1e0", "2147483648", "-2147483649", "true", "[]", "{}")) {
                cases.add(Arguments.of(CreateRuleSetVersionRequestBody.class, replace(CREATE, path, value)));
            }
        }
        for (String path : LIST_PATHS) {
            for (String value : List.of("{}", "1", "\"list\"", "true", "[1]", "[\"item\"]", "[[]]")) {
                cases.add(Arguments.of(CreateRuleSetVersionRequestBody.class, replace(CREATE, path, value)));
            }
        }
        for (String value : List.of("\"true\"", "1", "{}", "[]")) {
            cases.add(Arguments.of(CreateRuleSetVersionRequestBody.class, replace(CREATE, RULE + "/enabledFlag", value)));
        }
        for (String value : List.of("1", "1.0", "[]", "{}")) {
            cases.add(Arguments.of(CreateRuleSetVersionRequestBody.class, replace(CREATE, OPERAND + "/literalValue", value)));
        }
        for (String value : List.of("[]", "1", "true", "\"action\"")) {
            cases.add(Arguments.of(CreateRuleSetVersionRequestBody.class, replace(CREATE, RULE + "/action", value)));
        }
        for (String value : List.of("\"1\"", "1.0", "1e0", "2147483648", "-2147483649", "true", "[]", "{}")) {
            cases.add(Arguments.of(ValidateTestCandidateRequestBody.class, "{\"expectedVersion\":" + value + "}"));
        }
        cases.add(Arguments.of(FreezeRuleSetVersionRequestBody.class, "{\"expectedContentHash\":1}"));
        cases.add(Arguments.of(CreateTestCandidateRequestBody.class,
            "{\"ruleSetVersionId\":1,\"expectedRuleSetContentHash\":\"" + HASH + "\"}"));
        cases.add(Arguments.of(CreateTestCandidateRequestBody.class,
            "{\"ruleSetVersionId\":\"1\",\"expectedRuleSetContentHash\":false}"));
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("malformedRoots")
    void test_requiresOneObjectRootAndRejectsDuplicateRawKeys(Class<?> type, String payload) {
        assertShapeError(payload, type);
    }

    static Stream<Arguments> malformedRoots() {
        return commands().flatMap(command -> {
            Class<?> type = (Class<?>) command.get()[0];
            String payload = (String) command.get()[1];
            String firstProperty = JSON.readTree(payload).propertyNames().iterator().next();
            return Stream.of("", "{", "[]", "null", "1", "true", "\"body\"", payload + " {}",
                payload + " null", payload + " trailing", payload.replaceFirst("\\{", "{\"" + firstProperty + "\":null,"))
                .map(value -> Arguments.of(type, value));
        });
    }

    @Test
    void test_duplicateNestedRawKeysAreRejectedWithoutSensitiveDiagnostics() {
        assertShapeError(CREATE.replace("\"literalValue\":\"US\"",
            "\"literalValue\":\"must-not-leak\",\"literalValue\":\"US\""), CreateRuleSetVersionRequestBody.class);
        assertShapeError(CREATE.replace("\"nodeKey\":\"root\"",
            "\"nodeKey\":\"must-not-leak\",\"nodeKey\":\"root\""), CreateRuleSetVersionRequestBody.class);
    }

    @ParameterizedTest
    @MethodSource("nonNullProperties")
    void test_presentNullSurvivesDecodingFor422(Class<?> type, String payload, String path) throws Exception {
        assertValidationError(replace(payload, path, "null"), type, leaf(path));
    }

    static Stream<Arguments> nonNullProperties() {
        return commandProperties().filter(entry -> entry.get()[0] != CreateRuleSetVersionRequestBody.class
            || !NULLABLE_PATHS.contains(entry.get()[2]));
    }

    @ParameterizedTest
    @MethodSource("nullElements")
    void test_nullListElementsRemain422(String path) throws Exception {
        assertValidationError(replace(CREATE, path, "[null]"), CreateRuleSetVersionRequestBody.class, leaf(path));
    }

    static Stream<String> nullElements() {
        return LIST_PATHS.stream();
    }

    @ParameterizedTest
    @MethodSource("invalidValues")
    void test_blankPatternSizeAndPositiveViolationsAre422(Class<?> type, String payload, String field) throws Exception {
        assertValidationError(payload, type, field);
    }

    static Stream<Arguments> invalidValues() {
        List<Arguments> cases = new ArrayList<>();
        Map<String, List<String>> values = Map.ofEntries(
            Map.entry("/pointCode", List.of("\"\"", "\"lower\"", quote("A".repeat(65)))),
            Map.entry("/ruleSetCode", List.of("\" \"", "\"1BAD\"", quote("A".repeat(65)))),
            Map.entry("/ruleSetName", List.of("\" \"", quote("x".repeat(256)))),
            Map.entry("/changeReason", List.of("\" \"", quote("x".repeat(501)))),
            Map.entry("/baseRuleSetVersionId", List.of("\"0\"", "\"01\"", "\"10000000000000000000\"")),
            Map.entry("/baseContentHash", List.of("\"\"", quote("A".repeat(64)), quote("a".repeat(63)))),
            Map.entry(RULE + "/ruleCode", List.of("\" \"", "\"lower\"", quote("A".repeat(65)))),
            Map.entry(RULE + "/ruleName", List.of("\" \"", quote("x".repeat(256)))),
            Map.entry(RULE + "/reasonCode", List.of("\"\"", "\"lower\"", quote("A".repeat(65)))),
            Map.entry(NODE + "/nodeKey", List.of("\"\"", "\"with space\"", quote("a".repeat(65)), "\"中文\"")),
            Map.entry(NODE + "/parentNodeKey", List.of("\"\"", "\"with space\"", quote("a".repeat(65)))),
            Map.entry(NODE + "/nodeType", List.of("\"\"", "\"OTHER\"")),
            Map.entry(NODE + "/groupOperator", List.of("\"\"", "\"XOR\"")),
            Map.entry(NODE + "/comparisonOperator", List.of("\"\"", "\"LIKE\"")),
            Map.entry(OPERAND + "/operandRole", List.of("\"\"", "\"MIDDLE\"")),
            Map.entry(OPERAND + "/sourceType", List.of("\"\"", "\"FEATURE\"")),
            Map.entry(OPERAND + "/fieldCode", List.of("\"\"", "\"Country\"", quote("a".repeat(65)))),
            Map.entry(OPERAND + "/valueType", List.of("\"\"", "\"DECIMAL\"")),
            Map.entry(RULE + "/action/actionCode", List.of("\"\"", "\"lower\"", quote("A".repeat(65)))));
        values.forEach((path, invalid) -> invalid.forEach(value ->
            cases.add(Arguments.of(CreateRuleSetVersionRequestBody.class, replace(CREATE, path, value), leaf(path)))));
        for (String path : INTEGER_PATHS) {
            for (String value : List.of("0", "-1", "-2147483648")) {
                cases.add(Arguments.of(CreateRuleSetVersionRequestBody.class, replace(CREATE, path, value), leaf(path)));
            }
        }
        for (String path : List.of("/rules", RULE + "/conditionNodes")) {
            cases.add(Arguments.of(CreateRuleSetVersionRequestBody.class, replace(CREATE, path, "[]"), leaf(path)));
        }
        for (String value : List.of("0", "-1", "-2147483648")) {
            cases.add(Arguments.of(ValidateTestCandidateRequestBody.class, "{\"expectedVersion\":" + value + "}", "expectedVersion"));
        }
        cases.add(Arguments.of(FreezeRuleSetVersionRequestBody.class, "{\"expectedContentHash\":\"\"}", "expectedContentHash"));
        cases.add(Arguments.of(CreateTestCandidateRequestBody.class,
            "{\"ruleSetVersionId\":\"01\",\"expectedRuleSetContentHash\":\"" + HASH + "\"}", "ruleSetVersionId"));
        cases.add(Arguments.of(CreateTestCandidateRequestBody.class,
            "{\"ruleSetVersionId\":\"1\",\"expectedRuleSetContentHash\":\"invalid\"}", "expectedRuleSetContentHash"));
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("collectionLimits")
    void test_exactCollectionMaximumAcceptedAndOneMoreIs422(String path, int maximum) throws Exception {
        JsonNode item = JSON.readTree(CREATE).at(path).get(0);
        var atMaximum = JSON.createArrayNode();
        for (int index = 0; index < maximum; index++) atMaximum.add(item);
        Object valid = decode(replace(CREATE, path, atMaximum.toString()), CreateRuleSetVersionRequestBody.class);
        assertTrue(VALIDATION.getValidator().validate(valid).isEmpty());
        atMaximum.add(item);
        assertValidationError(replace(CREATE, path, atMaximum.toString()), CreateRuleSetVersionRequestBody.class, leaf(path));
    }

    static Stream<Arguments> collectionLimits() {
        return Stream.of(Arguments.of("/rules", 200), Arguments.of(RULE + "/conditionNodes", 2000),
            Arguments.of(NODE + "/operands", 101));
    }

    @Test
    void test_astIdentityConnectivityAndBasePairingRemainRiskOwned() throws Exception {
        ObjectNode root = (ObjectNode) JSON.readTree(CREATE);
        root.put("baseRuleSetVersionId", "1");
        var nodes = JSON.createArrayNode();
        ObjectNode first = ((ObjectNode) root.at(NODE)).deepCopy();
        first.put("parentNodeKey", "missing-parent");
        first.put("nodeType", "GROUP");
        first.put("groupOperator", "AND");
        first.put("comparisonOperator", "EQ");
        nodes.add(first);
        ObjectNode second = first.deepCopy();
        second.put("sequenceNo", 2);
        second.put("nodeType", "PREDICATE");
        second.set("operands", JSON.createArrayNode());
        nodes.add(second);
        ((ObjectNode) root.at(RULE)).set("conditionNodes", nodes);
        var decoded = decode(root.toString(), CreateRuleSetVersionRequestBody.class);
        assertTrue(VALIDATION.getValidator().validate(decoded).isEmpty());
        assertEquals("1", decoded.baseRuleSetVersionId());
        assertNull(decoded.baseContentHash());
        assertEquals(List.of("root", "root"), decoded.rules().getFirst().conditionNodes().stream()
            .map(ConditionNodeRequestBody::nodeKey).toList());
        assertEquals(List.of(1, 2), decoded.rules().getFirst().conditionNodes().stream()
            .map(ConditionNodeRequestBody::sequenceNo).toList());
        ((ObjectNode) root.at(RULE)).putNull("reasonMessage");
        // 旧请求省略的新空槽位会由当前 DTO 显式输出，业务内容保持不变。
        root.putNull("operandFormat");
        for (var node : root.at(RULE + "/conditionNodes")) for (var operand : node.path("operands")) {
            for (String name : List.of("variableCode", "numericUnit", "currencyCode", "currencyVariableCode")) ((ObjectNode)operand).putNull(name);
        }
        assertEquals(root, JSON.valueToTree(decoded));
        for (String source : List.of("CONTEXT", "LITERAL")) {
            String changed = replace(CREATE, OPERAND + "/sourceType", quote(source));
            changed = replace(changed, OPERAND + "/literalValue", "true");
            assertTrue(VALIDATION.getValidator().validate(decode(changed, CreateRuleSetVersionRequestBody.class)).isEmpty());
        }
        assertTrue(VALIDATION.getValidator().validate(decode(replace(CREATE, NODE + "/operands", "[]"),
            CreateRuleSetVersionRequestBody.class)).isEmpty());
    }

    @Test
    void test_literalConstructorAcceptsOnlyStringBooleanOrNull() {
        for (Object value : Arrays.asList("1.25", true, false, null)) {
            assertEquals(value, new OperandRequestBody("RIGHT", 1, "LITERAL", null, "NUMBER", value).literalValue());
        }
        for (Object value : List.of(1, 1L, 1.0, new BigDecimal("1"), List.of(), Map.of())) {
            assertThrows(IllegalArgumentException.class,
                () -> new OperandRequestBody("RIGHT", 1, "LITERAL", null, "NUMBER", value));
        }
    }

    @Test
    void test_intMaximumStaysExactAndWhitespaceAfterRootIsAllowed() throws Exception {
        assertEquals(Integer.MAX_VALUE, decode("{\"expectedVersion\":2147483647} \n\t",
            ValidateTestCandidateRequestBody.class).expectedVersion());
    }

    @Test
    void test_converterSelectionIsLimitedToExactFourRootsAndNeverWrites() throws Exception {
        var mapper = JsonMapper.builder().build();
        var defaults = new JacksonJsonHttpMessageConverter(mapper);
        var builder = HttpMessageConverters.forServer().registerDefaults().withJsonConverter(defaults);
        new RiskRequestJsonConfiguration(mapper).configureMessageConverters(builder);
        var converters = new ArrayList<HttpMessageConverter<?>>();
        builder.build().forEach(converters::add);
        var strict = (JacksonJsonHttpMessageConverter) converters.getFirst();
        assertNotSame(mapper, strict.getMapper());
        for (var command : commands().toList()) {
            Class<?> type = (Class<?>) command.get()[0];
            assertTrue(strict.canRead(type, MediaType.APPLICATION_JSON));
            assertTrue(strict.canRead(ResolvableType.forClass(type), MediaType.APPLICATION_JSON));
            assertFalse(strict.canRead(type, MediaType.TEXT_PLAIN));
            assertFalse(strict.canWrite(type, MediaType.APPLICATION_JSON));
            assertFalse(strict.canWrite(ResolvableType.forClass(type), type, MediaType.APPLICATION_JSON));
        }
        for (Class<?> type : List.of(DisableAccountRequestBody.class, RuleRequestBody.class,
            RuleSetQueryRequestBody.class, RuleSetDetailResponseBody.class, Map.class, List.class, Object.class)) {
            assertFalse(strict.canRead(type, MediaType.APPLICATION_JSON));
            assertFalse(strict.canRead(ResolvableType.forClass(type), MediaType.APPLICATION_JSON));
        }
        assertFalse(strict.canRead(ResolvableType.forClassWithGenerics(List.class,
            CreateRuleSetVersionRequestBody.class), MediaType.APPLICATION_JSON));
        assertSame(defaults, converters.stream().filter(value -> value.canRead(DisableAccountRequestBody.class,
            MediaType.APPLICATION_JSON)).findFirst().orElseThrow());
        var legacy = "{\"expectedVersion\":\"1\",\"reason\":\"existing\"}";
        assertEquals(new DisableAccountRequestBody(1, "existing"), defaults.read(DisableAccountRequestBody.class, input(legacy)));
        assertEquals(2, mapper.readTree("{\"value\":1,\"value\":2}").get("value").intValue());
    }

    @ParameterizedTest
    @ValueSource(classes = {RiskRuleSetModels.class, RiskTestCandidateModels.class})
    void test_allRecordsPreserveNullsAndEmptyArraysUnderNonEmptyGlobalMapper(Class<?> container) throws Exception {
        JsonMapper nonEmpty = JsonMapper.builder().changeDefaultPropertyInclusion(value ->
            value.withValueInclusion(JsonInclude.Include.NON_EMPTY)).build();
        for (Class<?> type : container.getDeclaredClasses()) {
            if (!type.isRecord()) continue;
            assertEquals(JsonInclude.Include.ALWAYS, type.getAnnotation(JsonInclude.class).value(), type.getSimpleName());
            RecordComponent[] components = type.getRecordComponents();
            Object[] values = new Object[components.length];
            Class<?>[] parameterTypes = new Class<?>[components.length];
            for (int index = 0; index < components.length; index++) {
                Class<?> componentType = components[index].getType();
                parameterTypes[index] = componentType;
                values[index] = componentType == int.class ? 1 : componentType == boolean.class ? false
                    : componentType == List.class ? new ArrayList<>() : null;
            }
            Object record = type.getDeclaredConstructor(parameterTypes).newInstance(values);
            JsonNode result = nonEmpty.valueToTree(record);
            assertEquals(Set.copyOf(Arrays.stream(components).map(RecordComponent::getName).toList()), result.propertyNames());
            for (RecordComponent component : components) {
                if (component.getType() == List.class) assertEquals(0, result.get(component.getName()).size());
            }
        }
    }

    @Test
    void test_responseListsAreDefensiveAndNeverNormalizeInvalidNulls() throws Exception {
        for (Class<?> container : List.of(RiskRuleSetModels.class, RiskTestCandidateModels.class)) {
            for (Class<?> type : container.getDeclaredClasses()) {
                if (!type.isRecord() || !type.getSimpleName().endsWith("ResponseBody")) continue;
                var components = type.getRecordComponents();
                Object[] values = new Object[components.length];
                Class<?>[] types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
                for (int index = 0; index < types.length; index++) {
                    values[index] = types[index] == int.class ? 1 : types[index] == boolean.class ? false
                        : types[index] == List.class ? new ArrayList<>() : null;
                }
                Object response = type.getDeclaredConstructor(types).newInstance(values);
                for (int index = 0; index < types.length; index++) {
                    if (types[index] != List.class) continue;
                    @SuppressWarnings("unchecked") List<Object> original = (List<Object>) values[index];
                    original.add(null);
                    List<?> snapshot = (List<?>) components[index].getAccessor().invoke(response);
                    assertTrue(snapshot.isEmpty());
                    assertThrows(UnsupportedOperationException.class, () -> snapshot.add(null));
                    Object[] invalid = values.clone();
                    invalid[index] = null;
                    assertInstanceOf(NullPointerException.class, assertThrows(java.lang.reflect.InvocationTargetException.class,
                        () -> type.getDeclaredConstructor(types).newInstance(invalid)).getCause());
                    original.clear();
                }
            }
        }
    }

    @Test
    void test_everyCommandAndNestedPropertyDeclaresRequiredIncludingNullable() {
        for (Class<?> type : List.of(CreateRuleSetVersionRequestBody.class, RuleRequestBody.class,
            ConditionNodeRequestBody.class, OperandRequestBody.class, RuleActionRequestBody.class,
            FreezeRuleSetVersionRequestBody.class, CreateTestCandidateRequestBody.class,
            ValidateTestCandidateRequestBody.class)) {
            for (RecordComponent component : type.getRecordComponents()) {
                if (type == RuleRequestBody.class && List.of("reasonMessage", "additionalActions").contains(component.getName())) continue;
                if (type == CreateRuleSetVersionRequestBody.class && component.getName().equals("operandFormat")) continue;
                if (type == OperandRequestBody.class && Set.of("variableCode", "numericUnit", "currencyCode", "currencyVariableCode").contains(component.getName())) continue;
                JsonProperty property = component.getAccessor().getAnnotation(JsonProperty.class);
                assertNotNull(property, type.getSimpleName() + "." + component.getName());
                assertEquals(OptBoolean.TRUE, property.isRequired());
            }
        }
    }

    @Test
    void test_nullableQueriesUseCodeAndClosedStatusPatterns() {
        for (String status : Arrays.asList(null, "DRAFT", "FROZEN", "RETIRED")) {
            assertTrue(VALIDATION.getValidator().validate(new RuleSetQueryRequestBody(null, null, status)).isEmpty());
        }
        for (String status : Arrays.asList(null, "DRAFT", "VALIDATING", "REJECTED")) {
            assertTrue(VALIDATION.getValidator().validate(new CandidateQueryRequestBody(null, status)).isEmpty());
        }
        for (Object query : List.of(new RuleSetQueryRequestBody("bad", null, null),
            new RuleSetQueryRequestBody(null, "bad", null), new RuleSetQueryRequestBody(null, null, "ACTIVE"),
            new CandidateQueryRequestBody("", null), new CandidateQueryRequestBody(null, "APPROVED"))) {
            assertFalse(VALIDATION.getValidator().validate(query).isEmpty());
        }
    }

    @Test
    void test_recordComponentsFollowTheBrowserWireTypeAndOrderContract() {
        assertSignature(RuleSetQueryRequestBody.class, "String pointCode,String ruleSetCode,String status");
        assertSignature(CreateRuleSetVersionRequestBody.class, "String pointCode,String ruleSetCode,String ruleSetName,"
            + "String baseRuleSetVersionId,String baseContentHash,String changeReason,List<RuleRequestBody> rules,String operandFormat");
        assertSignature(RuleRequestBody.class, "String ruleCode,String ruleName,Integer priorityNo,Boolean enabledFlag,"
            + "String reasonCode,List<ConditionNodeRequestBody> conditionNodes,RuleActionRequestBody action,String reasonMessage,List<AdditionalAction> additionalActions");
        assertSignature(ConditionNodeRequestBody.class, "String nodeKey,String parentNodeKey,Integer sequenceNo,String nodeType,"
            + "String groupOperator,String comparisonOperator,List<OperandRequestBody> operands");
        assertSignature(OperandRequestBody.class, "String operandRole,Integer valueSequence,String sourceType,"
            + "String fieldCode,String valueType,Object literalValue,String variableCode,String numericUnit,String currencyCode,String currencyVariableCode");
        assertSignature(RuleActionRequestBody.class, "String actionCode,Integer actionVersionNo");
        assertSignature(FreezeRuleSetVersionRequestBody.class, "String expectedContentHash");
        assertSignature(RuleAuthoringContextResponseBody.class, "String descriptorCode,DecisionPointDescriptorResponseBody point,"
            + "List<DecisionInputFieldDescriptorResponseBody> fields,List<String> operators,List<DecisionActionDescriptorResponseBody> actions,"
            + "List<Void> features,List<ActionParameterDescriptor> actionParameters,List<Void> obligations,List<Void> limitBindings,List<TargetBindingDescriptor> targetBindings,String operandFormat,VariableQueryContext variableQueryContext");
        assertSignature(DecisionPointDescriptorResponseBody.class, "String pointCode,String pointName,String businessDomain,"
            + "String inputContractCode,int inputContractVersion,String outputContractCode,String defaultDecision,"
            + "String evaluationStrategy,String failureStrategy,int timeoutMillis,String status");
        assertSignature(DecisionInputFieldDescriptorResponseBody.class, "String fieldCode,String valueType,boolean requiredFlag,"
            + "String dataClassification,boolean ruleReferenceFlag,boolean actionParameterFlag,String variableCode");
        assertSignature(DecisionActionDescriptorResponseBody.class, "String actionCode,String actionName,String actionCategory,"
            + "int versionNo,boolean terminalFlag,String decisionCode,String mergePolicy,boolean shadowSupportedFlag,String status");
        String ruleSetMetadata = "String ruleSetVersionId,String pointVersionId,String pointCode,String ruleSetCode,String ruleSetName,"
            + "int versionNo,String status,String contentHash,String createdBy,String changeReason,String createTime,String frozenBy,String frozenTime";
        assertSignature(RuleSetListItemResponseBody.class, ruleSetMetadata + ",String ruleSetId,String managementVersion,boolean inUse");
        assertSignature(DeleteRuleSetRequestBody.class, "String expectedVersion");
        assertSignature(DeleteRuleSetResponseBody.class, "String ruleSetId,String status,String version,String deletedBy,String deletedTime");
        assertSignature(RuleSetDetailResponseBody.class, ruleSetMetadata + ",List<RuleRequestBody> rules,String operandFormat,List<VariableSummary> currentVariables,String valuesAsOf,List<VariableIssue> variableIssues");
        assertSignature(CreateRuleSetVersionResponseBody.class, "String ruleSetVersionId,String pointVersionId,String ruleSetCode,"
            + "int versionNo,String status,String contentHash,String createdBy,String createTime");
        assertSignature(FreezeRuleSetVersionResponseBody.class, "String ruleSetVersionId,String pointVersionId,String ruleSetCode,"
            + "int versionNo,String status,String contentHash,String frozenBy,String frozenTime");
        assertSignature(CandidateQueryRequestBody.class, "String pointCode,String status");
        assertSignature(CreateTestCandidateRequestBody.class, "String ruleSetVersionId,String expectedRuleSetContentHash");
        assertSignature(ValidateTestCandidateRequestBody.class, "Integer expectedVersion");
        String candidateMetadata = "String releaseId,int releaseNo,String decisionPointId,String pointCode,String pointVersionId,"
            + "String ruleSetVersionId,String environmentCode,String status,int version,String contentHash,String createdBy,String createTime";
        assertSignature(TestCandidateListItemResponseBody.class, candidateMetadata + ",Boolean allStaticChecksPassed");
        assertSignature(TestCandidateDetailResponseBody.class, candidateMetadata + ",Boolean allStaticChecksPassed,boolean waitingForShadow,"
            + "FrozenCompositionResponseBody frozenComposition,List<ReleaseStateChangeResponseBody> stateChanges,"
            + "List<ReleaseStaticCheckResponseBody> staticChecks");
        assertSignature(FrozenCompositionResponseBody.class, "String decisionPointId,String pointCode,String pointVersionId,"
            + "int pointVersionNo,String pointVersionContentHash,String ruleSetVersionId,String ruleSetCode,"
            + "int ruleSetVersionNo,String ruleSetContentHash");
        assertSignature(ReleaseStateChangeResponseBody.class, "String auditEventId,String eventTime,String fromStatus,String toStatus,"
            + "String reasonCode,String triggerType,String triggerId,String actorId,int aggregateVersion");
        assertSignature(ReleaseStaticCheckResponseBody.class, "String auditEventId,String eventTime,String checkCode,"
            + "String checkStatus,String severity,String errorCode,String errorSummary");
        assertSignature(InitialStateChangeResponseBody.class, "String auditEventId,String eventTime,String fromStatus,String toStatus,"
            + "String reasonCode,String triggerType,String actorId,int aggregateVersion");
        assertSignature(ValidationCheckResponseBody.class, "String checkCode,String checkStatus,String severity,String errorCode,String errorSummary");
        assertSignature(CreateTestCandidateResponseBody.class, candidateMetadata + ",InitialStateChangeResponseBody initialStateChange");
        assertSignature(ValidateTestCandidateResponseBody.class, "String releaseId,int releaseNo,String environmentCode,String status,"
            + "int version,String contentHash,boolean allStaticChecksPassed,List<ValidationCheckResponseBody> checks");
    }

    private static void assertSignature(Class<?> record, String expected) {
        String actual = Arrays.stream(record.getRecordComponents()).map(component -> component.getGenericType().getTypeName()
            .replace("java.lang.", "").replace("java.util.", "")
            .replace(RiskRuleSetModels.class.getName() + "$", "")
            .replace(RiskTestCandidateModels.class.getName() + "$", "")
            .replace(com.paytech.operations.console.bff.service.model.RiskVariableModels.class.getName() + "$", "") + " " + component.getName())
            .collect(java.util.stream.Collectors.joining(","));
        assertEquals(expected, actual, record.getSimpleName());
    }

    private static List<String> createPaths() {
        var paths = new ArrayList<>(TEXT_PATHS);
        paths.addAll(INTEGER_PATHS);
        paths.addAll(LIST_PATHS);
        paths.addAll(List.of(RULE + "/enabledFlag", RULE + "/action", OPERAND + "/literalValue"));
        return paths;
    }

    private static JacksonJsonHttpMessageConverter converter() {
        var builder = HttpMessageConverters.forServer().registerDefaults();
        new RiskRequestJsonConfiguration(JSON).configureMessageConverters(builder);
        return (JacksonJsonHttpMessageConverter) builder.build().iterator().next();
    }

    private static <T> T decode(String payload, Class<T> type) throws Exception {
        return type.cast(converter().read(ResolvableType.forClass(type), input(payload), Map.of()));
    }

    private static MockHttpInputMessage input(String payload) {
        var input = new MockHttpInputMessage(payload.getBytes(StandardCharsets.UTF_8));
        input.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return input;
    }

    private static void assertShapeError(String payload, Class<?> type) {
        var failure = assertThrows(HttpMessageNotReadableException.class, () -> decode(payload, type));
        assertFalse(failure.toString().contains("must-not-leak"));
        assertNull(failure.getCause(), "Jackson diagnostics can include confidential properties and literals");
        var mapped = exceptionMapper().map(failure, new MockHttpServletRequest());
        assertEquals(400, mapped.status().value());
        assertEquals("INVALID_REQUEST", mapped.errorCode().getCode());
    }

    private static void assertValidationError(String payload, Class<?> type, String field) throws Exception {
        Object request = decode(payload, type);
        var binding = new BeanPropertyBindingResult(request, "request");
        new SpringValidatorAdapter(VALIDATION.getValidator()).validate(request, binding);
        assertTrue(binding.hasErrors(), field);
        var parameter = new MethodParameter(RiskDecisionConfigurationModelAutoTest.class
            .getDeclaredMethod("bodyParameter", type), 0);
        var mapped = exceptionMapper().map(new MethodArgumentNotValidException(parameter, binding), new MockHttpServletRequest());
        assertEquals(422, mapped.status().value());
        assertEquals("VALIDATION_ERROR", mapped.errorCode().getCode());
        assertTrue(mapped.errors().stream().anyMatch(error -> error.field().contains(field)), field);
        assertFalse(mapped.errors().toString().contains("Policy review"));
    }

    private static MvcProblemExceptionMapper exceptionMapper() {
        return new MvcProblemExceptionMapper((code, status) -> status, new RequestBodyFieldErrorProjector(JSON));
    }

    private static void bodyParameter(@RequestBody CreateRuleSetVersionRequestBody request) {}
    private static void bodyParameter(@RequestBody FreezeRuleSetVersionRequestBody request) {}
    private static void bodyParameter(@RequestBody CreateTestCandidateRequestBody request) {}
    private static void bodyParameter(@RequestBody ValidateTestCandidateRequestBody request) {}

    private static String replace(String payload, String path, String value) {
        ObjectNode root = (ObjectNode) JSON.readTree(payload);
        parent(root, path).set(leaf(path), JSON.readTree(value));
        return root.toString();
    }

    private static ObjectNode parent(ObjectNode root, String path) {
        return (ObjectNode) root.at(path.substring(0, path.lastIndexOf('/')));
    }

    private static String leaf(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String quote(String value) {
        return JSON.writeValueAsString(value);
    }
}
