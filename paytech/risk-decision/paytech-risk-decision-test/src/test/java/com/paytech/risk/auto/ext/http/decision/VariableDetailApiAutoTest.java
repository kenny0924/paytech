package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.risk.decision.domain.enums.DecisionValueType.*;
import static com.paytech.risk.decision.domain.enums.VariableFormat.*;
import static com.paytech.risk.test.builder.decision.VariableBuilder.INPUT_CURRENCY;

import java.time.Instant;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.risk.decision.api.RiskVariableApi;
import com.paytech.risk.decision.domain.enums.NumericUnit;
import com.paytech.risk.decision.domain.enums.VariableStatus;
import com.paytech.risk.decision.domain.enums.VariableUsage;
import com.paytech.risk.test.base.AbstractVariableApiTest;
import com.paytech.risk.test.builder.decision.VariableBuilder;
import com.paytech.risk.test.checker.decision.VariableDetailResponseChecker;
import com.paytech.risk.test.mocker.decision.VariableMocker.MockedVariable;

/**
 * 按永久编码读取变量详情
 * <li>正常流程：各类型常量、集合（成员计数与前三项预览）、可选值集合预览、输入别名与系统定义逐字段返回；停用与墓碑仍可读取。</li>
 * <li>未找到：编码不存在返回 404 VARIABLE_NOT_FOUND。</li>
 * <li>参数：路径编码格式、任意 Query 与 GET Body 在读取前拒绝（400）。</li>
 * <li>数据损坏：类型槽位、未规范化值、来源合同、元数据及成员规范键损坏时失败关闭（500），不降级为空值。</li>
 *
 * @see RiskVariableApi#getVariable(String, String, String)
 */
class VariableDetailApiAutoTest extends AbstractVariableApiTest {

    /** 各类型常量的定义、规范当前值与审计字段逐字段返回，读取时刻为本次请求时间。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("constants")
    void test_normalFlow_constant(String scenario, Supplier<VariableBuilder> spec) {
        MockedVariable constant = variables.mock(spec.get());
        Instant from = now();

        read(constant.code()).assertDetail(constant.code(), constant.spec(), null).assertValuesAsOfBetween(from);
    }

    /** 集合返回全部成员数及按顺序的前三项预览；成员值按集合类型输出。 */
    @Test
    void test_normalFlow_collectionPreview() {
        MockedVariable collection = variables.mock(VariableBuilder.collection(NUMBER, NONE).numericUnit(NumericUnit.SCALAR)
            .member("3.00000000", "three").member("1.50000000", null).member("-2.00000000", "minus two")
            .member("0.00000001", "tiny").member("100.00000000", "hundred"));

        read(collection.code()).assertDetail(collection.code(), collection.spec(), null);
    }

    /** 关联可选值集合的常量返回可选值集合的编码、名称、版本、成员数及预览。 */
    @Test
    void test_normalFlow_allowedValues() {
        MockedVariable options = variables.mock(VariableBuilder.collection(STRING, UPPER_ALPHA_2).version(4)
            .variableName("Allowed countries").member("AE", "UAE").member("SA", "Saudi").member("EG", null)
            .member("QA", "Qatar"));
        MockedVariable constant = variables.mock(VariableBuilder.constant(STRING, UPPER_ALPHA_2, "SA")
            .allowedValuesVariableCode(options.code()));

        read(constant.code()).assertDetail(constant.code(), constant.spec(), options);
    }

    /** 可选值预览最多一百项，成员数仍为全部数量。 */
    @Test
    void test_edgeCase_allowedValuesPreviewCappedAt100() {
        VariableBuilder spec = VariableBuilder.collection(DURATION, NONE);
        for (int index = 0; index < 101; index++) {
            spec.member(Integer.toString(index * 1000), null);
        }
        MockedVariable options = variables.mock(spec);
        MockedVariable constant = variables.mock(VariableBuilder.constant(DURATION, NONE, "5000")
            .allowedValuesVariableCode(options.code()));

        read(constant.code()).assertDetail(constant.code(), constant.spec(), options);
    }

    /** 运营登记的输入别名与系统定义返回准确来源合同与用途。 */
    @Test
    void test_normalFlow_inputAlias() {
        MockedVariable operator = variables.mock(VariableBuilder.countryInput().description(null));
        MockedVariable system = variables.mock(VariableBuilder.countryInput().systemDefinition()
            .usages(VariableUsage.FLOW_CONDITION));

        read(operator.code()).assertDetail(operator.code(), operator.spec(), null);
        read(system.code()).assertDetail(system.code(), system.spec(), null);
    }

    /** 停用与墓碑定义仍可读取，状态与版本按库中事实返回。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("inactiveStatuses")
    void test_normalFlow_inactiveDefinitionsReadable(VariableStatus status) {
        MockedVariable variable = variables.mock(VariableBuilder.constantText("kept").status(status).version(7));

        read(variable.code()).assertDetail(variable.code(), variable.spec(), null);
    }

    /** 墓碑变量的可选值集合随后被删除时仍可读取，可选值按该集合墓碑的编码、名称、版本、成员数及预览返回。 */
    @Test
    void test_edgeCase_tombstoneWithDeletedAllowedValues() {
        MockedVariable options = variables.mock(VariableBuilder.collection(STRING, UPPER_ALPHA_2).status(VariableStatus.DELETED)
            .version(5).variableName("Retired countries").member("AE", "UAE").member("SA", null));
        MockedVariable constant = variables.mock(VariableBuilder.constant(STRING, UPPER_ALPHA_2, "AE")
            .allowedValuesVariableCode(options.code()).status(VariableStatus.DELETED).version(3));

        read(constant.code()).assertDetail(constant.code(), constant.spec(), options);
    }

    /** 编码不存在。 */
    @Test
    void test_noDataFound() {
        read(variables.token() + "_ABSENT").assertNotFound();
    }

    /** 路径编码不是规范大写标识。 */
    @ParameterizedTest(name = "variableCode={0}")
    @ValueSource(strings = {"lower_case", "1ABC", "_ABC", "A-B", "A%20B"})
    void test_invalidParameter_variableCode(String code) {
        read(code).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 路径编码超过 64 个字符。 */
    @Test
    void test_invalidParameter_variableCodeTooLong() {
        read("A".repeat(65)).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 详情不接受任何 Query 参数。 */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"status=ACTIVE", "pointCode=CHECKOUT_PREPARE", "expectedVersion=1"})
    void test_invalidParameter_query(String query) {
        MockedVariable variable = variables.mock(VariableBuilder.constantText("value"));

        invoke(headers(), VARIABLES + "/" + variable.code() + "?" + query).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 携带 Body 的 GET 请求。 */
    @Test
    void test_invalidParameter_getWithBody() {
        MockedVariable variable = variables.mock(VariableBuilder.constantText("value"));
        HttpHeaders headers = headers();

        new VariableDetailResponseChecker(http.exchange(HttpMethod.GET, variablePath(variable.code()), headers, "{}"),
            objectMapper, headers.getFirst(ApiHeaderNames.REQUEST_ID)).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 持久化内容损坏时失败关闭，不把损坏值降级为空或默认值。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("corruptions")
    void test_businessRuleViolation_corruptedDefinitionFailsClosed(String scenario, Supplier<VariableBuilder> spec,
        String column, Object value) {
        MockedVariable variable = variables.mock(spec.get());
        variables.corrupt(variable, column, value);

        read(variable.code()).assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 集合成员规范键与值不一致时失败关闭。 */
    @Test
    void test_businessRuleViolation_corruptedMemberFailsClosed() {
        MockedVariable collection = variables.mock(VariableBuilder.textCollection().member("A", null));
        variables.corruptMemberKeys(collection);

        read(collection.code()).assertProblem(500, INTERNAL_ERROR.getCode());
    }

    static Stream<Arguments> constants() {
        return Stream.of(constant("STRING TEXT 保留大小写与空格", () -> VariableBuilder.constantText("Exact Text 001 ")),
            constant("STRING TOKEN", () -> VariableBuilder.constant(STRING, TOKEN, "HIGH_RISK")),
            constant("STRING BCP47", () -> VariableBuilder.constant(STRING, BCP47, "en-US")),
            constant("STRING 全局适用且无说明", () -> VariableBuilder.constantText("global").pointCode(null).description(null)),
            constant("NUMBER SCALAR", () -> VariableBuilder.constantNumber("-8000.50000000")),
            constant("NUMBER MONEY 固定币种",
                () -> VariableBuilder.constantNumber("100.00000000").numericUnit(NumericUnit.MONEY).currencyCode("USD")),
            constant("NUMBER MONEY 币种变量", () -> VariableBuilder.constantNumber("9999999999999999.99999999")
                .numericUnit(NumericUnit.MONEY).currencyVariableCode(INPUT_CURRENCY)),
            constant("BOOLEAN", () -> VariableBuilder.constant(BOOLEAN, NONE, Boolean.FALSE).usages(VariableUsage.FLOW_CONDITION)),
            constant("DATETIME", () -> VariableBuilder.constant(DATETIME, NONE, "2026-03-04T05:06:07.000001Z")),
            constant("DURATION", () -> VariableBuilder.constant(DURATION, NONE, "9223372036854775807")));
    }

    static Stream<VariableStatus> inactiveStatuses() {
        return Stream.of(VariableStatus.DISABLED, VariableStatus.DELETED);
    }

    static Stream<Arguments> corruptions() {
        return Stream.of(
            corruption("常量缺少值槽位", () -> VariableBuilder.constantNumber("1.00000000"), "number_value", null),
            corruption("常量值写入错误槽位", () -> VariableBuilder.constantText("value"), "boolean_value", 1),
            corruption("常量值未规范化", () -> VariableBuilder.constant(STRING, UPPER_ALPHA_2, "AE"), "string_value", "ae"),
            corruption("输入别名来源字段不在合同中", VariableBuilder::countryInput, "source_field", "unknown_field"),
            corruption("格式与类型不相容", () -> VariableBuilder.constantText("value"), "format_code", "NONE"),
            corruption("根版本为 0", () -> VariableBuilder.constantText("value"), "version", 0),
            corruption("创建者为 0", () -> VariableBuilder.constantText("value"), "create_by", 0),
            corruption("集合携带常量值", VariableBuilder::textCollection, "string_value", "A"));
    }

    private static Arguments constant(String scenario, Supplier<VariableBuilder> spec) {
        return Arguments.of(scenario, spec);
    }

    private static Arguments corruption(String scenario, Supplier<VariableBuilder> spec, String column, Object value) {
        return Arguments.of(scenario, spec, column, value);
    }

    private VariableDetailResponseChecker read(String code) {
        return invoke(headers(), variablePath(code));
    }

    private VariableDetailResponseChecker invoke(HttpHeaders headers, String uri) {
        return new VariableDetailResponseChecker(http.get(uri, headers), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }
}
