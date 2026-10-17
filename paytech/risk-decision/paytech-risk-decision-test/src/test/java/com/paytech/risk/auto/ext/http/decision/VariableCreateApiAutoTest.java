package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.CONCURRENT_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.risk.decision.api.enums.RiskVariableErrorCode.*;
import static com.paytech.risk.decision.domain.enums.DecisionValueType.*;
import static com.paytech.risk.decision.domain.enums.NumericUnit.MONEY;
import static com.paytech.risk.decision.domain.enums.NumericUnit.SCALAR;
import static com.paytech.risk.decision.domain.enums.VariableFormat.*;
import static com.paytech.risk.decision.domain.enums.VariableUsage.ACTION_PARAMETER;
import static com.paytech.risk.decision.domain.enums.VariableUsage.RULE_CONDITION;
import static com.paytech.risk.test.builder.decision.VariableBuilder.INPUT_CURRENCY;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.risk.decision.api.RiskVariableApi;
import com.paytech.risk.decision.api.request.CreateVariableRequestBody;
import com.paytech.risk.decision.domain.enums.VariableStatus;
import com.paytech.risk.test.base.AbstractVariableApiTest;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.builder.decision.VariableBuilder;
import com.paytech.risk.test.builder.decision.VariableRequestBuilder;
import com.paytech.risk.test.checker.decision.VariableDetailResponseChecker;
import com.paytech.risk.test.mocker.decision.VariableMocker.MockedVariable;

import tools.jackson.databind.node.ObjectNode;

/**
 * 创建运营维护的变量
 * <li>正常流程：常量（各类型与格式的规范化）、集合（初建为空）、输入别名及可选值关联创建成功（201），版本 1，审计主体为可信操作者。</li>
 * <li>业务规则：编码已存在或已删除（409）、节点输出只读（403）、依赖缺失（404）/未启用（409）/非集合（409）/不相容（400）、
 * 当前值不在可选集合（409）、资源锁被占用（409），均不写入。</li>
 * <li>参数：定义结构与值规范（400 INVALID_VARIABLE_DEFINITION）、枚举与重复用途（400）、Bean Validation（422）、
 * 严格 JSON（400）、16 KiB 上限（413）与媒体类型（415），均不写入。</li>
 *
 * @see RiskVariableApi#createVariable(String, String, CreateVariableRequestBody)
 */
class VariableCreateApiAutoTest extends AbstractVariableApiTest {

    /** 各来源、类型与格式的合法定义：响应与库中写入服务端规范化后的当前值。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("validDefinitions")
    void test_normalFlow(String scenario, Supplier<VariableBuilder> expected, Object requestValue) {
        String code = variables.newCode();
        VariableBuilder spec = expected.get();
        Instant from = now();

        VariableDetailResponseChecker response =
            create(VariableRequestBuilder.create(code, spec).currentValue(requestValue).build());

        assertCreated(response, code, spec, null, from);
    }

    /** 名称与说明去除首尾空白后保存。 */
    @Test
    void test_normalFlow_textStripped() {
        String code = variables.newCode();
        VariableBuilder spec = VariableBuilder.constantText("v").variableName("中文名称").description("说明");
        Instant from = now();

        VariableDetailResponseChecker response = create(VariableRequestBuilder.create(code, spec)
            .variableName("  中文名称　 ").description(" 说明  ").build());

        assertCreated(response, code, spec, null, from);
    }

    /** 关联启用的同类型集合作为可选值：当前值属于集合时创建成功，响应携带可选值预览。 */
    @Test
    void test_normalFlow_allowedValues() {
        MockedVariable options = variables.mock(VariableBuilder.collection(STRING, UPPER_ALPHA_2).version(3)
            .member("AE", "UAE").member("SA", null));
        String constantCode = variables.newCode();
        VariableBuilder constant = VariableBuilder.constant(STRING, UPPER_ALPHA_2, "SA")
            .allowedValuesVariableCode(options.code());
        String inputCode = variables.newCode();
        VariableBuilder input = VariableBuilder.countryInput().allowedValuesVariableCode(options.code());
        Instant from = now();

        assertCreated(create(VariableRequestBuilder.create(constantCode, constant).build()), constantCode, constant,
            options, from);
        assertCreated(create(VariableRequestBuilder.create(inputCode, input).build()), inputCode, input, options, from);
        variableData.assertUnchanged(options);
    }

    /** 请求体恰好 16 KiB 时仍可创建。 */
    @Test
    void test_edgeCase_bodyAtLimit() {
        String code = variables.newCode();
        VariableBuilder spec = VariableBuilder.constantText("v");
        Instant from = now();

        VariableDetailResponseChecker response = create(headers(), padded(VariableRequestBuilder.create(code, spec).build(),
            16384));

        assertCreated(response, code, spec, null, from);
    }

    /** 编码已存在（含墓碑）时以数据库唯一约束拒绝，原定义不变。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("existingStatuses")
    void test_businessRuleViolation_codeExists(VariableStatus status) {
        MockedVariable existing = variables.mock(VariableBuilder.constantText("old").status(status));

        create(VariableRequestBuilder.create(existing.code(), VariableBuilder.constantNumber("1")).build())
            .assertProblem(409, VARIABLE_CODE_EXISTS.getCode());

        variableData.assertUnchanged(existing);
    }

    /** 节点输出只能由工程合同登记。 */
    @Test
    void test_businessRuleViolation_nodeOutputReadOnly() {
        String code = variables.newCode();

        create(VariableRequestBuilder.create(code, VariableBuilder.nodeMatched()).build())
            .assertProblem(403, SYSTEM_VARIABLE_READ_ONLY.getCode());

        variableData.assertAbsent(code);
    }

    /** 依赖变量不存在。 */
    @Test
    void test_businessRuleViolation_dependencyNotFound() {
        String code = variables.newCode();
        VariableBuilder spec = VariableBuilder.constantNumber("1").numericUnit(MONEY)
            .currencyVariableCode(variables.token() + "_ABSENT");

        create(VariableRequestBuilder.create(code, spec).build()).assertProblem(404, VARIABLE_NOT_FOUND.getCode());

        variableData.assertAbsent(code);
    }

    /** 依赖变量停用或已删除时禁止新保存，依赖本身不变。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("inactiveStatuses")
    void test_businessRuleViolation_dependencyInactive(VariableStatus status) {
        MockedVariable options = variables.mock(VariableBuilder.textCollection().status(status).member("A", null));
        String code = variables.newCode();

        create(VariableRequestBuilder.create(code, VariableBuilder.constantText("A")
            .allowedValuesVariableCode(options.code())).build()).assertProblem(409, VARIABLE_DISABLED.getCode());

        variableData.assertAbsent(code);
        variableData.assertUnchanged(options);
    }

    /** 可选值来源不是集合。 */
    @Test
    void test_businessRuleViolation_allowedValuesNotCollection() {
        MockedVariable constant = variables.mock(VariableBuilder.constantText("A"));
        String code = variables.newCode();

        create(VariableRequestBuilder.create(code, VariableBuilder.constantText("A")
            .allowedValuesVariableCode(constant.code())).build()).assertProblem(409, VARIABLE_TYPE_MISMATCH.getCode());

        variableData.assertAbsent(code);
    }

    /** 当前值不属于关联的可选集合。 */
    @Test
    void test_businessRuleViolation_valueOutsideAllowedValues() {
        MockedVariable options = variables.mock(VariableBuilder.collection(STRING, UPPER_ALPHA_2).member("AE", null));
        String code = variables.newCode();

        create(VariableRequestBuilder.create(code, VariableBuilder.constant(STRING, UPPER_ALPHA_2, "SA")
            .allowedValuesVariableCode(options.code())).build()).assertProblem(409, VARIABLE_DOMAIN_MISMATCH.getCode());

        variableData.assertAbsent(code);
    }

    /** 依赖结构与本变量不相容：类型、适用范围或币种来源不符。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("incompatibleDependencies")
    void test_businessRuleViolation_incompatibleDependency(String scenario, Supplier<VariableBuilder> dependency,
        Function<String, VariableBuilder> consumer) {
        MockedVariable target = variables.mock(dependency.get());
        String code = variables.newCode();

        create(VariableRequestBuilder.create(code, consumer.apply(target.code())).build()).assertProblem(400,
            INVALID_VARIABLE_DEFINITION.getCode());

        variableData.assertAbsent(code);
    }

    /** 同一变量编码的资源锁被其他写入者持有时返回并发冲突。 */
    @Test
    void test_businessRuleViolation_resourceLocked() {
        String code = variables.newCode();
        variables.holdLock(code);

        create(VariableRequestBuilder.create(code, VariableBuilder.constantText("v")).build()).assertProblem(409,
            CONCURRENT_REQUEST.getCode());

        variableData.assertAbsent(code);
    }

    /** 定义结构或值不符合变量合同。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDefinitions")
    void test_invalidParameter_definition(String scenario, Function<String, VariableRequestBuilder> request) {
        String code = variables.newCode();

        create(request.apply(code).build()).assertProblem(400, INVALID_VARIABLE_DEFINITION.getCode());

        variableData.assertAbsent(code);
    }

    /** 枚举代码未知、大小写不符或用途重复。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCodes")
    void test_invalidParameter_codeValues(String scenario, UnaryOperator<VariableRequestBuilder> change) {
        String code = variables.newCode();

        create(change.apply(VariableRequestBuilder.create(code, VariableBuilder.constantText("v"))).build())
            .assertProblem(400, INVALID_REQUEST.getCode());

        variableData.assertAbsent(code);
    }

    /** 字段约束由入口 Bean Validation 拒绝并定位字段。 */
    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("invalidFields")
    void test_invalidParameter_fields(String field, String scenario, UnaryOperator<VariableRequestBuilder> change) {
        String code = variables.newCode();

        create(change.apply(VariableRequestBuilder.create(code, VariableBuilder.constantText("v"))).build())
            .assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError(field);

        variableData.assertAbsent(code);
    }

    /** 不符合严格 JSON 合同的报文统一返回 400。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedBodies")
    void test_invalidParameter_malformedBody(String scenario, Function<ObjectNode, String> raw) {
        String code = variables.newCode();
        ObjectNode valid = objectMapper.valueToTree(VariableRequestBuilder.create(code, VariableBuilder.constantText("v"))
            .build());

        create(headers(), raw.apply(valid)).assertProblem(400, INVALID_REQUEST.getCode());

        variableData.assertAbsent(code);
    }

    /** 请求体超过 16 KiB。 */
    @Test
    void test_invalidParameter_bodyTooLarge() {
        String code = variables.newCode();

        create(headers(), padded(VariableRequestBuilder.create(code, VariableBuilder.constantText("v")).build(), 16385))
            .assertProblem(413, INVALID_REQUEST.getCode());

        variableData.assertAbsent(code);
    }

    /** 非 JSON 请求在进入合同前以 415 拒绝。 */
    @Test
    void test_invalidParameter_unsupportedMediaType() {
        String code = variables.newCode();
        HttpHeaders headers = headers();
        headers.setContentType(MediaType.TEXT_PLAIN);

        create(headers, objectMapper.writeValueAsString(VariableRequestBuilder.create(code, VariableBuilder.constantText("v"))
            .build())).assertProblem(415, UNSUPPORTED_MEDIA_TYPE.getCode());

        variableData.assertAbsent(code);
    }

    static Stream<Arguments> validDefinitions() {
        return Stream.of(valid("STRING TEXT 保留首尾空格与大小写", () -> VariableBuilder.constantText(" Keep Case "), " Keep Case "),
            valid("STRING TOKEN", () -> VariableBuilder.constant(STRING, TOKEN, "HIGH_RISK"), "HIGH_RISK"),
            valid("STRING UPPER_ALPHA_2", () -> VariableBuilder.constant(STRING, UPPER_ALPHA_2, "AE"), "AE"),
            valid("STRING UPPER_ALPHA_3", () -> VariableBuilder.constant(STRING, UPPER_ALPHA_3, "USD"), "USD"),
            valid("STRING DIGITS_4 保留前导零", () -> VariableBuilder.constant(STRING, DIGITS_4, "0042"), "0042"),
            valid("STRING POSITIVE_ID 上限", () -> VariableBuilder.constant(STRING, POSITIVE_ID, "9223372036854775807"),
                "9223372036854775807"),
            valid("STRING BCP47 规范化大小写", () -> VariableBuilder.constant(STRING, BCP47, "en-US"), "en-us"),
            valid("STRING 512 字符", () -> VariableBuilder.constantText("界".repeat(512)), "界".repeat(512)),
            valid("NUMBER 补齐八位小数", () -> VariableBuilder.constantNumber("8000.00000000"), "8000"),
            valid("NUMBER 负数最小精度", () -> VariableBuilder.constantNumber("-0.00000001"), "-0.00000001"),
            valid("NUMBER 最大位数", () -> VariableBuilder.constantNumber("9999999999999999.99999999"),
                "9999999999999999.99999999"),
            valid("NUMBER MONEY 固定币种",
                () -> VariableBuilder.constantNumber("10.50000000").numericUnit(MONEY).currencyCode("USD"), "10.5"),
            valid("NUMBER MONEY 币种变量",
                () -> VariableBuilder.constantNumber("10.00000000").numericUnit(MONEY).currencyVariableCode(INPUT_CURRENCY),
                "10"),
            valid("BOOLEAN", () -> VariableBuilder.constant(BOOLEAN, NONE, Boolean.TRUE), Boolean.TRUE),
            valid("DATETIME", () -> VariableBuilder.constant(DATETIME, NONE, "2026-03-04T05:06:07.000001Z"),
                "2026-03-04T05:06:07.000001Z"),
            valid("DURATION 零", () -> VariableBuilder.constant(DURATION, NONE, "0"), "0"),
            valid("全局常量且无说明", () -> VariableBuilder.constantText("g").pointCode(null).description(null), "g"),
            valid("单一用途", () -> VariableBuilder.constantText("r").usages(RULE_CONDITION), "r"),
            valid("停用状态创建", () -> VariableBuilder.constantText("d").status(VariableStatus.DISABLED), "d"),
            valid("字符串集合初建为空", VariableBuilder::textCollection, null),
            valid("全局数值集合", () -> VariableBuilder.collection(NUMBER, NONE).numericUnit(SCALAR).pointCode(null), null),
            valid("国家输入别名", VariableBuilder::countryInput, null),
            valid("金额输入别名绑定同合同币种",
                () -> VariableBuilder.input("merchandise_amount", NUMBER, NONE).numericUnit(MONEY)
                    .currencyVariableCode(INPUT_CURRENCY).usages(RULE_CONDITION), null),
            valid("动作主体输入", () -> VariableBuilder.input("client_id", STRING, POSITIVE_ID).usages(ACTION_PARAMETER), null));
    }

    static Stream<VariableStatus> existingStatuses() {
        return Stream.of(VariableStatus.ACTIVE, VariableStatus.DELETED);
    }

    static Stream<VariableStatus> inactiveStatuses() {
        return Stream.of(VariableStatus.DISABLED, VariableStatus.DELETED);
    }

    static Stream<Arguments> incompatibleDependencies() {
        return Stream.of(
            Arguments.of("可选值集合类型不同", (Supplier<VariableBuilder>) () -> VariableBuilder.collection(NUMBER, NONE)
                .numericUnit(SCALAR), consumer(target -> VariableBuilder.constantText("1").allowedValuesVariableCode(target))),
            Arguments.of("可选值集合范围不覆盖全局变量", (Supplier<VariableBuilder>) () -> VariableBuilder.textCollection()
                .member("A", null), consumer(target -> VariableBuilder.constantText("A").pointCode(null)
                    .allowedValuesVariableCode(target))),
            Arguments.of("币种变量不是币种输入", (Supplier<VariableBuilder>) VariableBuilder::countryInput,
                consumer(target -> VariableBuilder.constantNumber("1").numericUnit(MONEY).currencyVariableCode(target))),
            Arguments.of("币种变量为常量", (Supplier<VariableBuilder>) () -> VariableBuilder.constant(STRING, UPPER_ALPHA_3, "USD"),
                consumer(target -> VariableBuilder.constantNumber("1").numericUnit(MONEY).currencyVariableCode(target))),
            Arguments.of("全局金额不能绑定决策点币种", (Supplier<VariableBuilder>) VariableBuilder::currencyInput,
                consumer(target -> VariableBuilder.constantNumber("1").pointCode(null).numericUnit(MONEY)
                    .currencyVariableCode(target))));
    }

    static Stream<Arguments> invalidDefinitions() {
        return Stream.of(definition("STRING 使用 NONE 格式", code -> text(code).formatCode("NONE")),
            definition("NUMBER 使用 TEXT 格式", code -> number(code).formatCode("TEXT")),
            definition("NUMBER 缺少量纲", code -> number(code).numericUnit(null)),
            definition("普通数值填写币种", code -> number(code).currencyCode("USD")),
            definition("金额缺少币种来源", code -> number(code).numericUnit("MONEY")),
            definition("金额同时填写两种币种来源",
                code -> number(code).numericUnit("MONEY").currencyCode("USD").currencyVariableCode(INPUT_CURRENCY)),
            definition("金额固定币种小写", code -> number(code).numericUnit("MONEY").currencyCode("usd")),
            definition("金额币种变量编码非法", code -> number(code).numericUnit("MONEY").currencyVariableCode("input_currency")),
            definition("非 NUMBER 填写量纲", code -> text(code).numericUnit("SCALAR")),
            definition("非 NUMBER 填写币种", code -> text(code).currencyCode("USD")),
            definition("常量填写来源合同", code -> text(code).sourceContractCode("ORDER_PREPARATION")),
            definition("常量填写来源版本", code -> text(code).sourceContractVersion(4)),
            definition("常量填写来源字段", code -> text(code).sourceField("country_code")),
            definition("常量用于动作主体", code -> text(code).usages(List.of("ACTION_PARAMETER", "RULE_CONDITION"))),
            definition("集合关联可选值集合", code -> collection(code).allowedValuesVariableCode(INPUT_CURRENCY)),
            definition("常量缺少当前值", code -> text(code).currentValue(null)),
            definition("集合携带当前值", code -> collection(code).currentValue("A")),
            definition("输入携带当前值", code -> input(code).currentValue("AE")),
            definition("值不符合两位大写格式", code -> request(code, VariableBuilder.constant(STRING, UPPER_ALPHA_2, "ae"))),
            definition("TOKEN 含小写", code -> request(code, VariableBuilder.constant(STRING, TOKEN, "High"))),
            definition("DIGITS_4 位数不足", code -> request(code, VariableBuilder.constant(STRING, DIGITS_4, "042"))),
            definition("POSITIVE_ID 前导零", code -> request(code, VariableBuilder.constant(STRING, POSITIVE_ID, "0123"))),
            definition("POSITIVE_ID 溢出", code -> request(code, VariableBuilder.constant(STRING, POSITIVE_ID,
                "9223372036854775808"))),
            definition("BCP47 无效标签", code -> request(code, VariableBuilder.constant(STRING, BCP47, "e"))),
            definition("字符串为空", code -> text(code).currentValue("")),
            definition("字符串超过 512 字符", code -> text(code).currentValue("a".repeat(513))),
            definition("字符串含控制字符", code -> text(code).currentValue("a\u0001b")),
            definition("BOOLEAN 使用字符串", code -> request(code, VariableBuilder.constant(BOOLEAN, NONE, "true"))),
            definition("NUMBER 使用 Boolean", code -> number(code).currentValue(Boolean.TRUE)),
            definition("NUMBER 科学计数法", code -> number(code).currentValue("1E3")),
            definition("NUMBER 前导零", code -> number(code).currentValue("01")),
            definition("NUMBER 整数 17 位", code -> number(code).currentValue("1" + "0".repeat(16))),
            definition("NUMBER 小数 9 位", code -> number(code).currentValue("1.000000001")),
            definition("DATETIME 缺少微秒", code -> request(code, VariableBuilder.constant(DATETIME, NONE,
                "2026-03-04T05:06:07Z"))),
            definition("DATETIME 日期无效", code -> request(code, VariableBuilder.constant(DATETIME, NONE,
                "2026-02-30T00:00:00.000000Z"))),
            definition("DATETIME 早于 1000 年", code -> request(code, VariableBuilder.constant(DATETIME, NONE,
                "0999-12-31T23:59:59.999999Z"))),
            definition("DURATION 负数", code -> request(code, VariableBuilder.constant(DURATION, NONE, "-1"))),
            definition("DURATION 溢出", code -> request(code, VariableBuilder.constant(DURATION, NONE,
                "9223372036854775808"))),
            definition("输入合同版本不存在", code -> input(code).sourceContractVersion(3)),
            definition("旧输入合同只读", code -> request(code, VariableBuilder.input("country_code", STRING, UPPER_ALPHA_2))
                .sourceContractVersion(RuleSetBuilder.LEGACY_CONTRACT_VERSION)),
            definition("输入字段不在合同中", code -> input(code).sourceField("card_bin")),
            definition("输入字段类型不符", code -> input(code).valueType("NUMBER").formatCode("NONE").numericUnit("SCALAR")),
            definition("输入用途超出合同", code -> input(code).usages(List.of("ACTION_PARAMETER"))),
            definition("输入量纲不符", code -> request(code, VariableBuilder.input("merchandise_amount", NUMBER, NONE)
                .numericUnit(SCALAR))),
            definition("输入金额使用固定币种",
                code -> request(code, VariableBuilder.input("merchandise_amount", NUMBER, NONE).numericUnit(MONEY)
                    .currencyCode("USD"))),
            definition("输入缺少决策点", code -> input(code).pointCode(null)),
            definition("决策点未支持", code -> text(code).pointCode("PAYOUT_CREATION")),
            definition("名称超过 255 字符", code -> text(code).variableName("名".repeat(256))),
            definition("名称含控制字符", code -> text(code).variableName("a\tb")),
            definition("说明超过 500 字符", code -> text(code).description("说".repeat(501))),
            definition("说明为空白", code -> text(code).description("   ")),
            definition("创建为墓碑状态", code -> text(code).status("DELETED")));
    }

    static Stream<Arguments> invalidCodes() {
        return Stream.of(code("来源未知", request -> request.sourceType("FEATURE")),
            code("来源小写", request -> request.sourceType("constant")),
            code("类型未知", request -> request.valueType("INTEGER")),
            code("格式小写", request -> request.formatCode("text")),
            code("量纲未知", request -> request.numericUnit("PERCENT")),
            code("状态未知", request -> request.status("ENABLED")),
            code("用途未知", request -> request.usages(List.of("ANY"))),
            code("用途重复", request -> request.usages(List.of("RULE_CONDITION", "RULE_CONDITION"))));
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(field("variableCode", "缺失", request -> request.variableCode(null)),
            field("variableCode", "空串", request -> request.variableCode("")),
            field("variableCode", "小写", request -> request.variableCode("mock_lower")),
            field("variableCode", "数字开头", request -> request.variableCode("1MOCK")),
            field("variableCode", "超过 64 字符", request -> request.variableCode("M".repeat(65))),
            field("variableName", "缺失", request -> request.variableName(null)),
            field("variableName", "空白", request -> request.variableName("   ")),
            field("sourceType", "缺失", request -> request.sourceType(null)),
            field("valueType", "缺失", request -> request.valueType(null)),
            field("usages", "缺失", request -> request.usages(null)),
            field("usages", "空数组", request -> request.usages(List.of())),
            field("usages", "超过 3 项",
                request -> request.usages(List.of("RULE_CONDITION", "FLOW_CONDITION", "ACTION_PARAMETER", "RULE_CONDITION"))),
            field("usages[0]", "元素空白", request -> request.usages(List.of(" "))),
            field("formatCode", "缺失", request -> request.formatCode(null)),
            field("status", "缺失", request -> request.status(null)),
            field("sourceContractVersion", "为 0", request -> request.sourceContractVersion(0)),
            field("sourceContractVersion", "负数", request -> request.sourceContractVersion(-1)));
    }

    static Stream<Arguments> malformedBodies() {
        return Stream.of(raw("未知字段", body -> body.put("extra", "x").toString()),
            raw("缺少字段键", body -> {
                body.remove("description");
                return body.toString();
            }), raw("重复字段键", body -> body.toString().replaceFirst("\\{", "{\"variableName\":\"dup\",")),
            raw("currentValue 为数字", body -> body.put("currentValue", 1).toString()),
            raw("currentValue 为对象", body -> {
                body.putObject("currentValue");
                return body.toString();
            }), raw("currentValue 为数组", body -> {
                body.putArray("currentValue").add("v");
                return body.toString();
            }), raw("sourceContractVersion 为字符串", body -> body.put("sourceContractVersion", "4").toString()),
            raw("sourceContractVersion 为小数", body -> body.put("sourceContractVersion", 4.5).toString()),
            raw("usages 不是数组", body -> body.put("usages", "RULE_CONDITION").toString()),
            raw("usages 元素为数字", body -> {
                body.putArray("usages").add(1);
                return body.toString();
            }), raw("名称为数字", body -> body.put("variableName", 123).toString()),
            raw("状态为 Boolean", body -> body.put("status", true).toString()),
            raw("根为数组", body -> "[" + body + "]"), raw("尾随内容", body -> body + "{}"),
            raw("非法 JSON", body -> body.toString().substring(1)), raw("JSON null", body -> "null"));
    }

    /** 创建成功：响应、库中根与零成员一致，时间为本次命令时间。 */
    private void assertCreated(VariableDetailResponseChecker response, String code, VariableBuilder spec,
        MockedVariable options, Instant from) {
        VariableBuilder expected = spec.created(ACTOR_ID);
        Instant time = variableData.assertStored(code, expected);
        variableData.assertMembers(code, expected, time);
        response.assertCreated(code, expected, options).assertWrittenAt(time, true).assertValuesAsOfBetween(from);
    }

    /** 以空白补齐到指定 UTF-8 字节数的合法 JSON。 */
    private String padded(CreateVariableRequestBody body, int bytes) {
        String json = objectMapper.writeValueAsString(body);
        return json + " ".repeat(bytes - json.getBytes(StandardCharsets.UTF_8).length);
    }

    private VariableDetailResponseChecker create(CreateVariableRequestBody body) {
        HttpHeaders headers = headers();
        return new VariableDetailResponseChecker(http.post(VARIABLES, headers, body), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }

    private VariableDetailResponseChecker create(HttpHeaders headers, String raw) {
        return new VariableDetailResponseChecker(http.post(VARIABLES, headers, raw), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }

    private static Arguments valid(String scenario, Supplier<VariableBuilder> expected, Object requestValue) {
        return Arguments.of(scenario, expected, requestValue);
    }

    private static Function<String, VariableBuilder> consumer(Function<String, VariableBuilder> consumer) {
        return consumer;
    }

    private static Arguments definition(String scenario, Function<String, VariableRequestBuilder> request) {
        return Arguments.of(scenario, request);
    }

    private static Arguments code(String scenario, UnaryOperator<VariableRequestBuilder> change) {
        return Arguments.of(scenario, change);
    }

    private static Arguments field(String field, String scenario, UnaryOperator<VariableRequestBuilder> change) {
        return Arguments.of(field, scenario, change);
    }

    private static Arguments raw(String scenario, Function<ObjectNode, String> raw) {
        return Arguments.of(scenario, raw);
    }

    private static VariableRequestBuilder request(String code, VariableBuilder spec) {
        return VariableRequestBuilder.create(code, spec);
    }

    private static VariableRequestBuilder text(String code) {
        return request(code, VariableBuilder.constantText("v"));
    }

    private static VariableRequestBuilder number(String code) {
        return request(code, VariableBuilder.constantNumber("1"));
    }

    private static VariableRequestBuilder collection(String code) {
        return request(code, VariableBuilder.textCollection());
    }

    private static VariableRequestBuilder input(String code) {
        return request(code, VariableBuilder.countryInput());
    }
}
