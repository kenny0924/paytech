package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.CONCURRENT_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.risk.api.enums.RiskVariableErrorCode.*;
import static com.paytech.risk.decision.domain.enums.DecisionValueType.STRING;
import static com.paytech.risk.decision.domain.enums.NumericUnit.MONEY;
import static com.paytech.risk.decision.domain.enums.VariableFormat.UPPER_ALPHA_2;
import static com.paytech.risk.decision.domain.enums.VariableStatus.*;
import static com.paytech.risk.test.builder.decision.VariableBuilder.INPUT_CURRENCY;
import static com.paytech.risk.test.builder.decision.VariableBuilder.UPDATE_TIME;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.risk.api.decision.RiskVariableApi;
import com.paytech.risk.api.decision.request.UpdateVariableRequestBody;
import com.paytech.risk.decision.domain.enums.VariableStatus;
import com.paytech.risk.test.base.AbstractVariableApiTest;
import com.paytech.risk.test.builder.decision.VariableBuilder;
import com.paytech.risk.test.builder.decision.VariableRequestBuilder;
import com.paytech.risk.test.checker.decision.VariableDetailResponseChecker;
import com.paytech.risk.test.mocker.decision.VariableMocker.MockedVariable;

import tools.jackson.databind.node.ObjectNode;

/**
 * 按期望根版本更新变量的名称、说明、常量当前值与状态
 * <li>正常流程：常量值规范化、停用/启用、清空说明、集合与输入只改展示字段、系统定义只改名称说明、可选值内改值；版本加一、更新主体为操作者，成员不变。</li>
 * <li>业务规则：根版本不符（409）、墓碑/不存在（404）、系统定义改状态或值（403）、值不在可选集合（409）、依赖已停用（409）、
 * 资源锁被占用（409）、版本已达上限（500），均不改变原定义。</li>
 * <li>参数：可变字段规范（400 INVALID_VARIABLE_DEFINITION）、状态代码与路径/Query（400）、Bean Validation（422）、严格 JSON（400）、
 * 16 KiB 上限（413）与媒体类型（415），均不改变原定义。</li>
 *
 * @see RiskVariableApi#updateVariable(String, String, String, UpdateVariableRequestBody)
 */
class VariableUpdateApiAutoTest extends AbstractVariableApiTest {

    /** 常量更新名称、说明、值与状态：值按类型规范化，版本加一，审计更新主体为操作者。 */
    @Test
    void test_normalFlow_constant() {
        MockedVariable constant = variables.mock(VariableBuilder.constantNumber("1.00000000").numericUnit(MONEY)
            .currencyVariableCode(INPUT_CURRENCY).version(5));
        VariableBuilder expected = constant.spec().updated(ACTOR_ID).variableName("新名称").description("新说明")
            .currentValue("9000.00000000").status(DISABLED);

        Instant from = now();


        assertUpdated(constant, expected, null, from,
            update(constant.code(), VariableRequestBuilder.update("5", " 新名称 ", "新说明", "9000", "DISABLED")));
    }

    /** 停用定义可重新启用，说明可清空。 */
    @Test
    void test_normalFlow_reenableAndClearDescription() {
        MockedVariable constant = variables.mock(VariableBuilder.constantText("keep").status(DISABLED));
        VariableBuilder expected = constant.spec().updated(ACTOR_ID).description(null).status(ACTIVE);

        Instant from = now();


        assertUpdated(constant, expected, null, from, update(constant.code(),
            VariableRequestBuilder.update("1", "Mock variable", null, "keep", "ACTIVE")));
    }

    /** 集合只改展示字段：响应携带当前成员数与前三项预览，成员行不变。 */
    @Test
    void test_normalFlow_collection() {
        MockedVariable collection = variables.mock(VariableBuilder.textCollection().member("Z", "zed").member("A", null)
            .member("M", "em").member("B", "bee"));
        VariableBuilder expected = collection.spec().updated(ACTOR_ID).variableName("Renamed").status(DISABLED);

        Instant from = now();


        assertUpdated(collection, expected, null, from,
            update(collection.code(), VariableRequestBuilder.update("1", "Renamed", "Mock description", null, "DISABLED")));
    }

    /** 系统定义只允许修改名称和说明，状态与值保持原事实。 */
    @Test
    void test_normalFlow_systemDefinitionRename() {
        MockedVariable system = variables.mock(VariableBuilder.countryInput().systemDefinition());
        VariableBuilder expected = system.spec().updated(ACTOR_ID).variableName("系统国家").description(null);

        Instant from = now();


        assertUpdated(system, expected, null, from,
            update(system.code(), VariableRequestBuilder.update("1", "系统国家", null, null, "ACTIVE")));
    }

    /** 关联可选值的常量改为集合内的另一个值，响应携带可选值预览，依赖集合不变。 */
    @Test
    void test_normalFlow_allowedValues() {
        MockedVariable options = variables.mock(VariableBuilder.collection(STRING, UPPER_ALPHA_2).member("AE", null)
            .member("SA", "Saudi"));
        MockedVariable constant = variables.mock(VariableBuilder.constant(STRING, UPPER_ALPHA_2, "AE")
            .allowedValuesVariableCode(options.code()));
        VariableBuilder expected = constant.spec().updated(ACTOR_ID).currentValue("SA");

        Instant from = now();


        assertUpdated(constant, expected, options, from, update(constant.code(),
            VariableRequestBuilder.update("1", "Mock variable", "Mock description", "SA", "ACTIVE")));
        variableData.assertUnchanged(options);
    }

    /** 期望根版本与当前版本不一致时拒绝，不自动以最新版本重试。 */
    @ParameterizedTest(name = "expectedVersion={0}")
    @ValueSource(strings = {"2", "4"})
    void test_businessRuleViolation_versionConflict(String expectedVersion) {
        MockedVariable constant = variables.mock(VariableBuilder.constantText("v").version(3));

        update(constant.code(), VariableRequestBuilder.update(expectedVersion, "x", null, "w", "ACTIVE"))
            .assertProblem(409, VARIABLE_VERSION_CONFLICT.getCode());

        variableData.assertUnchanged(constant);
    }

    /** 墓碑不可再写。 */
    @Test
    void test_businessRuleViolation_deleted() {
        MockedVariable deleted = variables.mock(VariableBuilder.constantText("v").status(DELETED));

        update(deleted.code(), VariableRequestBuilder.update("1", "x", null, "v", "ACTIVE")).assertNotFound();

        variableData.assertUnchanged(deleted);
    }

    /** 编码不存在。 */
    @Test
    void test_noDataFound() {
        String code = variables.newCode();

        update(code, VariableRequestBuilder.update("1", "x", null, "v", "ACTIVE")).assertNotFound();

        variableData.assertAbsent(code);
    }

    /** 系统定义的状态与值不开放运营修改。 */
    @Test
    void test_businessRuleViolation_systemDefinitionReadOnly() {
        MockedVariable input = variables.mock(VariableBuilder.countryInput().systemDefinition());
        MockedVariable constant = variables.mock(VariableBuilder.constantText("fixed").systemDefinition());

        update(input.code(), VariableRequestBuilder.update("1", "x", null, null, "DISABLED")).assertProblem(403,
            SYSTEM_VARIABLE_READ_ONLY.getCode());
        update(constant.code(), VariableRequestBuilder.update("1", "x", null, "changed", "ACTIVE")).assertProblem(403,
            SYSTEM_VARIABLE_READ_ONLY.getCode());

        variableData.assertUnchanged(input);
        variableData.assertUnchanged(constant);
    }

    /** 新值不属于关联的可选集合。 */
    @Test
    void test_businessRuleViolation_valueOutsideAllowedValues() {
        MockedVariable options = variables.mock(VariableBuilder.collection(STRING, UPPER_ALPHA_2).member("AE", null));
        MockedVariable constant = variables.mock(VariableBuilder.constant(STRING, UPPER_ALPHA_2, "AE")
            .allowedValuesVariableCode(options.code()));

        update(constant.code(), VariableRequestBuilder.update("1", "x", null, "SA", "ACTIVE")).assertProblem(409,
            VARIABLE_DOMAIN_MISMATCH.getCode());

        variableData.assertUnchanged(constant);
    }

    /** 依赖集合已停用时禁止保存，即使只修改名称。 */
    @Test
    void test_businessRuleViolation_dependencyDisabled() {
        MockedVariable options = variables.mock(VariableBuilder.textCollection().status(DISABLED).member("A", null));
        MockedVariable constant = variables.mock(VariableBuilder.constantText("A").allowedValuesVariableCode(options.code()));

        update(constant.code(), VariableRequestBuilder.update("1", "renamed", null, "A", "ACTIVE")).assertProblem(409,
            VARIABLE_DISABLED.getCode());

        variableData.assertUnchanged(constant);
    }

    /** 同一变量的资源锁被其他写入者持有时返回并发冲突。 */
    @Test
    void test_businessRuleViolation_resourceLocked() {
        MockedVariable constant = variables.mock(VariableBuilder.constantText("v"));
        variables.holdLock(constant.code());

        update(constant.code(), VariableRequestBuilder.update("1", "x", null, "w", "ACTIVE")).assertProblem(409,
            CONCURRENT_REQUEST.getCode());

        variableData.assertUnchanged(constant);
    }

    /** 根版本已达 BIGINT 上限时失败关闭，不溢出。 */
    @Test
    void test_edgeCase_versionAtMaximum() {
        MockedVariable constant = variables.mock(VariableBuilder.constantText("v").version(Long.MAX_VALUE));

        update(constant.code(), VariableRequestBuilder.update(Long.toString(Long.MAX_VALUE), "x", null, "w", "ACTIVE"))
            .assertProblem(500, INTERNAL_ERROR.getCode());

        variableData.assertUnchanged(constant);
    }

    /** 可变字段不符合变量合同。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidChanges")
    void test_invalidParameter_change(String scenario, VariableBuilder spec, UpdateVariableRequestBody request) {
        MockedVariable variable = variables.mock(spec);

        update(variable.code(), request).assertProblem(400, INVALID_VARIABLE_DEFINITION.getCode());

        variableData.assertUnchanged(variable);
    }

    /** 状态代码、版本数值或路径编码在进入 Owner 前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    void test_invalidParameter_request(String scenario, String pathSuffix, UpdateVariableRequestBody request) {
        MockedVariable constant = variables.mock(VariableBuilder.constantText("v"));

        update(headers(), variablePath(constant.code()) + pathSuffix, request).assertProblem(400,
            INVALID_REQUEST.getCode());

        variableData.assertUnchanged(constant);
    }

    /** 路径编码格式非法。 */
    @Test
    void test_invalidParameter_variableCode() {
        update("lower_code", VariableRequestBuilder.update("1", "x", null, "v", "ACTIVE")).assertProblem(400,
            INVALID_REQUEST.getCode());
    }

    /** 字段约束由入口 Bean Validation 拒绝并定位字段。 */
    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("invalidFields")
    void test_invalidParameter_fields(String field, String scenario, UpdateVariableRequestBody request) {
        MockedVariable constant = variables.mock(VariableBuilder.constantText("v"));

        update(constant.code(), request).assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError(field);

        variableData.assertUnchanged(constant);
    }

    /** 不符合严格 JSON 合同的报文统一返回 400。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedBodies")
    void test_invalidParameter_malformedBody(String scenario, Function<ObjectNode, String> raw) {
        MockedVariable constant = variables.mock(VariableBuilder.constantText("v"));
        ObjectNode valid = objectMapper.valueToTree(VariableRequestBuilder.update("1", "x", null, "w", "ACTIVE"));

        update(headers(), variablePath(constant.code()), raw.apply(valid)).assertProblem(400, INVALID_REQUEST.getCode());

        variableData.assertUnchanged(constant);
    }

    /** 请求体超过 16 KiB；恰好 16 KiB 仍可更新。 */
    @Test
    void test_edgeCase_bodySizeLimit() {
        MockedVariable constant = variables.mock(VariableBuilder.constantText("v"));
        String json = objectMapper.writeValueAsString(VariableRequestBuilder.update("1", "x", null, "w", "ACTIVE"));
        int length = json.getBytes(StandardCharsets.UTF_8).length;

        update(headers(), variablePath(constant.code()), json + " ".repeat(16385 - length)).assertProblem(413,
            INVALID_REQUEST.getCode());
        variableData.assertUnchanged(constant);

        update(headers(), variablePath(constant.code()), json + " ".repeat(16384 - length)).assertStatus(200);
        variableData.assertStored(constant.code(), constant.spec().updated(ACTOR_ID).variableName("x").description(null)
            .currentValue("w"));
    }

    /** 非 JSON 请求在进入合同前以 415 拒绝。 */
    @Test
    void test_invalidParameter_unsupportedMediaType() {
        MockedVariable constant = variables.mock(VariableBuilder.constantText("v"));
        HttpHeaders headers = headers();
        headers.setContentType(MediaType.TEXT_PLAIN);

        update(headers, variablePath(constant.code()),
            objectMapper.writeValueAsString(VariableRequestBuilder.update("1", "x", null, "w", "ACTIVE")))
            .assertProblem(415, UNSUPPORTED_MEDIA_TYPE.getCode());

        variableData.assertUnchanged(constant);
    }

    static Stream<Arguments> invalidChanges() {
        return Stream.of(
            change("更新为墓碑状态", VariableBuilder.constantText("v"), VariableRequestBuilder.update("1", "x", null, "v", "DELETED")),
            change("常量清空当前值", VariableBuilder.constantText("v"), VariableRequestBuilder.update("1", "x", null, null, "ACTIVE")),
            change("集合写入当前值", VariableBuilder.textCollection(), VariableRequestBuilder.update("1", "x", null, "A", "ACTIVE")),
            change("输入写入当前值", VariableBuilder.countryInput(), VariableRequestBuilder.update("1", "x", null, "AE", "ACTIVE")),
            change("值不符合格式", VariableBuilder.constant(STRING, UPPER_ALPHA_2, "AE"),
                VariableRequestBuilder.update("1", "x", null, "ae", "ACTIVE")),
            change("数值使用 Boolean", VariableBuilder.constantNumber("1.00000000"),
                VariableRequestBuilder.update("1", "x", null, Boolean.TRUE, "ACTIVE")),
            change("名称超过 255 字符", VariableBuilder.constantText("v"),
                VariableRequestBuilder.update("1", "名".repeat(256), null, "v", "ACTIVE")),
            change("名称含控制字符", VariableBuilder.constantText("v"), VariableRequestBuilder.update("1", "a\u0007", null, "v",
                "ACTIVE")),
            change("说明超过 500 字符", VariableBuilder.constantText("v"),
                VariableRequestBuilder.update("1", "x", "说".repeat(501), "v", "ACTIVE")),
            change("说明为空白", VariableBuilder.constantText("v"), VariableRequestBuilder.update("1", "x", " ", "v", "ACTIVE")));
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(Arguments.of("状态小写", "", VariableRequestBuilder.update("1", "x", null, "v", "active")),
            Arguments.of("状态未知", "", VariableRequestBuilder.update("1", "x", null, "v", "ENABLED")),
            Arguments.of("版本超过 BIGINT", "", VariableRequestBuilder.update("9223372036854775808", "x", null, "v", "ACTIVE")),
            Arguments.of("携带 Query 参数", "?expectedVersion=1", VariableRequestBuilder.update("1", "x", null, "v", "ACTIVE")));
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(Arguments.of("expectedVersion", "缺失", VariableRequestBuilder.update(null, "x", null, "v", "ACTIVE")),
            Arguments.of("expectedVersion", "为 0", VariableRequestBuilder.update("0", "x", null, "v", "ACTIVE")),
            Arguments.of("expectedVersion", "前导零", VariableRequestBuilder.update("01", "x", null, "v", "ACTIVE")),
            Arguments.of("expectedVersion", "非数字", VariableRequestBuilder.update("one", "x", null, "v", "ACTIVE")),
            Arguments.of("expectedVersion", "20 位", VariableRequestBuilder.update("1".repeat(20), "x", null, "v", "ACTIVE")),
            Arguments.of("variableName", "缺失", VariableRequestBuilder.update("1", null, null, "v", "ACTIVE")),
            Arguments.of("variableName", "空白", VariableRequestBuilder.update("1", " ", null, "v", "ACTIVE")),
            Arguments.of("status", "缺失", VariableRequestBuilder.update("1", "x", null, "v", null)));
    }

    static Stream<Arguments> malformedBodies() {
        return Stream.of(Arguments.of("未知字段（结构字段不可修改）",
            (Function<ObjectNode, String>) body -> body.put("valueType", "NUMBER").toString()),
            Arguments.of("缺少字段键", (Function<ObjectNode, String>) body -> {
                body.remove("currentValue");
                return body.toString();
            }), Arguments.of("重复字段键",
                (Function<ObjectNode, String>) body -> body.toString().replaceFirst("\\{", "{\"status\":\"ACTIVE\",")),
            Arguments.of("expectedVersion 为数字", (Function<ObjectNode, String>) body -> body.put("expectedVersion", 1)
                .toString()),
            Arguments.of("currentValue 为数字", (Function<ObjectNode, String>) body -> body.put("currentValue", 9000)
                .toString()),
            Arguments.of("根为数组", (Function<ObjectNode, String>) body -> "[" + body + "]"));
    }

    /** 更新成功：响应与库中根一致，成员保持夹具原样，时间为本次命令时间。 */
    private void assertUpdated(MockedVariable variable, VariableBuilder expected, MockedVariable options, Instant from,
        VariableDetailResponseChecker response) {
        Instant time = variableData.assertStored(variable.code(), expected);
        variableData.assertMembers(variable.code(), expected, UPDATE_TIME);
        response.assertDetail(variable.code(), expected, options).assertWrittenAt(time, false).assertValuesAsOfBetween(from);
    }

    private static Arguments change(String scenario, VariableBuilder spec, UpdateVariableRequestBody request) {
        return Arguments.of(scenario, spec, request);
    }

    private VariableDetailResponseChecker update(String code, UpdateVariableRequestBody body) {
        return update(headers(), variablePath(code), body);
    }

    private VariableDetailResponseChecker update(HttpHeaders headers, String path, Object body) {
        return new VariableDetailResponseChecker(http.exchange(HttpMethod.PUT, path, headers, body), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }
}
