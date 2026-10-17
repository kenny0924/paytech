package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.CONCURRENT_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.risk.api.enums.RiskVariableErrorCode.*;
import static com.paytech.risk.decision.domain.enums.NumericUnit.MONEY;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.DRAFT;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.FROZEN;
import static com.paytech.risk.decision.domain.enums.VariableStatus.*;
import static com.paytech.risk.test.builder.decision.RuleSetBuilder.versions;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.risk.api.decision.RiskVariableApi;
import com.paytech.risk.decision.domain.enums.FlowStatus;
import com.paytech.risk.test.base.AbstractVariableApiTest;
import com.paytech.risk.test.builder.decision.VariableBuilder;
import com.paytech.risk.test.checker.decision.VariableDeleteResponseChecker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;
import com.paytech.risk.test.mocker.decision.VariableMocker;
import com.paytech.risk.test.mocker.decision.VariableMocker.MockedVariable;

/**
 * 在无任何当前或历史引用时逻辑删除变量
 * <li>正常流程：启用、停用的常量与集合删除为墓碑，版本加一、更新主体为操作者，值与成员保留；已删除的消费者和无关连线不阻止删除。</li>
 * <li>业务规则：规则条件（当前/历史/已删除规则集）、规则币种、动作参数、变量依赖、流程连线（当前/历史/已删除流程）任一引用均阻止删除（409）；
 * 根版本不符（409）、墓碑/不存在（404）、系统定义（403）、资源锁被占用（409）、引用关系损坏（500），均不改变原定义。</li>
 * <li>参数：expectedVersion 缺失/格式/重复/空值、未知 Query、DELETE Body 与路径编码拒绝（400）。</li>
 *
 * @see RiskVariableApi#deleteVariable(String, String, String, String)
 */
class VariableDeleteApiAutoTest extends AbstractVariableApiTest {

    @Autowired
    private RuleSetMocker ruleSets;

    /** 建立一类对目标变量的引用。 */
    @FunctionalInterface
    interface ReferenceKind {
        void mock(VariableMocker variables, RuleSetMocker ruleSets, String code);
    }

    /** 无引用的变量删除为墓碑：状态 DELETED、版本加一，名称、说明、值与成员保留。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("deletable")
    void test_normalFlow(String scenario, Supplier<VariableBuilder> spec) {
        MockedVariable variable = variables.mock(spec.get().version(4));
        Instant from = now();

        delete(variable.code(), "4").assertDeleted(variable.code(), 5);

        assertTombstone(variable, from);
    }

    /** 已删除的消费者变量与不引用目标的条件连线不阻止删除。 */
    @Test
    void test_normalFlow_ignoredConsumers() {
        MockedVariable target = variables.mock(VariableBuilder.textCollection().member("A", null));
        variables.mock(VariableBuilder.constantText("A").allowedValuesVariableCode(target.code()).status(DELETED));
        variables.flowEdgeWithoutReference();
        Instant from = now();

        delete(target.code(), "1").assertDeleted(target.code(), 2);

        assertTombstone(target, from);
    }

    /** 任一当前或历史引用都阻止删除，原定义不变。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("references")
    void test_businessRuleViolation_inUse(String scenario, Supplier<VariableBuilder> spec, ReferenceKind reference) {
        MockedVariable target = variables.mock(spec.get());
        reference.mock(variables, ruleSets, target.code());

        delete(target.code(), "1").assertProblem(409, VARIABLE_IN_USE.getCode());

        variableData.assertUnchanged(target);
    }

    /** 期望根版本与当前版本不一致。 */
    @ParameterizedTest(name = "expectedVersion={0}")
    @ValueSource(strings = {"1", "3"})
    void test_businessRuleViolation_versionConflict(String expectedVersion) {
        MockedVariable variable = variables.mock(VariableBuilder.constantText("v").version(2));

        delete(variable.code(), expectedVersion).assertProblem(409, VARIABLE_VERSION_CONFLICT.getCode());

        variableData.assertUnchanged(variable);
    }

    /** 已删除的墓碑不能再次删除。 */
    @Test
    void test_businessRuleViolation_alreadyDeleted() {
        MockedVariable deleted = variables.mock(VariableBuilder.constantText("v").status(DELETED));

        delete(deleted.code(), "1").assertNotFound();

        variableData.assertUnchanged(deleted);
    }

    /** 编码不存在。 */
    @Test
    void test_noDataFound() {
        delete(variables.token() + "_ABSENT", "1").assertNotFound();
    }

    /** 系统定义不能删除。 */
    @Test
    void test_businessRuleViolation_systemDefinition() {
        MockedVariable system = variables.mock(VariableBuilder.countryInput().systemDefinition());

        delete(system.code(), "1").assertProblem(403, SYSTEM_VARIABLE_READ_ONLY.getCode());

        variableData.assertUnchanged(system);
    }

    /** 同一变量的资源锁被其他写入者持有时返回并发冲突。 */
    @Test
    void test_businessRuleViolation_resourceLocked() {
        MockedVariable variable = variables.mock(VariableBuilder.constantText("v"));
        variables.holdLock(variable.code());

        delete(variable.code(), "1").assertProblem(409, CONCURRENT_REQUEST.getCode());

        variableData.assertUnchanged(variable);
    }

    /** 引用关系损坏时失败关闭，不当作无引用而删除。 */
    @Test
    void test_businessRuleViolation_corruptedReferenceFailsClosed() {
        MockedVariable variable = variables.mock(VariableBuilder.constantText("v"));
        variables.orphanOperandReference(variable.code());

        delete(variable.code(), "1").assertProblem(500, INTERNAL_ERROR.getCode());

        variableData.assertUnchanged(variable);
    }

    /** 查询串非法时在删除前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidQueries")
    void test_invalidParameter(String scenario, String query) {
        MockedVariable variable = variables.mock(VariableBuilder.constantText("v"));

        invoke(HttpMethod.DELETE, variablePath(variable.code()) + query, null).assertProblem(400, INVALID_REQUEST.getCode());

        variableData.assertUnchanged(variable);
    }

    /** 携带 Body 的 DELETE 请求。 */
    @Test
    void test_invalidParameter_deleteWithBody() {
        MockedVariable variable = variables.mock(VariableBuilder.constantText("v"));

        invoke(HttpMethod.DELETE, variablePath(variable.code()) + "?expectedVersion=1", "{}").assertProblem(400,
            INVALID_REQUEST.getCode());

        variableData.assertUnchanged(variable);
    }

    /** 路径编码格式非法。 */
    @Test
    void test_invalidParameter_variableCode() {
        delete("lower_code", "1").assertProblem(400, INVALID_REQUEST.getCode());
    }

    static Stream<Arguments> deletable() {
        return Stream.of(Arguments.of("启用常量", (Supplier<VariableBuilder>) () -> VariableBuilder.constantText("kept")),
            Arguments.of("停用数值常量", (Supplier<VariableBuilder>) () -> VariableBuilder.constantNumber("5.00000000")
                .status(DISABLED)),
            Arguments.of("带成员的集合", (Supplier<VariableBuilder>) () -> VariableBuilder.textCollection().member("A", "a")
                .member("B", null)),
            Arguments.of("运营输入别名", (Supplier<VariableBuilder>) VariableBuilder::countryInput));
    }

    static Stream<Arguments> references() {
        Supplier<VariableBuilder> text = () -> VariableBuilder.constantText("v");
        Supplier<VariableBuilder> currency = VariableBuilder::currencyInput;
        return Stream.of(
            reference("当前规则集版本条件", text,
                (v, r, code) -> v.ruleOperandReference(r.mock(versions(FROZEN, DRAFT)), 2, false, code)),
            reference("历史规则集版本条件", text,
                (v, r, code) -> v.ruleOperandReference(r.mock(versions(FROZEN, DRAFT)), 1, false, code)),
            reference("已删除规则集", text,
                (v, r, code) -> v.ruleOperandReference(r.mock(versions(FROZEN, DRAFT).deleted()), 2, true, code)),
            reference("规则条件币种变量", currency, (v, r, code) -> v.ruleCurrencyReference(r.mock(versions(DRAFT)), 1, code)),
            reference("规则动作参数", text, (v, r, code) -> v.ruleParameterReference(r.mock(versions(FROZEN)), 1, code)),
            reference("变量可选值依赖", () -> VariableBuilder.textCollection().member("A", null),
                (v, r, code) -> v.mock(VariableBuilder.constantText("A").allowedValuesVariableCode(code))),
            reference("停用变量的币种依赖", currency, (v, r, code) -> v.mock(VariableBuilder.constantNumber("1.00000000")
                .numericUnit(MONEY).currencyVariableCode(code).status(DISABLED))),
            reference("当前流程连线", text, (v, r, code) -> v.flowReference(code, false, FlowStatus.CONFIGURABLE)),
            reference("历史流程连线", text, (v, r, code) -> v.flowReference(code, true, FlowStatus.CONFIGURABLE)),
            reference("已删除流程", text, (v, r, code) -> v.flowReference(code, false, FlowStatus.DELETED)));
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(Arguments.of("缺少 expectedVersion", ""), Arguments.of("expectedVersion 为 0", "?expectedVersion=0"),
            Arguments.of("expectedVersion 前导零", "?expectedVersion=01"),
            Arguments.of("expectedVersion 非数字", "?expectedVersion=one"),
            Arguments.of("expectedVersion 超过 BIGINT", "?expectedVersion=9223372036854775808"),
            Arguments.of("expectedVersion 为空值", "?expectedVersion="),
            Arguments.of("expectedVersion 重复", "?expectedVersion=1&expectedVersion=1"),
            Arguments.of("未知参数", "?expectedVersion=1&force=true"));
    }

    /** 墓碑：仅状态、版本、更新主体与更新时间变化，其余字段及成员保留。 */
    private void assertTombstone(MockedVariable variable, Instant from) {
        Instant time = variableData.assertStored(variable.code(), variable.spec().updated(ACTOR_ID).status(DELETED));
        variableData.assertMembers(variable.code(), variable.spec(), VariableBuilder.UPDATE_TIME);
        assertFalse(time.isBefore(from), "更新时间应为本次命令时间");
    }

    private static Arguments reference(String scenario, Supplier<VariableBuilder> spec, ReferenceKind kind) {
        return Arguments.of(scenario, spec, kind);
    }

    private VariableDeleteResponseChecker delete(String code, String expectedVersion) {
        return invoke(HttpMethod.DELETE, variablePath(code) + "?expectedVersion=" + expectedVersion, null);
    }

    private VariableDeleteResponseChecker invoke(HttpMethod method, String uri, Object body) {
        HttpHeaders headers = headers();
        return new VariableDeleteResponseChecker(http.exchange(method, uri, headers, body), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }
}
