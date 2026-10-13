package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.risk.decision.domain.enums.DictionaryStatus.DISABLED;
import static com.paytech.risk.decision.domain.enums.DictionaryType.SUBJECT_TAG;
import static com.paytech.risk.test.builder.decision.DictionaryEntryBuilder.tag;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.risk.api.decision.DecisionDictionaryApi;
import com.paytech.risk.test.base.AbstractDecisionDictionaryApiTest;
import com.paytech.risk.test.builder.decision.DictionaryEntryBuilder;
import com.paytech.risk.test.checker.decision.DictionaryEntryResponseChecker;
import com.paytech.risk.test.mapper.decision.DictionaryTestMapper;
import com.paytech.risk.test.mocker.decision.DictionaryMocker;
import com.paytech.risk.test.mocker.decision.DictionaryMocker.MockedDictionary;

/**
 * 主体标签当前值分页与详情查询
 * <li>正常流程：按标签编码升序分页，关键词对编码或名称做字面包含匹配（大小写不敏感，通配符按字面），可按状态筛选；详情返回停用记录。</li>
 * <li>未找到：关键词无匹配返回空页；未登记编码返回 404。</li>
 * <li>参数：分页、状态、关键词格式与长度、路径编码格式、未知/重复/空 Query 与 GET Body 均在查询前拒绝（400）。</li>
 * <li>完整性：命中的当前记录损坏时失败关闭（500），未命中的损坏记录不影响查询。</li>
 *
 * @see DecisionDictionaryApi#listSubjectTags(String, String, Integer, Integer, String, String)
 * @see DecisionDictionaryApi#getSubjectTag(String, String, String)
 */
class SubjectTagQueryApiAutoTest extends AbstractDecisionDictionaryApiTest {

    @Autowired
    private DictionaryMocker dictionaries;

    @Autowired
    private DictionaryTestMapper dictionaryMapper;

    /** 关键词前缀下的三条标签按编码升序返回，停用与多行说明记录同样返回。 */
    @Test
    void test_normalFlow() {
        String token = dictionaries.newCode(SUBJECT_TAG);
        MockedDictionary c = dictionaries.mock(tag().code(token + "_C"));
        MockedDictionary a = dictionaries.mock(tag().code(token + "_A"));
        MockedDictionary b = dictionaries.mock(tag().code(token + "_B").status(DISABLED).description("第一行\n第二行\t制表")
            .version(7));

        list("keyword=" + token).assertPage(1, 10, 3).assertItems(a, b, c);
    }

    /** 状态筛选只返回对应资格的标签，计数与当前页同一条件。 */
    @Test
    void test_normalFlow_statusFilter() {
        String token = dictionaries.newCode(SUBJECT_TAG);
        MockedDictionary a = dictionaries.mock(tag().code(token + "_A"));
        MockedDictionary b = dictionaries.mock(tag().code(token + "_B").status(DISABLED));
        MockedDictionary c = dictionaries.mock(tag().code(token + "_C"));

        list("keyword=" + token + "&status=ACTIVE").assertPage(1, 10, 2).assertItems(a, c);
        list("status=DISABLED&keyword=" + token).assertPage(1, 10, 1).assertItems(b);
    }

    /** 分页按编码切分，越界页返回空列表但保留总数与请求坐标。 */
    @Test
    void test_normalFlow_paging() {
        String token = dictionaries.newCode(SUBJECT_TAG);
        MockedDictionary[] all = IntStream.rangeClosed(1, 11)
            .mapToObj(i -> dictionaries.mock(tag().code(token + "_" + (char)('A' + i - 1))))
            .toArray(MockedDictionary[]::new);

        list("keyword=" + token).assertPage(1, 10, 11).assertItems(Arrays.copyOfRange(all, 0, 10));
        list("keyword=" + token + "&page=2&size=10").assertPage(2, 10, 11).assertItems(all[10]);
        list("keyword=" + token + "&page=3").assertPage(3, 10, 11).assertItems();
        list("keyword=" + token + "&size=20").assertPage(1, 20, 11).assertItems(all);
    }

    /** 关键词同时匹配编码或名称，且不区分大小写。 */
    @Test
    void test_normalFlow_keywordMatchesCodeOrName() {
        String token = dictionaries.newCode(SUBJECT_TAG);
        MockedDictionary byCode = dictionaries.mock(tag().code(token + "_CODE"));
        MockedDictionary byName = dictionaries.mock(tag().name("名称含 " + token));
        dictionaries.mock(tag().name("无关标签"));

        MockedDictionary[] expected = byCode.code().compareTo(byName.code()) < 0
            ? new MockedDictionary[] {byCode, byName} : new MockedDictionary[] {byName, byCode};
        list("keyword=" + token).assertPage(1, 10, 2).assertItems(expected);
        list("keyword=" + token.toLowerCase(Locale.ROOT)).assertPage(1, 10, 2).assertItems(expected);
    }

    /** 不带筛选时返回全部标签，本例记录按编码顺序出现在结果中。 */
    @Test
    void test_normalFlow_withoutFilters() {
        String token = dictionaries.newCode(SUBJECT_TAG);
        MockedDictionary b = dictionaries.mock(tag().code(token + "_B").status(DISABLED));
        MockedDictionary a = dictionaries.mock(tag().code(token + "_A"));
        long total = dictionaryMapper.count(SUBJECT_TAG);

        list("size=200").assertPage(1, 200, total).assertContainsInOrder(a, b);
    }

    /** % _ ! \ 均按字面匹配，不作为 LIKE 通配符或转义符。 */
    @ParameterizedTest(name = "字面量 {0}")
    @MethodSource("literalCharacters")
    void test_edgeCase_wildcardsMatchLiterally(String literal) {
        String token = dictionaries.newCode(SUBJECT_TAG);
        MockedDictionary literalName = dictionaries.mock(tag().name(token + literal + "Z"));
        dictionaries.mock(tag().name(token + "YZ"));

        list("keyword=" + token + literal).assertPage(1, 10, 1).assertItems(literalName);
    }

    /** 关键词无匹配时返回空页并保留分页坐标。 */
    @Test
    void test_noDataFound() {
        list("keyword=" + dictionaries.newCode(SUBJECT_TAG)).assertPage(1, 10, 0).assertItems();
    }

    /** 关键词恰为 100 个码点（含补充平面字符）时合法。 */
    @Test
    void test_edgeCase_keywordAtLengthLimit() {
        String token = dictionaries.newCode(SUBJECT_TAG);
        String keyword = token + "😀".repeat(100 - token.length());
        MockedDictionary matched = dictionaries.mock(tag().name(keyword));

        list("keyword=" + keyword).assertPage(1, 10, 1).assertItems(matched);
    }

    /** 当前页中的标签记录损坏时整体失败，不返回部分结果。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("corruptions")
    void test_businessRuleViolation_corruptedRecordFailsClosed(String scenario, Supplier<DictionaryEntryBuilder> entry) {
        String token = dictionaries.newCode(SUBJECT_TAG);
        dictionaries.mock(tag().code(token + "_A"));
        MockedDictionary corrupted = dictionaries.mock(entry.get().code(token + "_B"));

        list("keyword=" + token).assertProblem(500, INTERNAL_ERROR.getCode());
        detail(corrupted.code()).assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 损坏记录不在筛选结果中时不影响查询。 */
    @Test
    void test_edgeCase_corruptedRecordOutsideFilter() {
        String token = dictionaries.newCode(SUBJECT_TAG);
        MockedDictionary a = dictionaries.mock(tag().code(token + "_A"));
        dictionaries.mock(tag().code(token + "_B").status(DISABLED).name(" 首部空白"));

        list("keyword=" + token + "&status=ACTIVE").assertPage(1, 10, 1).assertItems(a);
    }

    /** 列表查询串非法时在查询前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidQueries")
    void test_invalidParameter(String scenario, String query) {
        list(query).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 携带 Body 的列表 GET 请求。 */
    @Test
    void test_invalidParameter_listWithBody() {
        exchange(HttpMethod.GET, TAGS, headers(ACTOR), "{}", DictionaryEntryResponseChecker::new)
            .assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 详情返回标签当前值及审计信息。 */
    @Test
    void test_normalFlow_detail() {
        MockedDictionary entry = dictionaries.mock(tag().version(5));

        detail(entry.code()).assertEntry(entry);
    }

    /** 停用且无说明的标签仍可查询，说明输出为 null，且不输出默认文案字段。 */
    @Test
    void test_normalFlow_detailDisabled() {
        MockedDictionary entry = dictionaries.mock(tag().status(DISABLED).description(null));

        detail(entry.code()).assertEntry(entry);
    }

    /** 64 位编码边界及 100 码点名称、500 码点多行说明可完整读取。 */
    @Test
    void test_edgeCase_detailLengthLimits() {
        String code = dictionaries.newCode(SUBJECT_TAG);
        MockedDictionary entry = dictionaries.mock(tag().code(code + "X".repeat(64 - code.length()))
            .name("名".repeat(99) + "😀").description("说".repeat(499) + "\n"));

        detail(entry.code()).assertEntry(entry);
    }

    /** 未登记的标签编码。 */
    @Test
    void test_noDataFound_detail() {
        detail(dictionaries.newCode(SUBJECT_TAG)).assertNotFound();
    }

    /** 路径编码格式非法时在查询前拒绝。 */
    @ParameterizedTest(name = "tagCode={0}")
    @MethodSource("invalidCodes")
    void test_invalidParameter_detailCode(String code) {
        detail(code).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 详情不接受任何 Query 或 Body。 */
    @Test
    void test_invalidParameter_detailQueryOrBody() {
        MockedDictionary entry = dictionaries.mock(tag());

        get(TAGS + "/" + entry.code() + "?page=1", DictionaryEntryResponseChecker::new).assertProblem(400,
            INVALID_REQUEST.getCode());
        exchange(HttpMethod.GET, TAGS + "/" + entry.code(), headers(ACTOR), "{}",
            DictionaryEntryResponseChecker::new).assertProblem(400, INVALID_REQUEST.getCode());
    }

    static Stream<String> literalCharacters() {
        return Stream.of("%", "_", "!", "\\");
    }

    static Stream<Arguments> corruptions() {
        return Stream.of(corrupted("名称首尾空白", () -> tag().name("尾部空白 ")),
            corrupted("名称为空串", () -> tag().name("")),
            corrupted("名称含换行", () -> tag().name("第一行\n第二行")),
            corrupted("说明含控制字符", () -> tag().description("说明\u0001")),
            corrupted("修订号为 0", () -> tag().version(0)),
            corrupted("更新人为 0", () -> tag().updatedBy(0)));
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(Arguments.of("page 为 0", "page=0"), Arguments.of("page 超过 100000", "page=100001"),
            Arguments.of("page 非数字", "page=one"), Arguments.of("size 不在允许值内", "size=15"),
            Arguments.of("status 小写", "status=active"), Arguments.of("status 未知", "status=ENABLED"),
            Arguments.of("status 为空值", "status="), Arguments.of("keyword 为空值", "keyword="),
            Arguments.of("keyword 首部空白", "keyword= MOCK"), Arguments.of("keyword 尾部空白", "keyword=MOCK "),
            Arguments.of("keyword 超过 100 码点", "keyword=" + "K".repeat(101)),
            Arguments.of("keyword 含控制字符", "keyword=MO\u0001CK"),
            Arguments.of("keyword 重复", "keyword=MOCK&keyword=MOCK"),
            Arguments.of("未知参数", "scope=ALL"));
    }

    static Stream<String> invalidCodes() {
        return Stream.of("mock_tag", "1MOCK", "_MOCK", "MOCK-TAG", "M".repeat(65));
    }

    private static Arguments corrupted(String scenario, Supplier<DictionaryEntryBuilder> entry) {
        return Arguments.of(scenario, entry);
    }

    private DictionaryEntryResponseChecker list(String query) {
        return get(query.isEmpty() ? TAGS : TAGS + "?" + query, DictionaryEntryResponseChecker::new);
    }

    private DictionaryEntryResponseChecker detail(String code) {
        return get(TAGS + "/" + code, DictionaryEntryResponseChecker::new);
    }
}
