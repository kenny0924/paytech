package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.BUSINESS_CHECK_FAILED;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.risk.api.enums.RiskVariableErrorCode.LEGACY_AUTHORING_NOT_SUPPORTED;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.api.decision.DecisionRuleSetManagementApi;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.ActionParameter;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.AdditionalAction;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.ConditionNodeRequestBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.OperandRequestBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.RuleActionRequestBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.RuleRequestBody;
import com.paytech.risk.api.decision.request.CreateRuleSetVersionRequestBody;
import com.paytech.risk.test.base.MockTestBase;
import com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder;
import com.paytech.risk.test.checker.decision.RuleSetCommandResponseChecker;
import com.paytech.risk.test.checker.decision.RuleSetStoreChecker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 保存规则集版本的输入合同与规则 AST 校验
 * <li>字段约束：编码、名称、基准、原因、规则、节点与动作字段的格式和范围由 Bean Validation 拒绝（422）并定位字段。</li>
 * <li>报文合同：非 JSON、重复键、未知或缺失键、操作数十字段形状、旧格式保存、超大报文与非 JSON 媒体类型在业务前拒绝（400/413/415）。</li>
 * <li>内容安全：变更原因含凭据、联系方式、卡号或 JSON 时拒绝（400），响应不回显原文。</li>
 * <li>AST 规则：规则目录重复、单根、孤儿与不可达节点、兄弟顺序、分组与谓词矩阵、动作注册、拒绝文案与附加动作矩阵违反时拒绝（400）。</li>
 * <li>所有拒绝都不创建管理根或版本。</li>
 *
 * @see DecisionRuleSetManagementApi#createRuleSetVersion(String, String, CreateRuleSetVersionRequestBody)
 */
class RuleSetCreateInputApiAutoTest extends MockTestBase {
    private static final String PATH = "/decision-rule-sets/v1/rule-sets";

    @Autowired
    private RuleSetMocker ruleSets;

    @Autowired
    private RuleSetStoreChecker store;

    /** 字段格式与范围由入口 Bean Validation 拒绝，并定位到具体字段。 */
    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("invalidFields")
    void test_invalidParameter(String field, String scenario, Function<String, RuleSetCreateRequestBuilder> request) {
        String code = ruleSets.newRuleSetCode();

        invoke(json(request.apply(code).build())).assertProblem(422, VALIDATION_ERROR.getCode())
            .assertFieldError(field);

        store.assertNothingWritten(code);
    }

    /** 不符合 JSON 字段或类型合同的报文在业务前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedBodies")
    void test_invalidParameter_malformedBody(String scenario, BiFunction<ObjectMapper, String, String> body) {
        String code = ruleSets.newRuleSetCode();

        invoke(body.apply(objectMapper, code)).assertProblem(400, INVALID_REQUEST.getCode());

        store.assertNothingWritten(code);
    }

    /** 未声明 VARIABLE_REFERENCE 的旧格式保存不再支持。 */
    @Test
    void test_invalidParameter_legacyAuthoring() {
        String code = ruleSets.newRuleSetCode();
        OperandRequestBody context = new OperandRequestBody("LEFT", 1, "CONTEXT", "country_code", "STRING", null);
        RuleRequestBody legacyRule = new RuleRequestBody("LEGACY_RULE", "Legacy rule", 10, true, null,
            List.of(predicate("root", null, 1, "IS_NOT_NULL", context)), action(ALLOW));

        invoke(json(template(code).operandFormat(null).rules(legacyRule).build())).assertProblem(400,
            LEGACY_AUTHORING_NOT_SUPPORTED.name());
        // 旧格式请求混入统一变量操作数时在 AST 入口即被拒绝。
        invoke(json(template(code).operandFormat(null).build())).assertProblem(400, INVALID_REQUEST.getCode());

        store.assertNothingWritten(code);
    }

    /** 变更原因含敏感或结构化内容时拒绝，且错误响应不回显原文。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("unsafeChangeReasons")
    void test_invalidParameter_unsafeChangeReason(String changeReason) {
        String code = ruleSets.newRuleSetCode();

        RuleSetCommandResponseChecker response = invoke(json(template(code).changeReason(changeReason).build()))
            .assertProblem(400, INVALID_REQUEST.getCode());

        assertFalse(response.rawBody().contains(changeReason), "错误响应不得回显变更原因");
        store.assertNothingWritten(code);
    }

    /** 决策点不是当前批准的编排对象，或基准版本与摘要只给出一个。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDraftHeads")
    void test_invalidParameter_draftHead(String scenario, Function<String, RuleSetCreateRequestBuilder> request) {
        String code = ruleSets.newRuleSetCode();

        invoke(json(request.apply(code).build())).assertProblem(400, INVALID_REQUEST.getCode());

        store.assertNothingWritten(code);
    }

    /** 条件树深度超过 12 层。 */
    @Test
    void test_invalidParameter_treeTooDeep() {
        String code = ruleSets.newRuleSetCode();
        List<ConditionNodeRequestBody> nodes = new ArrayList<>();
        for (int depth = 1; depth <= 12; depth++) {
            nodes.add(group("n" + depth, depth == 1 ? null : "n" + (depth - 1), 1, "NOT"));
        }
        nodes.add(predicate("leaf", "n12", 1, "IS_NOT_NULL", variable("LEFT", 1, "INPUT_LOCALE", "STRING")));

        invoke(json(template(code).rules(new RuleRequestBody("DEEP_RULE", "Deep rule", 10, true, null, nodes,
            action(ALLOW), null, List.of())).build())).assertProblem(400, INVALID_REQUEST.getCode());

        store.assertNothingWritten(code);
    }

    /** 规则目录、AST 结构、动作与附加动作矩阵违反业务规则。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("astViolations")
    void test_businessRuleViolation_ruleAst(String scenario, Function<String, RuleSetCreateRequestBuilder> request) {
        String code = ruleSets.newRuleSetCode();

        invoke(json(request.apply(code).build())).assertProblem(400, BUSINESS_CHECK_FAILED.getCode());

        store.assertNothingWritten(code);
    }

    /** 非 JSON 请求在进入合同前以 415 拒绝。 */
    @Test
    void test_invalidParameter_unsupportedMediaType() {
        String code = ruleSets.newRuleSetCode();

        invoke(json(template(code).build()), MediaType.TEXT_PLAIN).assertProblem(415,
            UNSUPPORTED_MEDIA_TYPE.getCode());

        store.assertNothingWritten(code);
    }

    /** 超过 64 KiB 的报文在解析前以 413 拒绝。 */
    @Test
    void test_edgeCase_payloadTooLarge() {
        String code = ruleSets.newRuleSetCode();

        invoke(json(template(code).ruleSetName("N".repeat(70_000)).build())).assertProblem(413,
            INVALID_REQUEST.getCode());

        store.assertNothingWritten(code);
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(invalid("pointCode", "小写", code -> template(code).pointCode("checkout_prepare")),
            invalid("ruleSetCode", "小写", code -> template("mock_rule_set")),
            invalid("ruleSetCode", "超过 64 位", code -> template("R".repeat(65))),
            invalid("ruleSetName", "空白", code -> template(code).ruleSetName(" ")),
            invalid("ruleSetName", "超过 255 位", code -> template(code).ruleSetName("N".repeat(256))),
            invalid("baseRuleSetVersionId", "零", code -> template(code).base("0", "a".repeat(64))),
            invalid("baseRuleSetVersionId", "前导零", code -> template(code).base("012", "a".repeat(64))),
            invalid("baseContentHash", "大写", code -> template(code).base("123456789012345", "A".repeat(64))),
            invalid("baseContentHash", "63 位", code -> template(code).base("123456789012345", "a".repeat(63))),
            invalid("changeReason", "空白", code -> template(code).changeReason("  ")),
            invalid("changeReason", "超过 500 位", code -> template(code).changeReason("r".repeat(501))),
            invalid("rules", "空规则集", code -> template(code).rules()),
            invalid("operandFormat", "未知格式", code -> template(code).operandFormat("LEGACY")),
            invalid("rules[0].ruleCode", "小写",
                code -> template(code).rules(withRule(rule -> rule.setRuleCode("rule")))),
            invalid("rules[0].ruleName", "空白", code -> template(code).rules(withRule(rule -> rule.setRuleName(" ")))),
            invalid("rules[0].priorityNo", "零", code -> template(code).rules(withRule(rule -> rule.setPriorityNo(0)))),
            invalid("rules[0].reasonCode", "小写",
                code -> template(code).rules(withRule(rule -> rule.setReasonCode("x")))),
            invalid("rules[0].reasonMessage", "超过 500 位",
                code -> template(code).rules(withRule(rule -> rule.setReasonMessage("m".repeat(501))))),
            invalid("rules[0].conditionNodes", "空节点",
                code -> template(code).rules(withRule(rule -> rule.setConditionNodes(List.of())))),
            invalid("rules[0].conditionNodes[0].nodeKey", "含空格", code -> template(code).rules(withNode(node ->
                node.setNodeKey("root node")))),
            invalid("rules[0].conditionNodes[0].nodeType", "未知类型", code -> template(code).rules(withNode(node ->
                node.setNodeType("LEAF")))),
            invalid("rules[0].conditionNodes[0].groupOperator", "未知分组",
                code -> template(code).rules(withNode(node -> node.setGroupOperator("XOR")))),
            invalid("rules[0].conditionNodes[0].comparisonOperator", "未知比较",
                code -> template(code).rules(withNode(node -> node.setComparisonOperator("LIKE")))),
            invalid("rules[0].conditionNodes[0].sequenceNo", "零", code -> template(code).rules(withNode(node ->
                node.setSequenceNo(0)))),
            invalid("rules[0].action.actionCode", "小写", code -> template(code).rules(withRule(rule ->
                rule.setAction(new RuleActionRequestBody("allow", 1))))),
            invalid("rules[0].action.actionVersionNo", "零", code -> template(code).rules(withRule(rule ->
                rule.setAction(new RuleActionRequestBody(ALLOW, 0))))),
            invalid("rules[0].additionalActions[0].sequenceNo", "零",
                code -> template(code).rules(withAdditional(cooldown(0, "60000")))),
            invalid("rules[0].additionalActions[0].targetBindingCode", "小写",
                code -> template(code).rules(withAdditional(new AdditionalAction(1, COOLDOWN, 1, "client",
                    cooldown(1, "60000").getParameters(), CLIENT_VARIABLE)))),
            invalid("rules[0].additionalActions[0].parameters", "空参数",
                code -> template(code).rules(withAdditional(new AdditionalAction(1, COOLDOWN, 1, CLIENT_TARGET,
                    List.of(), CLIENT_VARIABLE)))),
            invalid("rules[0].additionalActions[0].parameters[0].parameterCode", "小写",
                code -> template(code).rules(withAdditional(new AdditionalAction(1, COOLDOWN, 1, CLIENT_TARGET,
                    List.of(new ActionParameter("duration", 1, "LITERAL", null, "DURATION", "60000")),
                    CLIENT_VARIABLE)))));
    }

    static Stream<Arguments> malformedBodies() {
        return Stream.of(raw("非法 JSON", (mapper, code) -> "{\"pointCode\":"),
            raw("空报文", (mapper, code) -> ""), raw("顶层为数组", (mapper, code) -> "[]"),
            raw("重复键", (mapper, code) -> json(mapper, template(code).build()).replaceFirst("\\{",
                "{\"ruleSetCode\":\"" + code + "\",")),
            raw("未知顶层字段", (mapper, code) -> mutate(mapper, code, root -> root.put("ruleSetVersion", 1))),
            raw("缺少 baseContentHash 键", (mapper, code) -> mutate(mapper, code, root -> root.remove("baseContentHash"))),
            raw("operandFormat 为数字", (mapper, code) -> mutate(mapper, code, root -> root.put("operandFormat", 1))),
            raw("rules 为对象", (mapper, code) -> mutate(mapper, code, root -> root.putObject("rules"))),
            raw("规则缺少 additionalActions", (mapper, code) -> mutate(mapper, code,
                root -> ruleNode(root).remove("additionalActions"))),
            raw("规则 additionalActions 为 null", (mapper, code) -> mutate(mapper, code,
                root -> ruleNode(root).putNull("additionalActions"))),
            raw("规则缺少 reasonCode 键",
                (mapper, code) -> mutate(mapper, code, root -> ruleNode(root).remove("reasonCode"))),
            raw("规则含未知字段", (mapper, code) -> mutate(mapper, code, root -> ruleNode(root).put("weight", 1))),
            raw("动作缺少 actionVersionNo 键", (mapper, code) -> mutate(mapper, code,
                root -> ((ObjectNode)ruleNode(root).get("action")).remove("actionVersionNo"))),
            raw("节点缺少 groupOperator 键",
                (mapper, code) -> mutate(mapper, code, root -> nodeNode(root).remove("groupOperator"))),
            raw("操作数缺少 currencyCode 键", (mapper, code) -> mutate(mapper, code,
                root -> operand(root, 0).remove("currencyCode"))),
            raw("操作数含旧 values 键", (mapper, code) -> mutate(mapper, code, root -> operand(root, 0).putArray("values"))),
            raw("统一变量操作数填写 fieldCode", (mapper, code) -> mutate(mapper, code,
                root -> operand(root, 0).put("fieldCode", "country_code"))),
            raw("操作数顺序为 0", (mapper, code) -> mutate(mapper, code, root -> operand(root, 0).put("valueSequence", 0))),
            raw("操作数顺序为字符串", (mapper, code) -> mutate(mapper, code,
                root -> operand(root, 0).put("valueSequence", "1"))),
            raw("操作数角色未知",
                (mapper, code) -> mutate(mapper, code, root -> operand(root, 0).put("operandRole", "MIDDLE"))),
            raw("操作数来源为旧 CONTEXT", (mapper, code) -> mutate(mapper, code,
                root -> operand(root, 0).put("sourceType", "CONTEXT"))),
            raw("字面量为数字", (mapper, code) -> mutate(mapper, code, root -> operand(root, 1).put("literalValue", 5))),
            raw("变量操作数附带字面量", (mapper, code) -> mutate(mapper, code,
                root -> operand(root, 0).put("literalValue", "AE"))),
            raw("附加动作主体变量为 null", (mapper, code) -> mutate(mapper, code, root -> additional(root)
                .putNull("subjectVariableCode"))),
            raw("附加动作参数缺少 literalValue 键", (mapper, code) -> mutate(mapper, code,
                root -> ((ObjectNode)additional(root).get("parameters").get(0)).remove("literalValue"))));
    }

    static Stream<String> unsafeChangeReasons() {
        return Stream.of("release\u0000reason", "release​reason", "{\"ticket\":\"OPS-123\"}", "[\"rotate\",\"key\"]",
            "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature", "api_key=sk_test_1234567890",
            "password = hunter2", "Bearer abcdefghijklmnop", "card candidate 4111-1111-1111-1111",
            "contact alice@example.com", "phone=0501234567", "手机号: +86 138 0013 8000", "call (415) 555-2671");
    }

    static Stream<Arguments> invalidDraftHeads() {
        return Stream.of(head("未批准的决策点", code -> template(code).pointCode("PAYMENT_AUTHORIZE")),
            head("只给基准版本", code -> template(code).base("123456789012345", null)),
            head("只给基准摘要", code -> template(code).base(null, "a".repeat(64))),
            head("基准版本超出 BIGINT", code -> template(code).base("9223372036854775808", "a".repeat(64))));
    }

    static Stream<Arguments> astViolations() {
        RuleRequestBody allow = countryAllow();
        return Stream.of(
            head("规则编码重复", code -> template(code).rules(allow, rule(allow.getRuleCode(), "Other", 20, action(ALLOW),
                countryPredicate("root", null, 1)))),
            head("规则优先级重复", code -> template(code).rules(allow, rule("OTHER_RULE", "Other", 10, action(ALLOW),
                countryPredicate("root", null, 1)))),
            head("节点键重复", code -> template(code).rules(rule("R", "R", 1, action(ALLOW), group("g", null, 1, "AND"),
                countryPredicate("g", "g", 1), countryPredicate("p", "g", 2)))),
            head("两个根节点", code -> template(code).rules(rule("R", "R", 1, action(ALLOW), countryPredicate("a", null, 1),
                countryPredicate("b", null, 2)))),
            head("根节点顺序不为 1", code -> template(code).rules(rule("R", "R", 1, action(ALLOW),
                countryPredicate("root", null, 2)))),
            head("孤儿节点", code -> template(code).rules(rule("R", "R", 1, action(ALLOW), group("g", null, 1, "NOT"),
                countryPredicate("a", "g", 1), countryPredicate("b", "missing", 1)))),
            head("不可达的环", code -> template(code).rules(rule("R", "R", 1, action(ALLOW), group("g", null, 1, "NOT"),
                countryPredicate("a", "g", 1), group("x", "y", 1, "NOT"), group("y", "x", 1, "NOT")))),
            head("兄弟顺序不连续", code -> template(code).rules(rule("R", "R", 1, action(ALLOW), group("g", null, 1, "AND"),
                countryPredicate("a", "g", 1), countryPredicate("b", "g", 3)))),
            head("AND 只有一个子节点", code -> template(code).rules(rule("R", "R", 1, action(ALLOW),
                group("g", null, 1, "AND"), countryPredicate("a", "g", 1)))),
            head("NOT 有两个子节点", code -> template(code).rules(rule("R", "R", 1, action(ALLOW),
                group("g", null, 1, "NOT"), countryPredicate("a", "g", 1), countryPredicate("b", "g", 2)))),
            head("GROUP 缺少分组运算符", code -> template(code).rules(rule("R", "R", 1, action(ALLOW),
                new ConditionNodeRequestBody("g", null, 1, "GROUP", null, null, List.of()),
                countryPredicate("a", "g", 1)))),
            head("GROUP 携带操作数", code -> template(code).rules(rule("R", "R", 1, action(ALLOW),
                new ConditionNodeRequestBody("g", null, 1, "GROUP", "NOT", null,
                    List.of(variable("LEFT", 1, "INPUT_LOCALE", "STRING"))), countryPredicate("a", "g", 1)))),
            head("PREDICATE 携带分组运算符", code -> template(code).rules(rule("R", "R", 1, action(ALLOW),
                new ConditionNodeRequestBody("root", null, 1, "PREDICATE", "AND", "EQ",
                    countryPredicate("x", null, 1).getOperands())))),
            head("IS_NOT_NULL 携带右侧值", code -> template(code).rules(rule("R", "R", 1, action(ALLOW),
                predicate("root", null, 1, "IS_NOT_NULL", variable("LEFT", 1, "INPUT_COUNTRY_CODE", "STRING"),
                    literal("RIGHT", 1, "STRING", "AE"))))),
            head("EQ 携带两个右侧值", code -> template(code).rules(rule("R", "R", 1, action(ALLOW),
                predicate("root", null, 1, "EQ", variable("LEFT", 1, "INPUT_COUNTRY_CODE", "STRING"),
                    literal("RIGHT", 1, "STRING", "AE"), literal("RIGHT", 2, "STRING", "SA"))))),
            head("STRING 做大小比较", code -> template(code).rules(rule("R", "R", 1, action(ALLOW),
                predicate("root", null, 1, "GT", variable("LEFT", 1, "INPUT_COUNTRY_CODE", "STRING"),
                    literal("RIGHT", 1, "STRING", "AE"))))),
            head("右侧声明类型与左侧不符", code -> template(code).rules(rule("R", "R", 1, action(ALLOW),
                predicate("root", null, 1, "EQ", variable("LEFT", 1, "INPUT_COUNTRY_CODE", "STRING"),
                    literal("RIGHT", 1, "NUMBER", "1"))))),
            head("PREDICATE 拥有子节点", code -> template(code).rules(rule("R", "R", 1, action(ALLOW),
                countryPredicate("root", null, 1), countryPredicate("child", "root", 1)))),
            head("终态动作版本未注册", code -> template(code).rules(rule("R", "R", 1,
                new RuleActionRequestBody(ALLOW, 2), countryPredicate("root", null, 1)))),
            head("非拒绝规则填写拒绝文案", code -> template(code).rules(rule("R", "R", 1, null, "Not a denial",
                action(ALLOW), List.of(), countryPredicate("root", null, 1)))),
            head("附加动作顺序不连续", code -> template(code).rules(withAdditional(cooldown(2, "60000")))),
            head("附加动作版本不支持", code -> template(code).rules(withAdditional(new AdditionalAction(1, COOLDOWN, 2,
                CLIENT_TARGET, cooldown(1, "60000").getParameters(), CLIENT_VARIABLE)))),
            head("附加动作主体绑定未知", code -> template(code).rules(withAdditional(new AdditionalAction(1, COOLDOWN, 1,
                "CURRENT_PARTNER_V1", cooldown(1, "60000").getParameters(), CLIENT_VARIABLE)))),
            head("冷却时长为 0", code -> template(code).rules(withAdditional(cooldown(1, "0")))),
            head("冷却时长超过 365 天", code -> template(code).rules(withAdditional(cooldown(1, "31536000001")))),
            head("标签格式非法", code -> template(code).rules(withAdditional(tag(1, "vip")))),
            head("附加动作参数编码与动作不符", code -> template(code).rules(withAdditional(new AdditionalAction(1, COOLDOWN, 1,
                CLIENT_TARGET, List.of(new ActionParameter("TAG", 1, "LITERAL", null, "DURATION", "60000")),
                CLIENT_VARIABLE)))));
    }

    /** 模板规则上修改一个字段。 */
    private static RuleRequestBody withRule(Consumer<RuleRequestBody> change) {
        RuleRequestBody rule = countryAllow();
        change.accept(rule);
        return rule;
    }

    /** 模板规则根节点上修改一个字段。 */
    private static RuleRequestBody withNode(Consumer<ConditionNodeRequestBody> change) {
        ConditionNodeRequestBody node = countryPredicate("root", null, 1);
        change.accept(node);
        return withRule(rule -> rule.setConditionNodes(List.of(node)));
    }

    /** 模板规则带一个附加动作。 */
    private static RuleRequestBody withAdditional(AdditionalAction additional) {
        return withRule(rule -> rule.setAdditionalActions(List.of(additional)));
    }

    private static ConditionNodeRequestBody countryPredicate(String nodeKey, String parentNodeKey, int sequenceNo) {
        return predicate(nodeKey, parentNodeKey, sequenceNo, "EQ", variable("LEFT", 1, "INPUT_COUNTRY_CODE", "STRING"),
            literal("RIGHT", 1, "STRING", "AE"));
    }

    private static Arguments invalid(String field, String scenario,
        Function<String, RuleSetCreateRequestBuilder> request) {
        return Arguments.of(field, scenario, request);
    }

    private static Arguments head(String scenario, Function<String, RuleSetCreateRequestBuilder> request) {
        return Arguments.of(scenario, request);
    }

    private static Arguments raw(String scenario, BiFunction<ObjectMapper, String, String> body) {
        return Arguments.of(scenario, body);
    }

    /** 带一个冷却附加动作的合法模板 JSON，经修改后输出。 */
    private static String mutate(ObjectMapper mapper, String code, Consumer<ObjectNode> change) {
        ObjectNode root = mapper.valueToTree(template(code).rules(withAdditional(cooldown(1, "60000"))).build());
        change.accept(root);
        return root.toString();
    }

    private static ObjectNode ruleNode(ObjectNode root) {
        return (ObjectNode)root.get("rules").get(0);
    }

    private static ObjectNode nodeNode(ObjectNode root) {
        return (ObjectNode)ruleNode(root).get("conditionNodes").get(0);
    }

    private static ObjectNode operand(ObjectNode root, int index) {
        return (ObjectNode)((ArrayNode)nodeNode(root).get("operands")).get(index);
    }

    private static ObjectNode additional(ObjectNode root) {
        return (ObjectNode)ruleNode(root).get("additionalActions").get(0);
    }

    private static String json(ObjectMapper mapper, CreateRuleSetVersionRequestBody body) {
        return mapper.writeValueAsString(body);
    }

    private String json(CreateRuleSetVersionRequestBody body) {
        return json(objectMapper, body);
    }

    private RuleSetCommandResponseChecker invoke(String body) {
        return invoke(body, MediaType.APPLICATION_JSON);
    }

    private RuleSetCommandResponseChecker invoke(String body, MediaType contentType) {
        String requestId = "risk-rule-set-create-input-" + UUID.randomUUID();
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Gateway-Actor-Subject", "70001");
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        headers.setContentType(contentType);
        Instant before = Instant.now();
        var result = http.post(PATH, headers, body.getBytes(StandardCharsets.UTF_8));
        return new RuleSetCommandResponseChecker(result, objectMapper, requestId, before, Instant.now());
    }
}
