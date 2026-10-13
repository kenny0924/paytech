package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.risk.decision.domain.enums.DecisionValueType.STRING;
import static com.paytech.risk.decision.domain.enums.VariableFormat.POSITIVE_ID;
import static com.paytech.risk.decision.domain.enums.VariableUsage.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.risk.api.decision.RiskVariableApi;
import com.paytech.risk.decision.domain.enums.VariableStatus;
import com.paytech.risk.test.base.AbstractVariableApiTest;
import com.paytech.risk.test.builder.decision.VariableBuilder;
import com.paytech.risk.test.checker.decision.VariableListResponseChecker;
import com.paytech.risk.test.mocker.decision.VariableMocker.MockedVariable;

/**
 * 变量目录分页查询与按编码批取
 * <li>正常流程：缺省排除墓碑并按编码升序分页；来源、类型、状态、用途（含决策点与节点类型）筛选；关键字字面包含编码或中文名称。</li>
 * <li>编码批取：包含停用与墓碑，缺失编码按请求顺序返回，不返回分页字段；上限 100 个。</li>
 * <li>参数：互斥模式、用途上下文、编码格式与重复、枚举大小写、关键字、分页边界、未知/重复/空 Query 与 GET Body 拒绝（400）。</li>
 * <li>数据损坏：当前页任一变量定义损坏时整体失败关闭（500）。</li>
 *
 * @see RiskVariableApi#listVariables(String, String, String, String, String, String, String, String, String, String,
 *      Integer, Integer)
 */
class VariableListApiAutoTest extends AbstractVariableApiTest {

    /** 缺省分页：第 1 页、每页 20，排除墓碑，按编码升序；停用定义仍在目录中，集合带前三项预览。 */
    @Test
    void test_normalFlow() {
        MockedVariable constant = variables.mock(VariableBuilder.constantNumber("12.50000000"));
        MockedVariable collection = variables.mock(VariableBuilder.textCollection().status(VariableStatus.DISABLED)
            .member("B", "bee").member("A", null).member("a", "lower").member("C", "sea"));
        variables.mock(VariableBuilder.constantText("gone").status(VariableStatus.DELETED));
        MockedVariable input = variables.mock(VariableBuilder.countryInput().systemDefinition());
        Instant from = now();

        list("keyword=" + variables.token()).assertPage(1, 20, 3, constant, collection, input).assertValuesAsOfBetween(from);
    }

    /** 分页按编码切分，越界页为空但保留总数；页码和每页数量取上限仍合法。 */
    @Test
    void test_normalFlow_paging() {
        List<MockedVariable> all = new ArrayList<>();
        IntStream.range(0, 5).forEach(index -> all.add(variables.mock(VariableBuilder.constantText("v" + index))));
        String keyword = "keyword=" + variables.token();

        list(keyword + "&page=1&size=2").assertPage(1, 2, 5, all.get(0), all.get(1));
        list(keyword + "&page=2&size=2").assertPage(2, 2, 5, all.get(2), all.get(3));
        list(keyword + "&page=3&size=2").assertPage(3, 2, 5, all.get(4));
        list(keyword + "&page=4&size=2").assertPage(4, 2, 5);
        list(keyword + "&size=1").assertPage(1, 1, 5, all.get(0));
        list(keyword + "&page=10000&size=100").assertPage(10000, 100, 5);
    }

    /** 各筛选条件组合只返回符合条件的定义；用途筛选只返回启用定义，节点输出仅在规则集出线条件中出现。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("filters")
    void test_normalFlow_filters(String query, int[] expected) {
        List<MockedVariable> fixtures = filterFixtures();

        list("keyword=" + variables.token() + "&" + query).assertPage(1, 20, expected.length,
            IntStream.of(expected).mapToObj(fixtures::get).toArray(MockedVariable[]::new));
    }

    /** 关键字按字面包含匹配中文名称或编码，通配符不生效。 */
    @Test
    void test_normalFlow_keyword() {
        MockedVariable literal = variables.mock(VariableBuilder.constantText("x").variableName("风控阈值_100%!" + variables.token()));
        variables.mock(VariableBuilder.constantText("x").variableName("风控阈值X100abc!" + variables.token()));

        list("keyword=风控阈值_100%!" + variables.token()).assertPage(1, 20, 1, literal);
        list("keyword=" + literal.code()).assertPage(1, 20, 1, literal);
    }

    /** 编码批取包含停用与墓碑，找到的按编码升序，缺失编码按请求顺序返回，不返回分页字段。 */
    @Test
    void test_normalFlow_batchCodes() {
        MockedVariable active = variables.mock(VariableBuilder.constantText("a"));
        MockedVariable disabled = variables.mock(VariableBuilder.constantText("d").status(VariableStatus.DISABLED));
        MockedVariable deleted = variables.mock(VariableBuilder.constantText("x").status(VariableStatus.DELETED));
        String absentB = variables.token() + "_ABSENT_B";
        String absentA = variables.token() + "_ABSENT_A";

        list("codes=" + deleted.code() + "," + absentB + "," + active.code() + "," + absentA + "," + disabled.code())
            .assertBatch(List.of(absentB, absentA), active, disabled, deleted);
    }

    /** 批取上限 100 个编码。 */
    @Test
    void test_edgeCase_batchLimit() {
        MockedVariable variable = variables.mock(VariableBuilder.constantText("a"));
        List<String> codes = new ArrayList<>(List.of(variable.code()));
        IntStream.rangeClosed(1, 99).forEach(index -> codes.add(variables.token() + "_ABSENT_" + index));

        list("codes=" + String.join(",", codes)).assertBatch(codes.subList(1, 100), variable);
        codes.add(variables.token() + "_ABSENT_100");
        list("codes=" + String.join(",", codes)).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 没有匹配的目录返回空页，批取全部缺失时只返回缺失编码。 */
    @Test
    void test_noDataFound() {
        String absent = variables.token() + "_ABSENT";

        list("keyword=" + absent).assertPage(1, 20, 0);
        list("codes=" + absent).assertBatch(List.of(absent));
    }

    /** 当前页任一定义损坏时整体失败关闭，不跳过损坏行；同页的完好定义也不返回。 */
    @Test
    void test_businessRuleViolation_corruptedDefinitionFailsClosed() {
        variables.mock(VariableBuilder.constantText("ok"));
        MockedVariable corrupted = variables.mock(VariableBuilder.constantText("broken"));
        variables.corrupt(corrupted, "string_value", null);

        list("keyword=" + variables.token()).assertProblem(500, INTERNAL_ERROR.getCode());
        list("codes=" + corrupted.code()).assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 集合预览成员损坏时整体失败关闭。 */
    @Test
    void test_businessRuleViolation_corruptedMemberFailsClosed() {
        MockedVariable collection = variables.mock(VariableBuilder.textCollection().member("A", null));
        variables.corruptMemberKeys(collection);

        list("keyword=" + variables.token()).assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 查询串非法时在查询前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidQueries")
    void test_invalidParameter(String scenario, String query) {
        list(query).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 携带 Body 的 GET 请求。 */
    @Test
    void test_invalidParameter_getWithBody() {
        HttpHeaders headers = headers();

        new VariableListResponseChecker(http.exchange(HttpMethod.GET, VARIABLES, headers, "{}"), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID)).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /**
     * 筛选夹具，按编码升序：
     * 0 规则+流程常量、1 全局规则常量、2 停用流程集合、3 系统国家输入、4 动作主体输入、5 节点输出、6 墓碑常量。
     */
    private List<MockedVariable> filterFixtures() {
        return List.of(variables.mock(VariableBuilder.constantText("text")),
            variables.mock(VariableBuilder.constantNumber("1.00000000").pointCode(null).usages(RULE_CONDITION)),
            variables.mock(VariableBuilder.textCollection().usages(FLOW_CONDITION).status(VariableStatus.DISABLED)),
            variables.mock(VariableBuilder.countryInput().systemDefinition()),
            variables.mock(VariableBuilder.input("client_id", STRING, POSITIVE_ID).usages(ACTION_PARAMETER)),
            variables.mock(VariableBuilder.nodeMatched()),
            variables.mock(VariableBuilder.constantText("gone").status(VariableStatus.DELETED)));
    }

    static Stream<Arguments> filters() {
        return Stream.of(filter("sourceType=CONSTANT", 0, 1), filter("sourceType=COLLECTION", 2),
            filter("sourceType=INPUT", 3, 4), filter("sourceType=NODE_OUTPUT", 5), filter("valueType=NUMBER", 1),
            filter("valueType=BOOLEAN", 5), filter("status=ACTIVE", 0, 1, 3, 4, 5), filter("status=DISABLED", 2),
            filter("status=DELETED", 6), filter("pointCode=CHECKOUT_PREPARE", 0, 1, 2, 3, 4, 5),
            filter("pointCode=CHECKOUT_PREPARE&usage=RULE_CONDITION", 0, 1, 3),
            filter("pointCode=CHECKOUT_PREPARE&usage=FLOW_CONDITION", 0, 3),
            filter("pointCode=CHECKOUT_PREPARE&usage=FLOW_CONDITION&nodeType=START", 0, 3),
            filter("pointCode=CHECKOUT_PREPARE&usage=FLOW_CONDITION&nodeType=RULE_SET", 0, 3, 5),
            filter("pointCode=CHECKOUT_PREPARE&usage=ACTION_PARAMETER", 4),
            filter("pointCode=CHECKOUT_PREPARE&usage=RULE_CONDITION&sourceType=INPUT", 3),
            filter("status=DISABLED&usage=FLOW_CONDITION&pointCode=CHECKOUT_PREPARE"),
            filter("sourceType=CONSTANT&valueType=STRING", 0));
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(Arguments.of("批取与关键字同时出现", "codes=A_CODE&keyword=A"),
            Arguments.of("批取与分页同时出现", "codes=A_CODE&page=1"),
            Arguments.of("批取与状态同时出现", "codes=A_CODE&status=ACTIVE"),
            Arguments.of("批取编码重复", "codes=A_CODE,A_CODE"), Arguments.of("批取编码小写", "codes=a_code"),
            Arguments.of("批取编码含空段", "codes=A_CODE,,B_CODE"), Arguments.of("批取编码末尾逗号", "codes=A_CODE,"),
            Arguments.of("来源未知", "sourceType=FEATURE"), Arguments.of("来源小写", "sourceType=constant"),
            Arguments.of("类型未知", "valueType=INTEGER"), Arguments.of("状态未知", "status=ENABLED"),
            Arguments.of("用途缺少决策点", "usage=RULE_CONDITION"),
            Arguments.of("用途未知", "pointCode=CHECKOUT_PREPARE&usage=ANY"),
            Arguments.of("决策点未支持", "pointCode=PAYOUT_CREATION"),
            Arguments.of("决策点小写", "pointCode=checkout_prepare"),
            Arguments.of("节点类型不用于规则条件", "pointCode=CHECKOUT_PREPARE&usage=RULE_CONDITION&nodeType=START"),
            Arguments.of("节点类型缺少用途", "nodeType=RULE_SET"),
            Arguments.of("节点类型未知", "pointCode=CHECKOUT_PREPARE&usage=FLOW_CONDITION&nodeType=END"),
            Arguments.of("关键字超过 128 字符", "keyword=" + "k".repeat(129)),
            Arguments.of("关键字含控制字符", "keyword=a\tb"), Arguments.of("page 为 0", "page=0"),
            Arguments.of("page 超过 10000", "page=10001"), Arguments.of("size 为 0", "size=0"),
            Arguments.of("size 超过 100", "size=101"), Arguments.of("page 非数字", "page=one"),
            Arguments.of("参数为空值", "keyword="), Arguments.of("参数重复", "status=ACTIVE&status=ACTIVE"),
            Arguments.of("未知参数", "expectedVersion=1"));
    }

    private static Arguments filter(String query, int... expected) {
        return Arguments.of(query, expected);
    }

    private VariableListResponseChecker list(String query) {
        HttpHeaders headers = headers();
        return new VariableListResponseChecker(http.get(VARIABLES + "?" + query, headers), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }
}
