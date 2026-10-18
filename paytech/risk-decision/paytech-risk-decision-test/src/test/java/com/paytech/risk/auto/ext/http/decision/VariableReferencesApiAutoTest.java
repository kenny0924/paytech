package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.risk.decision.api.enums.RiskVariableErrorCode.VARIABLE_NOT_FOUND;
import static com.paytech.risk.decision.domain.enums.NumericUnit.MONEY;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.DRAFT;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.FROZEN;
import static com.paytech.risk.test.builder.decision.RuleSetBuilder.versions;
import static com.paytech.risk.test.checker.decision.VariableReferencesResponseChecker.SCAN_ORDER;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.risk.decision.api.RiskVariableApi;
import com.paytech.risk.decision.domain.enums.FlowStatus;
import com.paytech.risk.decision.domain.enums.VariableStatus;
import com.paytech.risk.test.base.AbstractVariableApiTest;
import com.paytech.risk.test.builder.decision.VariableBuilder;
import com.paytech.risk.test.checker.decision.VariableReferencesResponseChecker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;
import com.paytech.risk.test.mocker.decision.VariableMocker;
import com.paytech.risk.test.mocker.decision.VariableMocker.MockedReference;
import com.paytech.risk.test.mocker.decision.VariableMocker.MockedVariable;

/**
 * 分段扫描变量的当前与历史引用位置
 * <li>正常流程：规则条件、规则币种、动作参数、变量依赖与流程条件连线按扫描阶段和行号返回准确位置，CURRENT/HISTORY 按当前指针及删除状态判定。</li>
 * <li>分页：scope 在扫描时过滤；size 控制每页引用数；游标续扫不重不漏；单页扫描预算 1000 行用尽时可返回空页且 hasMore=true。</li>
 * <li>未找到：编码不存在返回 404；墓碑变量与无引用变量返回空结果。</li>
 * <li>参数：scope、size、游标格式/身份绑定、未知/重复/空 Query、GET Body 与路径编码拒绝（400）。</li>
 * <li>数据损坏：引用父级断边或流程条件损坏时失败关闭（500），不视为无引用。</li>
 *
 * @see RiskVariableApi#getReferences(String, String, String, String, String, Integer)
 */
class VariableReferencesApiAutoTest extends AbstractVariableApiTest {

    @Autowired
    private RuleSetMocker ruleSets;

    /** 七类引用位置一次返回，按扫描阶段与行号升序，scannedRows 为实际扫描的候选行数。 */
    @Test
    void test_normalFlow() {
        MockedVariable target = variables.mock(VariableBuilder.currencyInput());
        List<MockedReference> all = mockAllKinds(target);

        read(target.code(), "").assertPage(sorted(all), false, 7);
        read(target.code(), "scope=ALL&size=8").assertPage(sorted(all), false, 7);
    }

    /** scope 在扫描时过滤，只计入命中范围的候选行。 */
    @Test
    void test_normalFlow_scope() {
        MockedVariable target = variables.mock(VariableBuilder.currencyInput());
        List<MockedReference> all = mockAllKinds(target);
        List<MockedReference> current = all.stream().filter(reference -> reference.scope().equals("CURRENT")).toList();
        List<MockedReference> history = all.stream().filter(reference -> reference.scope().equals("HISTORY")).toList();

        read(target.code(), "scope=CURRENT").assertPage(sorted(current), false, current.size());
        read(target.code(), "scope=HISTORY").assertPage(sorted(history), false, history.size());
    }

    /** 已删除规则集的当前版本、删除流程的当前版本均视为历史引用。 */
    @Test
    void test_normalFlow_deletedConsumersAreHistory() {
        MockedVariable target = variables.mock(VariableBuilder.constantText("v"));
        MockedRuleSet deleted = ruleSets.mock(versions(FROZEN, DRAFT).deleted());
        MockedReference rule = variables.ruleOperandReference(deleted, 2, true, target.code());
        MockedReference flow = variables.flowReference(target.code(), false, FlowStatus.DELETED);

        read(target.code(), "scope=HISTORY").assertPage(List.of(rule, flow), false, 2);
        read(target.code(), "scope=CURRENT").assertPage(List.of(), false, 0);
    }

    /** 游标续扫：每页最多 size 条，按扫描顺序不重不漏，末页无游标。 */
    @Test
    void test_normalFlow_cursorPaging() {
        MockedVariable target = variables.mock(VariableBuilder.currencyInput());
        List<MockedReference> all = sorted(mockAllKinds(target));

        VariableReferencesResponseChecker page = read(target.code(), "size=2").assertPage(all.subList(0, 2), true, 2);
        page = read(target.code(), "size=2&cursor=" + page.nextCursor()).assertPage(all.subList(2, 4), true, 2);
        page = read(target.code(), "size=2&cursor=" + page.nextCursor()).assertPage(all.subList(4, 6), true, 2);
        read(target.code(), "size=2&cursor=" + page.nextCursor()).assertPage(all.subList(6, 7), false, 1);
    }

    /** 恰好填满最后一页时仍返回游标，续扫得到无引用的末页。 */
    @Test
    void test_edgeCase_exactPageLeavesEmptyTail() {
        MockedVariable target = variables.mock(VariableBuilder.constantText("v"));
        MockedReference only = variables.ruleOperandReference(ruleSets.mock(versions(DRAFT)), 1, false, target.code());

        VariableReferencesResponseChecker page = read(target.code(), "size=1").assertPage(List.of(only), true, 1);
        read(target.code(), "size=1&cursor=" + page.nextCursor()).assertPage(List.of(), false, 0);
    }

    /** 单页扫描 1000 行仍未命中时返回空页且 hasMore=true，续扫找到其后的引用。 */
    @Test
    void test_edgeCase_scanBudgetExhausted() {
        MockedVariable target = variables.mock(VariableBuilder.constantText("v"));
        variables.flowEdgesWithoutReference(1000);
        MockedReference flow = variables.flowReference(target.code(), false, FlowStatus.CONFIGURABLE);

        VariableReferencesResponseChecker page = read(target.code(), "").assertPage(List.of(), true, 1000);
        read(target.code(), "cursor=" + page.nextCursor()).assertPage(List.of(flow), false, 1);
    }

    /** 不引用目标的条件连线计入扫描行数但不产生引用位置。 */
    @Test
    void test_edgeCase_unrelatedFlowEdgeScannedOnly() {
        MockedVariable target = variables.mock(VariableBuilder.constantText("v"));
        variables.flowEdgeWithoutReference();
        MockedReference flow = variables.flowReference(target.code(), true, FlowStatus.CONFIGURABLE);

        read(target.code(), "").assertPage(List.of(flow), false, 2);
    }

    /** 已删除的消费者变量不再构成变量依赖引用。 */
    @Test
    void test_edgeCase_deletedVariableConsumerIgnored() {
        MockedVariable target = variables.mock(VariableBuilder.textCollection().member("A", null));
        variables.mock(VariableBuilder.constantText("A").allowedValuesVariableCode(target.code())
            .status(VariableStatus.DELETED));

        read(target.code(), "").assertPage(List.of(), false, 0);
    }

    /** 变量存在但没有任何引用；墓碑变量同样可查询。 */
    @Test
    void test_noDataFound_noReferences() {
        MockedVariable active = variables.mock(VariableBuilder.constantText("v"));
        MockedVariable deleted = variables.mock(VariableBuilder.constantText("v").status(VariableStatus.DELETED));

        read(active.code(), "").assertPage(List.of(), false, 0);
        read(deleted.code(), "").assertPage(List.of(), false, 0);
    }

    /** 编码不存在。 */
    @Test
    void test_noDataFound() {
        read(variables.token() + "_ABSENT", "").assertProblem(404, VARIABLE_NOT_FOUND.getCode());
    }

    /** 引用父级断边时失败关闭，不把损坏引用当作无引用。 */
    @Test
    void test_businessRuleViolation_orphanReferenceFailsClosed() {
        MockedVariable target = variables.mock(VariableBuilder.constantText("v"));
        variables.orphanOperandReference(target.code());

        read(target.code(), "").assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 任一条件连线的持久化条件损坏时失败关闭。 */
    @Test
    void test_businessRuleViolation_corruptedFlowConditionFailsClosed() {
        MockedVariable target = variables.mock(VariableBuilder.constantText("v"));
        variables.corruptedFlowEdge();

        read(target.code(), "").assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 查询串非法时在扫描前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidQueries")
    void test_invalidParameter(String scenario, String query) {
        MockedVariable target = variables.mock(VariableBuilder.constantText("v"));

        read(target.code(), query).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 游标绑定变量编码与 scope，换用其他变量或范围、或篡改上界均拒绝。 */
    @Test
    void test_invalidParameter_cursorBinding() {
        MockedVariable target = variables.mock(VariableBuilder.constantText("v"));
        MockedVariable other = variables.mock(VariableBuilder.constantText("v"));
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT));
        variables.ruleOperandReference(ruleSet, 1, false, target.code());
        variables.ruleOperandReference(ruleSet, 2, false, target.code());
        String cursor = read(target.code(), "size=1").nextCursor();
        String json = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);

        read(other.code(), "size=1&cursor=" + cursor).assertProblem(400, INVALID_REQUEST.getCode());
        read(target.code(), "size=1&scope=CURRENT&cursor=" + cursor).assertProblem(400, INVALID_REQUEST.getCode());
        read(target.code(), "size=1&cursor=" + encode(json.replaceFirst("\"lastId\":\"\\d+\"",
            "\"lastId\":\"9223372036854775807\""))).assertProblem(400, INVALID_REQUEST.getCode());
        read(target.code(), "size=1&cursor=" + encode(json.replace("\"OPERAND\"", "\"UNKNOWN\"")))
            .assertProblem(400, INVALID_REQUEST.getCode());
        read(target.code(), "size=1&cursor=" + cursor + "AA").assertProblem(400, INVALID_REQUEST.getCode());
        read(target.code(), "size=1&cursor=" + encode(json)).assertStatus(200);
    }

    /** 携带 Body 的 GET 请求。 */
    @Test
    void test_invalidParameter_getWithBody() {
        MockedVariable target = variables.mock(VariableBuilder.constantText("v"));
        HttpHeaders headers = headers();

        new VariableReferencesResponseChecker(http.exchange(HttpMethod.GET, variablePath(target.code()) + "/references",
            headers, "{}"), objectMapper, headers.getFirst(ApiHeaderNames.REQUEST_ID), target.code())
            .assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 路径编码格式非法。 */
    @Test
    void test_invalidParameter_variableCode() {
        read("lower_code", "").assertProblem(400, INVALID_REQUEST.getCode());
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(Arguments.of("scope 小写", "scope=all"), Arguments.of("scope 未知", "scope=DELETED"),
            Arguments.of("size 为 0", "size=0"), Arguments.of("size 超过 100", "size=101"),
            Arguments.of("size 非数字", "size=ten"), Arguments.of("游标含非法字符", "cursor=abc.def"),
            Arguments.of("游标带填充", "cursor=e30="), Arguments.of("游标超过 2048 字符", "cursor=" + "A".repeat(2049)),
            Arguments.of("游标不是对象", "cursor=W10"), Arguments.of("游标为空对象", "cursor=e30"),
            Arguments.of("游标不是 Base64 JSON", "cursor=abcd"), Arguments.of("参数为空值", "scope="),
            Arguments.of("参数重复", "size=1&size=1"), Arguments.of("未知参数", "page=1"));
    }

    /**
     * 在同一目标上建立七类引用：
     * 当前/历史规则条件、规则币种、动作参数、变量币种依赖、当前/历史流程连线。
     */
    private List<MockedReference> mockAllKinds(MockedVariable target) {
        String code = target.code();
        MockedRuleSet first = ruleSets.mock(versions(FROZEN, DRAFT));
        MockedRuleSet second = ruleSets.mock(versions(FROZEN));
        List<MockedReference> all = new ArrayList<>();
        all.add(variables.ruleOperandReference(first, 2, false, code));
        all.add(variables.ruleOperandReference(first, 1, false, code));
        all.add(variables.ruleCurrencyReference(second, 1, code));
        all.add(variables.ruleParameterReference(first, 2, code));
        all.add(VariableMocker.variableReference(variables.mock(VariableBuilder.constantNumber("1.00000000")
            .numericUnit(MONEY).currencyVariableCode(code)), "currencyVariableCode"));
        all.add(variables.flowReference(code, false, FlowStatus.CONFIGURABLE));
        all.add(variables.flowReference(code, true, FlowStatus.CONFIGURABLE));
        return all;
    }

    private static List<MockedReference> sorted(List<MockedReference> references) {
        return references.stream().sorted(SCAN_ORDER).toList();
    }

    private static String encode(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private VariableReferencesResponseChecker read(String code, String query) {
        HttpHeaders headers = headers();
        String path = variablePath(code) + "/references" + (query.isEmpty() ? "" : "?" + query);
        return new VariableReferencesResponseChecker(http.get(path, headers), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID), code);
    }
}
