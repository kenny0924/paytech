package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.risk.decision.domain.enums.DecisionEnvironment.PRODUCTION;
import static com.paytech.risk.decision.domain.enums.DecisionEnvironment.TEST;
import static com.paytech.risk.decision.domain.enums.DecisionReleaseStatus.ACTIVE;
import static com.paytech.risk.decision.domain.enums.DecisionReleaseStatus.SUPERSEDED;
import static com.paytech.risk.decision.domain.enums.DictionaryStatus.DISABLED;
import static com.paytech.risk.decision.domain.enums.DictionaryType.REJECTION_REASON;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.DRAFT;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.FROZEN;
import static com.paytech.risk.test.builder.decision.DictionaryEntryBuilder.reason;
import static com.paytech.risk.test.builder.decision.RuleSetBuilder.versions;
import static com.paytech.risk.test.checker.decision.RejectionReasonReferencesResponseChecker.flow;
import static com.paytech.risk.test.checker.decision.RejectionReasonReferencesResponseChecker.rule;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.risk.api.decision.DecisionDictionaryApi;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.FlowStatus;
import com.paytech.risk.test.base.AbstractDecisionDictionaryApiTest;
import com.paytech.risk.test.builder.decision.ReleaseBuilder;
import com.paytech.risk.test.checker.decision.RejectionReasonReferencesResponseChecker;
import com.paytech.risk.test.checker.decision.RejectionReasonReferencesResponseChecker.Expected;
import com.paytech.risk.test.mocker.decision.DictionaryMocker;
import com.paytech.risk.test.mocker.decision.DictionaryMocker.MockedDictionary;
import com.paytech.risk.test.mocker.decision.DictionaryReferenceMocker;
import com.paytech.risk.test.mocker.decision.DictionaryReferenceMocker.MockedFlow;
import com.paytech.risk.test.mocker.decision.DictionaryReferenceMocker.MockedFlowEnd;
import com.paytech.risk.test.mocker.decision.DictionaryReferenceMocker.MockedRule;
import com.paytech.risk.test.mocker.decision.DictionaryReferenceMocker.ReferencedRuleSet;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;

/**
 * 拒绝原因在规则与流程中的精确引用分页
 * <li>正常流程：DENY 终态规则上的原因与 DENY 结束节点上的原因统一分页，按范围 ALL/CURRENT/HISTORY/DELETED 筛选；规则来源附带精确版本的活动环境，停用原因仍可反查。</li>
 * <li>排除项：ALLOW 规则或 REVIEW 结束节点上的同码原因、其他原因编码不计入。</li>
 * <li>未找到：无引用返回空页；未登记编码返回 404。</li>
 * <li>失败关闭：原因记录损坏或当前页规则所在决策点的发布闭包断边时返回 500。</li>
 * <li>参数：范围、分页、路径编码、未知/重复/空 Query 与 GET Body 均在查询前拒绝（400）。</li>
 *
 * @see DecisionDictionaryApi#getRejectionReasonReferences(String, String, String, Integer, Integer, String)
 */
class RejectionReasonReferencesApiAutoTest extends AbstractDecisionDictionaryApiTest {

    @Autowired
    private DictionaryMocker dictionaries;

    @Autowired
    private DictionaryReferenceMocker references;

    @Autowired
    private RuleSetMocker ruleSets;

    /** 覆盖四种范围的规则与流程引用，以及不应计入的同码原因。 */
    private record ScopeMatrix(MockedRule historyRule, MockedRule currentRule, MockedRule deletedRule,
        MockedFlowEnd historyEnd, MockedFlowEnd currentEnd, MockedFlowEnd deletedEnd) {
    }

    /** 缺省范围为 ALL，返回全部规则与流程引用并按版本倒序统一分页；冻结版本附带去重排序的活动环境。 */
    @Test
    void test_normalFlow() {
        MockedDictionary reason = dictionaries.mock(reason());
        ScopeMatrix m = scopeMatrix(reason.code());

        Expected[] all = {rule(m.historyRule(), PRODUCTION, TEST), rule(m.currentRule()), rule(m.deletedRule()),
            flow(m.historyEnd()), flow(m.currentEnd()), flow(m.deletedEnd())};
        read(reason.code(), "").assertReferences(1, 10, all);
        read(reason.code(), "scope=ALL").assertReferences(1, 10, all);
    }

    /** CURRENT/HISTORY 只看可配置根的当前指针；DELETED 返回整体删除的规则集与流程。 */
    @Test
    void test_normalFlow_scopes() {
        MockedDictionary reason = dictionaries.mock(reason());
        ScopeMatrix m = scopeMatrix(reason.code());

        read(reason.code(), "scope=CURRENT").assertReferences(1, 10, rule(m.currentRule()), flow(m.currentEnd()));
        read(reason.code(), "scope=HISTORY").assertReferences(1, 10, rule(m.historyRule(), PRODUCTION, TEST),
            flow(m.historyEnd()));
        read(reason.code(), "scope=DELETED").assertReferences(1, 10, rule(m.deletedRule()), flow(m.deletedEnd()));
    }

    /** 同一版本内的多条引用按规则 ID 升序分页，越界页为空但保留总数。 */
    @Test
    void test_normalFlow_paging() {
        MockedDictionary reason = dictionaries.mock(reason());
        ReferencedRuleSet ruleSet = references.ruleSet(versions(FROZEN));
        Expected[] all = IntStream.range(0, 11).mapToObj(i -> {
            MockedRule rule = references.rule(ruleSet, 1, reason.code());
            references.deny(rule);
            return rule(rule);
        }).toArray(Expected[]::new);

        read(reason.code(), "").assertReferences(1, 10, all);
        read(reason.code(), "page=2&size=10").assertReferences(2, 10, all);
        read(reason.code(), "page=3").assertReferences(3, 10, all);
        read(reason.code(), "size=20&scope=CURRENT").assertReferences(1, 20, all);
    }

    /** 停用原因仍可反查已有引用。 */
    @Test
    void test_normalFlow_disabledReason() {
        MockedDictionary reason = dictionaries.mock(reason().status(DISABLED));
        MockedRule rule = references.rule(references.ruleSet(versions(DRAFT)), 1, reason.code());
        references.deny(rule);

        read(reason.code(), "").assertReferences(1, 10, rule(rule));
    }

    /** 已登记但没有任何引用的原因返回空页。 */
    @Test
    void test_noDataFound() {
        MockedDictionary reason = dictionaries.mock(reason());
        MockedRule allowRule = references.rule(references.ruleSet(versions(DRAFT)), 1, reason.code());
        references.allow(allowRule);

        read(reason.code(), "").assertReferences(1, 10);
    }

    /** 未登记的原因编码。 */
    @Test
    void test_noDataFound_unregistered() {
        read(dictionaries.newCode(REJECTION_REASON), "").assertNotFound();
    }

    /** 原因当前记录损坏时失败关闭。 */
    @Test
    void test_businessRuleViolation_corruptedReason() {
        MockedDictionary reason = dictionaries.mock(reason().defaultMessage(" "));

        read(reason.code(), "").assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 当前页规则所在决策点存在发布断边时失败关闭，不返回可能失真的活动环境。 */
    @Test
    void test_businessRuleViolation_brokenReleaseClosure() {
        MockedDictionary reason = dictionaries.mock(reason());
        references.deny(references.rule(references.ruleSet(versions(FROZEN)), 1, reason.code()));
        ruleSets.mockBrokenRelease();

        read(reason.code(), "").assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 当前页只有流程来源时不读取发布闭包，断边不影响流程引用。 */
    @Test
    void test_edgeCase_brokenReleaseWithFlowOnlyPage() {
        MockedDictionary reason = dictionaries.mock(reason());
        MockedFlowEnd end = references.end(references.flow(1, FlowStatus.CONFIGURABLE), 1, DecisionValue.DENY,
            reason.code());
        ruleSets.mockBrokenRelease();

        read(reason.code(), "").assertReferences(1, 10, flow(end));
    }

    /** 查询串非法时在查询前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidQueries")
    void test_invalidParameter(String scenario, String query) {
        MockedDictionary reason = dictionaries.mock(reason());

        read(reason.code(), query).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 路径编码格式非法。 */
    @ParameterizedTest(name = "reasonCode={0}")
    @MethodSource("invalidCodes")
    void test_invalidParameter_reasonCode(String code) {
        read(code, "").assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 携带 Body 的 GET 请求。 */
    @Test
    void test_invalidParameter_getWithBody() {
        MockedDictionary reason = dictionaries.mock(reason());

        exchange(HttpMethod.GET, REASONS + "/" + reason.code() + "/references", headers(ACTOR), "{}",
            RejectionReasonReferencesResponseChecker::new).assertProblem(400, INVALID_REQUEST.getCode());
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(Arguments.of("scope 小写", "scope=all"), Arguments.of("scope 未知", "scope=ACTIVE"),
            Arguments.of("scope 为空值", "scope="), Arguments.of("scope 重复", "scope=ALL&scope=ALL"),
            Arguments.of("page 为 0", "page=0"), Arguments.of("page 超过 100000", "page=100001"),
            Arguments.of("size 不在允许值内", "size=30"), Arguments.of("未知参数", "status=ACTIVE"));
    }

    static Stream<String> invalidCodes() {
        return Stream.of("mock", "9MOCK", "MOCK.REASON", "R".repeat(65));
    }

    /**
     * 构造引用矩阵
     * <li>可配置规则集 v1 冻结（历史，TEST/PRODUCTION 生效且另有已替代发布）、v2 草稿（当前）；删除规则集 v1 为删除前当前版本。</li>
     * <li>可配置流程 v1 历史、v2 当前；删除流程 v1。另插入 ALLOW 规则、REVIEW 结束节点上的同码原因及其他原因的 DENY 规则，均不应计入。</li>
     */
    private ScopeMatrix scopeMatrix(String reasonCode) {
        ReferencedRuleSet configurable = references.ruleSet(versions(FROZEN, DRAFT));
        MockedRule historyRule = references.rule(configurable, 1, reasonCode);
        references.deny(historyRule);
        MockedRule currentRule = references.rule(configurable, 2, reasonCode);
        references.deny(currentRule);
        ruleSets.mockRelease(configurable.ruleSet(), 1, ReleaseBuilder.of(PRODUCTION, ACTIVE));
        ruleSets.mockRelease(configurable.ruleSet(), 1, ReleaseBuilder.of(TEST, ACTIVE));
        ruleSets.mockRelease(configurable.ruleSet(), 1, ReleaseBuilder.of(TEST, SUPERSEDED));
        references.allow(references.rule(configurable, 2, reasonCode));
        references.deny(references.rule(configurable, 2, dictionaries.newCode(REJECTION_REASON)));

        ReferencedRuleSet deleted = references.ruleSet(versions(FROZEN).deleted());
        MockedRule deletedRule = references.rule(deleted, 1, reasonCode);
        references.deny(deletedRule);

        MockedFlow flow = references.flow(2, FlowStatus.CONFIGURABLE);
        MockedFlowEnd historyEnd = references.end(flow, 1, DecisionValue.DENY, reasonCode);
        MockedFlowEnd currentEnd = references.end(flow, 2, DecisionValue.DENY, reasonCode);
        references.end(flow, 2, DecisionValue.REVIEW, reasonCode);
        MockedFlowEnd deletedEnd =
            references.end(references.flow(1, FlowStatus.DELETED), 1, DecisionValue.DENY, reasonCode);
        return new ScopeMatrix(historyRule, currentRule, deletedRule, historyEnd, currentEnd, deletedEnd);
    }

    private RejectionReasonReferencesResponseChecker read(String code, String query) {
        String path = REASONS + "/" + code + "/references";
        return get(query.isEmpty() ? path : path + "?" + query, RejectionReasonReferencesResponseChecker::new);
    }
}
