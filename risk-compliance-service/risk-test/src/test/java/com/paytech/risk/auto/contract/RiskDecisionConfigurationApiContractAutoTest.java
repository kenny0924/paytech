package com.paytech.risk.auto.contract;

import static com.paytech.risk.api.decision.DecisionReleaseManagementModels.*;
import static com.paytech.risk.api.decision.DecisionRuleSetManagementModels.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.paytech.foundation.web.api.ApiErrorStatusResolver;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.risk.api.decision.DecisionReleaseManagementApi;
import com.paytech.risk.api.decision.DecisionRuleSetManagementApi;
import com.paytech.risk.api.decision.request.*;
import com.paytech.risk.api.decision.response.*;
import com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode;
import com.paytech.risk.configuration.RiskApiErrorConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 发布给 Operations BFF 的 Risk 决策配置 HTTP 合同。
 *
 * <p>原九项配置合同加本轮规则集整体删除，沿用既有路径版本。
 */
class RiskDecisionConfigurationApiContractAutoTest {

    private static final ObjectMapper STRICT_MAPPER = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void test_tenFormalRoutesAndRawHeadersAreExact() {
        assertEquals("/decision-rule-sets/v1", httpBase(DecisionRuleSetManagementApi.class));
        assertEquals("/decision-releases/v1", httpBase(DecisionReleaseManagementApi.class));

        assertEquals(Map.of(
            "GET /authoring-context/{pointCode}", "getAuthoringContext",
            "GET /rule-sets", "listRuleSets",
            "GET /rule-sets/{ruleSetVersionId}", "getRuleSet",
            "POST /rule-sets", "createRuleSetVersion",
            "DELETE /rule-sets/{ruleSetId}", "deleteRuleSet",
            "POST /rule-sets/{ruleSetVersionId}/freeze", "freezeRuleSetVersion",
            "GET /candidates", "listCandidates",
            "GET /candidates/{releaseId}", "getCandidate",
            "POST /candidates", "createCandidate",
            "POST /candidates/{releaseId}/validate", "validateCandidate"), routes(
            DecisionRuleSetManagementApi.class, DecisionReleaseManagementApi.class));

        for (Method method : allMethods(DecisionRuleSetManagementApi.class, DecisionReleaseManagementApi.class)) {
            Set<String> headers = requestHeaders(method);
            // HTTP Idempotency-Key 归 Gateway，命令与查询都只接收可信操作者与会话。
            assertEquals(Set.of("X-Gateway-Actor-Subject", "X-Gateway-Session-Id"), headers, method::getName);
        }
    }

    @Test
    void test_fiveWriteRequestsHaveRequiredMetadataAndExactShapes() {
        assertRecord(CreateRuleSetVersionRequestBody.class,
            "pointCode:String", "ruleSetCode:String", "ruleSetName:String", "baseRuleSetVersionId:String",
            "baseContentHash:String", "changeReason:String", "rules:List", "operandFormat:String");
        assertRecord(RuleRequestBody.class,
            "ruleCode:String", "ruleName:String", "priorityNo:Integer", "enabledFlag:Boolean",
            "reasonCode:String", "conditionNodes:List", "action:RuleActionRequestBody", "reasonMessage:String", "additionalActions:List");
        assertRecord(ConditionNodeRequestBody.class,
            "nodeKey:String", "parentNodeKey:String", "sequenceNo:Integer", "nodeType:String",
            "groupOperator:String", "comparisonOperator:String", "operands:List");
        assertRecord(OperandRequestBody.class,
            "operandRole:String", "valueSequence:Integer", "sourceType:String", "fieldCode:String",
            "valueType:String", "literalValue:Object", "variableCode:String", "numericUnit:String", "currencyCode:String", "currencyVariableCode:String");
        assertRecord(RuleActionRequestBody.class, "actionCode:String", "actionVersionNo:Integer");
        assertRecord(DeleteRuleSetRequestBody.class, "expectedVersion:String");
        assertRecord(FreezeRuleSetVersionRequestBody.class, "expectedContentHash:String");
        assertRecord(CreateTestReleaseRequestBody.class,
            "ruleSetVersionId:String", "expectedRuleSetContentHash:String");
        assertRecord(ValidateTestReleaseRequestBody.class, "expectedVersion:Integer");
        assertListElementType(CreateRuleSetVersionRequestBody.class, "rules", RuleRequestBody.class);
        assertListElementType(RuleRequestBody.class, "conditionNodes", ConditionNodeRequestBody.class);
        assertListElementType(ConditionNodeRequestBody.class, "operands", OperandRequestBody.class);

        for (Class<?> requestType : List.of(CreateRuleSetVersionRequestBody.class, RuleRequestBody.class,
            ConditionNodeRequestBody.class, OperandRequestBody.class, RuleActionRequestBody.class,
            DeleteRuleSetRequestBody.class, FreezeRuleSetVersionRequestBody.class, CreateTestReleaseRequestBody.class,
            ValidateTestReleaseRequestBody.class)) {
            for (Field component : instanceFields(requestType)) {
                if (requestType == RuleRequestBody.class && List.of("reasonMessage", "additionalActions").contains(component.getName())) continue;
                // 新格式由请求 Reader 要求显式键；旧成功请求仍允许省略新增键。
                if (requestType == CreateRuleSetVersionRequestBody.class && component.getName().equals("operandFormat")) continue;
                if (requestType == OperandRequestBody.class && List.of("variableCode", "numericUnit", "currencyCode", "currencyVariableCode").contains(component.getName())) continue;
                JsonProperty property = component.getAnnotation(JsonProperty.class);
                assertNotNull(property, requestType.getSimpleName() + "." + component.getName());
                assertEquals(OptBoolean.TRUE, property.isRequired(), requestType.getSimpleName() + "." + component.getName());
            }
        }

        for (Method method : allMethods(DecisionRuleSetManagementApi.class, DecisionReleaseManagementApi.class)) {
            if (isCommand(method)) {
                Parameter body = List.of(method.getParameters()).stream()
                    .filter(parameter -> parameter.isAnnotationPresent(RequestBody.class)).findFirst().orElseThrow();
                assertTrue(body.isAnnotationPresent(Valid.class), method::getName);
            }
        }
    }

    @Test
    void test_strictJsonRejectsUnknownAndLiteralOnlyAllowsStringBooleanOrNull() throws Exception {
        String create = """
            {"pointCode":"CHECKOUT_PREPARE","ruleSetCode":"RULES","ruleSetName":"Rules",
             "baseRuleSetVersionId":null,"baseContentHash":null,"changeReason":"initial","rules":[
               {"ruleCode":"RULE","ruleName":"Rule","priorityNo":1,"enabledFlag":true,"reasonCode":null,
                "conditionNodes":[{"nodeKey":"root","parentNodeKey":null,"sequenceNo":1,"nodeType":"PREDICATE",
                "groupOperator":null,"comparisonOperator":"EQ","operands":[
                  {"operandRole":"LEFT","valueSequence":1,"sourceType":"CONTEXT","fieldCode":"country_code","valueType":"STRING","literalValue":null},
                  {"operandRole":"RIGHT","valueSequence":1,"sourceType":"LITERAL","fieldCode":null,"valueType":"STRING","literalValue":"AED"}] }],
                "action":{"actionCode":"ALLOW_CHECKOUT","actionVersionNo":1}}]}
            """;
        assertNotNull(STRICT_MAPPER.readValue(create, CreateRuleSetVersionRequestBody.class));
        // 必填键缺失由 HTTP 入口的严格 Reader 与内部 Request 校验拒绝，普通 class 在 Jackson 层不强制，
        // 见 DecisionRuleSetManagementHttpAutoTest / DecisionReleaseManagementHttpAutoTest 的缺键用例。
        assertThrows(Exception.class, () -> STRICT_MAPPER.readValue(create.replace("\"literalValue\":null", "\"literalValue\":1"),
            CreateRuleSetVersionRequestBody.class));
        assertThrows(Exception.class, () -> STRICT_MAPPER.readValue(create.replace("\"literalValue\":null", "\"literalValue\":{}"),
            CreateRuleSetVersionRequestBody.class));
        assertThrows(Exception.class, () -> STRICT_MAPPER.readValue(create.replace("\"literalValue\":null", "\"literalValue\":[]"),
            CreateRuleSetVersionRequestBody.class));
        assertThrows(Exception.class, () -> STRICT_MAPPER.readValue(create.replace("\"pointCode\":\"CHECKOUT_PREPARE\"",
            "\"pointCode\":\"CHECKOUT_PREPARE\",\"unexpected\":true"), CreateRuleSetVersionRequestBody.class));
        assertThrows(Exception.class, () -> STRICT_MAPPER.readValue(create.replace("\"nodeKey\":\"root\"",
            "\"nodeKey\":\"root\",\"unexpectedNested\":true"), CreateRuleSetVersionRequestBody.class));

        CreateRuleSetVersionRequestBody nullableBase = STRICT_MAPPER.readValue(create,
            CreateRuleSetVersionRequestBody.class);
        assertTrue(VALIDATOR.validate(nullableBase).isEmpty());
        CreateRuleSetVersionRequestBody nullPoint = STRICT_MAPPER.readValue(create.replace(
            "\"pointCode\":\"CHECKOUT_PREPARE\"", "\"pointCode\":null"), CreateRuleSetVersionRequestBody.class);
        assertFalse(VALIDATOR.validate(nullPoint).isEmpty());
        assertEquals(Boolean.TRUE, STRICT_MAPPER.readValue(create.replace("\"literalValue\":null", "\"literalValue\":true"),
            CreateRuleSetVersionRequestBody.class).getRules().getFirst().getConditionNodes().getFirst().getOperands().getFirst().getLiteralValue());
        assertEquals("AED", STRICT_MAPPER.readValue(create, CreateRuleSetVersionRequestBody.class)
            .getRules().getFirst().getConditionNodes().getFirst().getOperands().get(1).getLiteralValue());
        CreateRuleSetVersionRequestBody genericLowercase = STRICT_MAPPER.readValue(create.replace(
            "\"country_code\"", "\"merchant_label\""), CreateRuleSetVersionRequestBody.class);
        assertTrue(VALIDATOR.validate(genericLowercase).isEmpty());
    }

    @Test
    void test_getAndCommandSchemasUseExactTypesExplicitNullsAndDefensiveLists() throws Exception {
        assertRecord(RuleAuthoringContextResponseBody.class,
            "descriptorCode:String", "point:DecisionPointDescriptorResponseBody", "fields:List", "operators:List",
            "actions:List", "features:List", "actionParameters:List", "obligations:List", "limitBindings:List", "targetBindings:List", "operandFormat:String", "variableQueryContext:VariableQueryContext");
        assertRecord(RuleSetListItemResponseBody.class,
            "ruleSetVersionId:String", "pointVersionId:String", "pointCode:String", "ruleSetCode:String", "ruleSetName:String",
            "versionNo:Integer", "status:String", "contentHash:String", "createdBy:String", "changeReason:String",
            "createTime:String", "frozenBy:String", "frozenTime:String",
            "ruleSetId:String", "managementVersion:String", "inUse:Boolean");
        assertRecord(RuleSetDetailResponseBody.class,
            "ruleSetVersionId:String", "pointVersionId:String", "pointCode:String", "ruleSetCode:String", "ruleSetName:String",
            "versionNo:Integer", "status:String", "contentHash:String", "createdBy:String", "changeReason:String",
            "createTime:String", "frozenBy:String", "frozenTime:String", "rules:List", "operandFormat:String", "currentVariables:List", "valuesAsOf:String", "variableIssues:List");
        assertRecord(TestReleaseListItemResponseBody.class,
            "releaseId:String", "releaseNo:Integer", "decisionPointId:String", "pointCode:String", "pointVersionId:String",
            "ruleSetVersionId:String", "environmentCode:String", "status:String", "version:Integer", "contentHash:String",
            "createdBy:String", "createTime:String", "allStaticChecksPassed:Boolean");
        assertRecord(TestReleaseDetailResponseBody.class,
            "releaseId:String", "releaseNo:Integer", "decisionPointId:String", "pointCode:String", "pointVersionId:String",
            "ruleSetVersionId:String", "environmentCode:String", "status:String", "version:Integer", "contentHash:String",
            "createdBy:String", "createTime:String", "allStaticChecksPassed:Boolean", "waitingForShadow:Boolean",
            "frozenComposition:FrozenCompositionResponseBody", "stateChanges:List", "staticChecks:List");
        assertRecord(CreateRuleSetVersionResponseBody.class,
            "ruleSetVersionId:String", "pointVersionId:String", "ruleSetCode:String", "versionNo:Integer", "status:String",
            "contentHash:String", "createdBy:String", "createTime:String");
        assertRecord(DeleteRuleSetResponseBody.class, "ruleSetId:String", "status:String", "version:String",
            "deletedBy:String", "deletedTime:String");
        assertRecord(FreezeRuleSetVersionResponseBody.class,
            "ruleSetVersionId:String", "pointVersionId:String", "ruleSetCode:String", "versionNo:Integer", "status:String",
            "contentHash:String", "frozenBy:String", "frozenTime:String");
        assertRecord(CreateTestReleaseResponseBody.class,
            "releaseId:String", "releaseNo:Integer", "decisionPointId:String", "pointCode:String", "pointVersionId:String",
            "ruleSetVersionId:String", "environmentCode:String", "status:String", "version:Integer", "contentHash:String",
            "createdBy:String", "createTime:String", "initialStateChange:InitialStateChangeResponseBody");
        assertRecord(ValidateTestReleaseResponseBody.class,
            "releaseId:String", "releaseNo:Integer", "environmentCode:String", "status:String", "version:Integer",
            "contentHash:String", "allStaticChecksPassed:Boolean", "checks:List");
        assertRecord(InitialStateChangeResponseBody.class,
            "auditEventId:String", "eventTime:String", "fromStatus:String", "toStatus:String", "reasonCode:String",
            "triggerType:String", "actorId:String", "aggregateVersion:Integer");
        assertRecord(ValidationCheckResponseBody.class,
            "checkCode:String", "checkStatus:String", "severity:String", "errorCode:String", "errorSummary:String");
        assertRecord(DecisionPointDescriptorResponseBody.class,
            "pointCode:String", "pointName:String", "businessDomain:String", "inputContractCode:String",
            "inputContractVersion:Integer", "outputContractCode:String", "defaultDecision:String",
            "evaluationStrategy:String", "failureStrategy:String", "timeoutMillis:Integer", "status:String");
        assertRecord(DecisionInputFieldDescriptorResponseBody.class,
            "fieldCode:String", "valueType:String", "requiredFlag:Boolean", "dataClassification:String",
            "ruleReferenceFlag:Boolean", "actionParameterFlag:Boolean", "variableCode:String");
        assertRecord(DecisionActionDescriptorResponseBody.class,
            "actionCode:String", "actionName:String", "actionCategory:String", "versionNo:Integer", "handlerCode:String",
            "implementationVersion:String", "implementationHash:String", "terminalFlag:Boolean", "decisionCode:String",
            "mergePolicy:String", "shadowSupportedFlag:Boolean", "status:String");
        assertRecord(FrozenCompositionResponseBody.class,
            "decisionPointId:String", "pointCode:String", "pointVersionId:String", "pointVersionNo:Integer",
            "pointVersionContentHash:String", "ruleSetVersionId:String", "ruleSetCode:String", "ruleSetVersionNo:Integer",
            "ruleSetContentHash:String");
        assertRecord(ReleaseStateChangeResponseBody.class,
            "auditEventId:String", "eventTime:String", "fromStatus:String", "toStatus:String", "reasonCode:String",
            "triggerType:String", "triggerId:String", "actorId:String", "aggregateVersion:Integer");
        assertRecord(ReleaseStaticCheckResponseBody.class,
            "auditEventId:String", "eventTime:String", "checkCode:String", "checkStatus:String", "severity:String",
            "errorCode:String", "errorSummary:String");
        assertListElementType(RuleAuthoringContextResponseBody.class, "fields", DecisionInputFieldDescriptorResponseBody.class);
        assertListElementType(RuleAuthoringContextResponseBody.class, "operators", String.class);
        assertListElementType(RuleAuthoringContextResponseBody.class, "actions", DecisionActionDescriptorResponseBody.class);
        assertListElementType(RuleAuthoringContextResponseBody.class, "features", Void.class);
        assertListElementType(RuleAuthoringContextResponseBody.class, "actionParameters", ActionParameterDescriptor.class);
        assertListElementType(RuleAuthoringContextResponseBody.class, "obligations", Void.class);
        assertListElementType(RuleAuthoringContextResponseBody.class, "limitBindings", Void.class);
        assertListElementType(RuleSetDetailResponseBody.class, "rules", RuleRequestBody.class);
        assertListElementType(TestReleaseDetailResponseBody.class, "stateChanges", ReleaseStateChangeResponseBody.class);
        assertListElementType(TestReleaseDetailResponseBody.class, "staticChecks", ReleaseStaticCheckResponseBody.class);
        assertListElementType(ValidateTestReleaseResponseBody.class, "checks", ValidationCheckResponseBody.class);

        List<String> operators = new java.util.ArrayList<>(List.of("EQ"));
        RuleAuthoringContextResponseBody context = new RuleAuthoringContextResponseBody("descriptor", null, List.of(), operators,
            List.of(), List.of(), List.of(), List.of(), List.of());
        operators.add("NE");
        assertEquals(List.of("EQ"), context.getOperators());
        assertThrows(UnsupportedOperationException.class, () -> context.getOperators().add("GT"));
        String responseJson = STRICT_MAPPER.writeValueAsString(new RuleSetListItemResponseBody(
            "1", "2", "POINT", "RULES", "Rules", 1, "DRAFT", "a".repeat(64), "3", "initial",
            "2026-09-05T00:00:00.000000Z", null, null, "100", "1", false));
        assertTrue(responseJson.contains("\"frozenBy\":null"));
        assertTrue(responseJson.contains("\"frozenTime\":null"));
        assertListSnapshots();
    }

    /** 保持原分页模型，逐一验证规则与字典的已发布错误状态。 */
    @Test
    void test_listsUseFoundationPagingAndApprovedErrorsKeepTheirStatus() {
        assertPagingElementType(DecisionRuleSetManagementApi.class, "listRuleSets", RuleSetListItemResponseBody.class);
        assertPagingElementType(DecisionReleaseManagementApi.class, "listCandidates", TestReleaseListItemResponseBody.class);
        assertArrayEquals(new RiskDecisionConfigurationErrorCode[] {
            RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND,
            RiskDecisionConfigurationErrorCode.RISK_RULE_SET_IN_USE,
            RiskDecisionConfigurationErrorCode.RISK_RULE_SET_CHANGED,
            RiskDecisionConfigurationErrorCode.RISK_RULE_SET_DELETED,
            RiskDecisionConfigurationErrorCode.RISK_FLOW_CODE_EXISTS,
            RiskDecisionConfigurationErrorCode.RISK_FLOW_VERSION_CONFLICT,
            RiskDecisionConfigurationErrorCode.RISK_FLOW_DELETED,
            RiskDecisionConfigurationErrorCode.RISK_FLOW_REFERENCE_UNAVAILABLE,
            RiskDecisionConfigurationErrorCode.RISK_DICTIONARY_NOT_FOUND,
            RiskDecisionConfigurationErrorCode.RISK_DICTIONARY_CODE_EXISTS,
            RiskDecisionConfigurationErrorCode.RISK_DICTIONARY_CHANGED,
            RiskDecisionConfigurationErrorCode.RISK_DICTIONARY_SELECTION_UNAVAILABLE}, RiskDecisionConfigurationErrorCode.values());
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(RiskApiErrorConfiguration.class)) {
            ApiErrorStatusResolver resolver = context.getBean(ApiErrorStatusResolver.class);
            assertEquals(HttpStatus.NOT_FOUND, resolver.resolve(
                RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND, HttpStatus.BAD_REQUEST));
            assertEquals(HttpStatus.NOT_FOUND, resolver.resolve(
                RiskDecisionConfigurationErrorCode.RISK_DICTIONARY_NOT_FOUND, HttpStatus.BAD_REQUEST));
            for (var code : List.of(RiskDecisionConfigurationErrorCode.RISK_RULE_SET_IN_USE,
                RiskDecisionConfigurationErrorCode.RISK_RULE_SET_CHANGED, RiskDecisionConfigurationErrorCode.RISK_RULE_SET_DELETED,
                RiskDecisionConfigurationErrorCode.RISK_DICTIONARY_CODE_EXISTS,
                RiskDecisionConfigurationErrorCode.RISK_DICTIONARY_CHANGED,
                RiskDecisionConfigurationErrorCode.RISK_DICTIONARY_SELECTION_UNAVAILABLE)) {
                assertEquals(HttpStatus.CONFLICT, resolver.resolve(code, HttpStatus.BAD_REQUEST));
            }
            assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, resolver.resolve(RiskDecisionConfigurationErrorCode.RISK_FLOW_REFERENCE_UNAVAILABLE, HttpStatus.BAD_REQUEST));
            for (var code : List.of(RiskDecisionConfigurationErrorCode.RISK_FLOW_CODE_EXISTS, RiskDecisionConfigurationErrorCode.RISK_FLOW_VERSION_CONFLICT, RiskDecisionConfigurationErrorCode.RISK_FLOW_DELETED)) assertEquals(HttpStatus.CONFLICT, resolver.resolve(code, HttpStatus.BAD_REQUEST));
            assertEquals(HttpStatus.CONFLICT, resolver.resolve(new OtherCode(), HttpStatus.CONFLICT));
        }
    }

    private static String httpBase(Class<?> apiType) {
        return apiType.getAnnotation(HttpExchange.class).value();
    }

    private static Map<String, String> routes(Class<?>... apiTypes) {
        return java.util.Arrays.stream(apiTypes).flatMap(api -> java.util.Arrays.stream(api.getDeclaredMethods()))
            .collect(java.util.stream.Collectors.toMap(RiskDecisionConfigurationApiContractAutoTest::route,
                Method::getName));
    }

    private static List<Method> allMethods(Class<?>... apiTypes) {
        return java.util.Arrays.stream(apiTypes).flatMap(api -> java.util.Arrays.stream(api.getDeclaredMethods())).toList();
    }

    private static String route(Method method) {
        DeleteExchange delete = method.getAnnotation(DeleteExchange.class);
        if (delete != null) return "DELETE " + delete.value();
        GetExchange get = method.getAnnotation(GetExchange.class);
        return get == null ? "POST " + method.getAnnotation(PostExchange.class).value() : "GET " + get.value();
    }

    private static boolean isCommand(Method method) {
        return method.isAnnotationPresent(PostExchange.class) || method.isAnnotationPresent(DeleteExchange.class);
    }

    private static Set<String> requestHeaders(Method method) {
        return List.of(method.getParameters()).stream().map(parameter -> parameter.getAnnotation(RequestHeader.class))
            .filter(java.util.Objects::nonNull).map(RequestHeader::value).collect(java.util.stream.Collectors.toSet());
    }

    /** HTTP 模型为普通 class，按实例字段声明顺序核对名称与类型。 */
    private static void assertRecord(Class<?> type, String... expectedComponents) {
        assertFalse(type.isRecord(), type::getName);
        assertEquals(List.of(expectedComponents), instanceFields(type).stream()
            .map(field -> field.getName() + ":" + field.getType().getSimpleName()).toList(), type::getName);
    }

    /** 非静态实例字段，按声明顺序。 */
    private static List<Field> instanceFields(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredFields())
            .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers())).toList();
    }

    private static void assertListElementType(Class<?> recordType, String componentName, Class<?> elementType) {
        Field component = instanceFields(recordType).stream()
            .filter(candidate -> candidate.getName().equals(componentName)).findFirst().orElseThrow();
        assertEquals(List.class, component.getType(), recordType.getName() + "." + componentName);
        ParameterizedType parameterizedType = (ParameterizedType) component.getGenericType();
        assertEquals(elementType, parameterizedType.getActualTypeArguments()[0], recordType.getName() + "." + componentName);
    }

    private static void assertPagingElementType(Class<?> apiType, String methodName, Class<?> elementType) {
        Method method = java.util.Arrays.stream(apiType.getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        ParameterizedType parameterizedType = (ParameterizedType) method.getGenericReturnType();
        assertEquals(PagingResponseBody.class, parameterizedType.getRawType(), methodName);
        assertEquals(elementType, parameterizedType.getActualTypeArguments()[0], methodName);
    }

    private static void assertListSnapshots() {
        RuleActionRequestBody action = new RuleActionRequestBody("ALLOW_CHECKOUT", 1);
        OperandRequestBody operand = new OperandRequestBody("LEFT", 1, "CONTEXT", "country_code", "STRING", null);
        assertSnapshot(new java.util.ArrayList<>(List.of(operand)), values -> new ConditionNodeRequestBody(
            "node", null, 1, "PREDICATE", null, "EQ", values).getOperands());
        ConditionNodeRequestBody node = new ConditionNodeRequestBody("node", null, 1, "PREDICATE", null, "EQ", List.of(operand));
        assertSnapshot(new java.util.ArrayList<>(List.of(node)), values -> new RuleRequestBody(
            "RULE", "Rule", 1, true, null, values, action).getConditionNodes());
        RuleRequestBody rule = new RuleRequestBody("RULE", "Rule", 1, true, null, List.of(node), action);
        assertSnapshot(new java.util.ArrayList<>(List.of(rule)), values -> new CreateRuleSetVersionRequestBody(
            "CHECKOUT_PREPARE", "RULES", "Rules", null, null, "initial", values).getRules());
        assertSnapshot(new java.util.ArrayList<>(List.of(rule)), values -> new RuleSetDetailResponseBody(
            "1", "2", "POINT", "RULES", "Rules", 1, "DRAFT", "a".repeat(64), "3", "initial",
            "2026-09-05T00:00:00.000000Z", null, null, values).getRules());
        ReleaseStateChangeResponseBody stateChange = new ReleaseStateChangeResponseBody(
            "1", "2026-09-05T00:00:00.000000Z", null, "DRAFT", "CREATED", "COMMAND", null, "3", 1);
        ReleaseStaticCheckResponseBody staticCheck = new ReleaseStaticCheckResponseBody(
            "2", "2026-09-05T00:00:00.000000Z", "AST_SHAPE", "PASSED", "INFO", null, null);
        assertSnapshot(new java.util.ArrayList<>(List.of(stateChange)), values -> new TestReleaseDetailResponseBody(
            "1", 1, "2", "POINT", "3", "4", "TEST", "DRAFT", 1, "a".repeat(64), "5",
            "2026-09-05T00:00:00.000000Z", null, false, null, values, List.of()).getStateChanges());
        assertSnapshot(new java.util.ArrayList<>(List.of(staticCheck)), values -> new TestReleaseDetailResponseBody(
            "1", 1, "2", "POINT", "3", "4", "TEST", "DRAFT", 1, "a".repeat(64), "5",
            "2026-09-05T00:00:00.000000Z", null, false, null, List.of(), values).getStaticChecks());
        ValidationCheckResponseBody check = new ValidationCheckResponseBody("AST_SHAPE", "PASSED", "INFO", null, null);
        assertSnapshot(new java.util.ArrayList<>(List.of(check)), values -> new ValidateTestReleaseResponseBody(
            "1", 1, "TEST", "VALIDATING", 2, "a".repeat(64), true, values).getChecks());
        assertEmptyVoidSnapshot((List<Void> values) -> new RuleAuthoringContextResponseBody(
            "descriptor", null, List.of(), List.of(), List.of(), values, List.of(), List.of(), List.of()).getFeatures());
        assertEmptyVoidSnapshot((List<com.paytech.risk.api.decision.DecisionRuleSetManagementModels.ActionParameterDescriptor> values) -> new RuleAuthoringContextResponseBody(
            "descriptor", null, List.of(), List.of(), List.of(), List.of(), values, List.of(), List.of()).getActionParameters());
        assertEmptyVoidSnapshot((List<Void> values) -> new RuleAuthoringContextResponseBody(
            "descriptor", null, List.of(), List.of(), List.of(), List.of(), List.of(), values, List.of()).getObligations());
        assertEmptyVoidSnapshot((List<Void> values) -> new RuleAuthoringContextResponseBody(
            "descriptor", null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), values).getLimitBindings());
        assertSnapshot(new java.util.ArrayList<>(List.of("EQ")), values -> new RuleAuthoringContextResponseBody(
            "descriptor", null, List.of(), values, List.of(), List.of(), List.of(), List.of(), List.of()).getOperators());
        DecisionInputFieldDescriptorResponseBody field = new DecisionInputFieldDescriptorResponseBody(
            "country_code", "STRING", true, "INTERNAL", true, false);
        assertSnapshot(new java.util.ArrayList<>(List.of(field)), values -> new RuleAuthoringContextResponseBody(
            "descriptor", null, values, List.of(), List.of(), List.of(), List.of(), List.of(), List.of()).getFields());
        DecisionActionDescriptorResponseBody descriptorAction = new DecisionActionDescriptorResponseBody(
            "ALLOW_CHECKOUT", "Allow Checkout", "DECISION", 1, "DECISION_ALLOW_V1", "1.0.0", "a".repeat(64),
            true, "ALLOW", "MOST_RESTRICTIVE", true, "ACTIVE");
        assertSnapshot(new java.util.ArrayList<>(List.of(descriptorAction)), values -> new RuleAuthoringContextResponseBody(
            "descriptor", null, List.of(), List.of(), values, List.of(), List.of(), List.of(), List.of()).getActions());
    }

    private static <T> void assertSnapshot(List<T> source, Function<List<T>, List<T>> construct) {
        List<T> snapshot = construct.apply(source);
        assertNotSame(source, snapshot);
        source.clear();
        assertFalse(snapshot.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(null));
    }

    private static <T> void assertEmptyVoidSnapshot(Function<List<T>, List<T>> construct) {
        List<T> source = new java.util.ArrayList<>();
        List<T> snapshot = construct.apply(source);
        assertNotSame(source, snapshot);
        source.add(null);
        assertTrue(snapshot.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(null));
    }

    private static final class OtherCode implements CodeMessageEnum {
        @Override public String getCode() { return "OTHER"; }
        @Override public String getMessage() { return "Other"; }
    }
}
