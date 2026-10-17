package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.risk.decision.domain.enums.DecisionEnvironment.PRODUCTION;
import static com.paytech.risk.decision.domain.enums.DecisionReleaseStatus.ACTIVE;
import static com.paytech.risk.decision.domain.enums.DecisionReleaseStatus.APPROVED;
import static com.paytech.risk.decision.domain.enums.DecisionEnvironment.TEST;
import static com.paytech.risk.decision.domain.enums.DictionaryStatus.DISABLED;
import static com.paytech.risk.decision.domain.enums.DictionaryType.SUBJECT_TAG;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.DRAFT;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.FROZEN;
import static com.paytech.risk.test.builder.decision.DictionaryEntryBuilder.tag;
import static com.paytech.risk.test.builder.decision.RuleSetBuilder.versions;
import static com.paytech.risk.test.checker.decision.SubjectTagReferencesResponseChecker.tag;
import static com.paytech.risk.test.mocker.decision.DictionaryReferenceMocker.TARGET_BINDING;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.risk.decision.api.DecisionDictionaryApi;
import com.paytech.risk.test.base.AbstractDecisionDictionaryApiTest;
import com.paytech.risk.test.builder.decision.ReleaseBuilder;
import com.paytech.risk.test.checker.decision.SubjectTagReferencesResponseChecker;
import com.paytech.risk.test.checker.decision.SubjectTagReferencesResponseChecker.Expected;
import com.paytech.risk.test.mocker.decision.DictionaryMocker;
import com.paytech.risk.test.mocker.decision.DictionaryMocker.MockedDictionary;
import com.paytech.risk.test.mocker.decision.DictionaryReferenceMocker;
import com.paytech.risk.test.mocker.decision.DictionaryReferenceMocker.MockedRule;
import com.paytech.risk.test.mocker.decision.DictionaryReferenceMocker.MockedRuleAction;
import com.paytech.risk.test.mocker.decision.DictionaryReferenceMocker.ReferencedRuleSet;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;

/**
 * 主体标签在规则动作中的精确引用分页
 * <li>正常流程：每个以 TAG 字面参数保存该标签的 ADD_RISK_TAG 动作是一条引用，保留动作序号与主体绑定；按范围 ALL/CURRENT/HISTORY/DELETED
 * 筛选，附带精确版本的活动环境；停用标签仍可反查。</li>
 * <li>排除项：其他标签、SUBJECT_ID 参数上的同码字面值、规则上的同码原因不计入；同一动作的多个 TAG 值只算一条。</li>
 * <li>未找到：无引用返回空页；未登记编码返回 404。</li>
 * <li>失败关闭：标签记录损坏、引用缺少主体绑定或发布闭包断边时返回 500。</li>
 * <li>参数：范围、分页、路径编码、未知/重复/空 Query 与 GET Body 均在查询前拒绝（400）。</li>
 *
 * @see DecisionDictionaryApi#getSubjectTagReferences(String, String, String, Integer, Integer, String)
 */
class SubjectTagReferencesApiAutoTest extends AbstractDecisionDictionaryApiTest {

    @Autowired
    private DictionaryMocker dictionaries;

    @Autowired
    private DictionaryReferenceMocker references;

    @Autowired
    private RuleSetMocker ruleSets;

    /** 历史、当前与删除三类保存项，当前规则含两个标签动作。 */
    private record ScopeMatrix(MockedRuleAction history, MockedRuleAction current1, MockedRuleAction current2,
        MockedRuleAction deleted) {
    }

    /** 缺省范围 ALL 返回全部保存项，冻结版本附带生效环境（非 ACTIVE 发布不计入）。 */
    @Test
    void test_normalFlow() {
        MockedDictionary tag = dictionaries.mock(tag());
        ScopeMatrix m = scopeMatrix(tag.code());

        Expected[] all = {tag(m.history(), PRODUCTION), tag(m.current1()), tag(m.current2()), tag(m.deleted())};
        read(tag.code(), "").assertReferences(1, 10, all);
        read(tag.code(), "scope=ALL").assertReferences(1, 10, all);
    }

    /** CURRENT/HISTORY 只看可配置根的当前指针，DELETED 返回整体删除的规则集（当前指针仍为真）。 */
    @Test
    void test_normalFlow_scopes() {
        MockedDictionary tag = dictionaries.mock(tag());
        ScopeMatrix m = scopeMatrix(tag.code());

        read(tag.code(), "scope=CURRENT").assertReferences(1, 10, tag(m.current1()), tag(m.current2()));
        read(tag.code(), "scope=HISTORY").assertReferences(1, 10, tag(m.history(), PRODUCTION));
        read(tag.code(), "scope=DELETED").assertReferences(1, 10, tag(m.deleted()));
    }

    /** 两条规则共 11 个标签动作，按规则 ID、动作序号升序分页，越界页为空但保留总数。 */
    @Test
    void test_normalFlow_paging() {
        MockedDictionary tag = dictionaries.mock(tag());
        ReferencedRuleSet ruleSet = references.ruleSet(versions(DRAFT));
        List<Expected> all = new ArrayList<>();
        for (int ruleIndex = 0; ruleIndex < 2; ruleIndex++) {
            MockedRule rule = references.rule(ruleSet, 1, null);
            for (int action = 0; action < (ruleIndex == 0 ? 6 : 5); action++) {
                all.add(tag(references.addTag(rule, TARGET_BINDING, tag.code())));
            }
        }
        Expected[] expected = all.toArray(Expected[]::new);

        read(tag.code(), "").assertReferences(1, 10, expected);
        read(tag.code(), "page=2&size=10").assertReferences(2, 10, expected);
        read(tag.code(), "page=3").assertReferences(3, 10, expected);
        read(tag.code(), "size=50&scope=CURRENT").assertReferences(1, 50, expected);
    }

    /** 停用标签仍可反查；同一动作保存多个 TAG 值且其中之一为该标签时只算一条。 */
    @Test
    void test_normalFlow_disabledTagAndMultiValueAction() {
        MockedDictionary tag = dictionaries.mock(tag().status(DISABLED));
        MockedRule rule = references.rule(references.ruleSet(versions(DRAFT)), 1, null);
        MockedRuleAction action =
            references.addTag(rule, TARGET_BINDING, dictionaries.newCode(SUBJECT_TAG), tag.code(), tag.code());

        read(tag.code(), "").assertReferences(1, 10, tag(action));
    }

    /** 已登记但只出现在非引用位置的标签返回空页。 */
    @Test
    void test_noDataFound() {
        MockedDictionary tag = dictionaries.mock(tag());
        ReferencedRuleSet ruleSet = references.ruleSet(versions(DRAFT));
        MockedRule rule = references.rule(ruleSet, 1, tag.code());
        references.deny(rule);
        references.subjectParameter(references.addTag(rule, TARGET_BINDING, dictionaries.newCode(SUBJECT_TAG)),
            tag.code());

        read(tag.code(), "").assertReferences(1, 10);
    }

    /** 未登记的标签编码。 */
    @Test
    void test_noDataFound_unregistered() {
        read(dictionaries.newCode(SUBJECT_TAG), "").assertNotFound();
    }

    /** 标签当前记录损坏时失败关闭。 */
    @Test
    void test_businessRuleViolation_corruptedTag() {
        MockedDictionary tag = dictionaries.mock(tag().name("名称\n换行"));

        read(tag.code(), "").assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 标签动作缺少主体绑定时引用坐标损坏，失败关闭。 */
    @Test
    void test_businessRuleViolation_missingTargetBinding() {
        MockedDictionary tag = dictionaries.mock(tag());
        references.addTag(references.rule(references.ruleSet(versions(DRAFT)), 1, null), null, tag.code());

        read(tag.code(), "").assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 决策点存在发布断边时失败关闭。 */
    @Test
    void test_businessRuleViolation_brokenReleaseClosure() {
        MockedDictionary tag = dictionaries.mock(tag());
        references.addTag(references.rule(references.ruleSet(versions(FROZEN)), 1, null), TARGET_BINDING, tag.code());
        ruleSets.mockBrokenRelease();

        read(tag.code(), "").assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 查询串非法时在查询前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidQueries")
    void test_invalidParameter(String scenario, String query) {
        MockedDictionary tag = dictionaries.mock(tag());

        read(tag.code(), query).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 路径编码格式非法。 */
    @ParameterizedTest(name = "tagCode={0}")
    @MethodSource("invalidCodes")
    void test_invalidParameter_tagCode(String code) {
        read(code, "").assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 携带 Body 的 GET 请求。 */
    @Test
    void test_invalidParameter_getWithBody() {
        MockedDictionary tag = dictionaries.mock(tag());

        exchange(HttpMethod.GET, TAGS + "/" + tag.code() + "/references", headers(ACTOR), "{}",
            SubjectTagReferencesResponseChecker::new).assertProblem(400, INVALID_REQUEST.getCode());
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(Arguments.of("scope 小写", "scope=current"), Arguments.of("scope 未知", "scope=RELEASED"),
            Arguments.of("scope 为空值", "scope="), Arguments.of("size 重复", "size=10&size=10"),
            Arguments.of("page 非数字", "page=x"), Arguments.of("page 为负数", "page=-1"),
            Arguments.of("size 不在允许值内", "size=1"), Arguments.of("未知参数", "keyword=MOCK"));
    }

    static Stream<String> invalidCodes() {
        return Stream.of("mock_tag", "0TAG", "TAG$", "T".repeat(65));
    }

    /**
     * 构造引用矩阵
     * <li>可配置规则集 v1 冻结（历史，PRODUCTION 生效，TEST 仅已批准）、v2 草稿（当前，同一规则两个标签动作）；删除规则集 v1。</li>
     * <li>另插入其他标签的动作、SUBJECT_ID 参数上的同码值和同码拒绝原因，均不应计入。</li>
     */
    private ScopeMatrix scopeMatrix(String tagCode) {
        ReferencedRuleSet configurable = references.ruleSet(versions(FROZEN, DRAFT));
        MockedRuleAction history = references.addTag(references.rule(configurable, 1, null), TARGET_BINDING, tagCode);
        ruleSets.mockRelease(configurable.ruleSet(), 1, ReleaseBuilder.of(PRODUCTION, ACTIVE));
        ruleSets.mockRelease(configurable.ruleSet(), 1, ReleaseBuilder.of(TEST, APPROVED));
        MockedRule currentRule = references.rule(configurable, 2, tagCode);
        references.deny(currentRule);
        MockedRuleAction current1 = references.addTag(currentRule, TARGET_BINDING, tagCode);
        MockedRuleAction other = references.addTag(currentRule, TARGET_BINDING, dictionaries.newCode(SUBJECT_TAG));
        references.subjectParameter(other, tagCode);
        MockedRuleAction current2 = references.addTag(currentRule, "CURRENT_PARTNER_V1", tagCode);

        ReferencedRuleSet deleted = references.ruleSet(versions(FROZEN).deleted());
        MockedRuleAction deletedAction = references.addTag(references.rule(deleted, 1, null), TARGET_BINDING, tagCode);
        return new ScopeMatrix(history, current1, current2, deletedAction);
    }

    private SubjectTagReferencesResponseChecker read(String code, String query) {
        String path = TAGS + "/" + code + "/references";
        return get(query.isEmpty() ? path : path + "?" + query, SubjectTagReferencesResponseChecker::new);
    }
}
