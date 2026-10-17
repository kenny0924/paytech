package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.api.decision.DecisionFlowQueryApi;
import com.paytech.risk.decision.domain.enums.VariableStatus;
import com.paytech.risk.test.base.MockTestBase;
import com.paytech.risk.test.checker.decision.FlowAuthoringContextResponseChecker;
import com.paytech.risk.test.checker.decision.FlowAuthoringContextResponseChecker.ExpectedField;
import com.paytech.risk.test.mocker.decision.FlowMocker;

/**
 * 流程配置能力目录
 * <li>正常流程：缺省或显式 CHECKOUT_PREPARE 返回固定能力、容量上限，以及种子目录中可用于流程条件的系统输入与节点输出（按变量编码排序）。</li>
 * <li>目录筛选：只取系统管理且声明流程条件用途、未删除的变量；停用变量仍列出，运算符由值类型决定。</li>
 * <li>未找到：不受支持的决策点返回 404。</li>
 * <li>参数：决策点编码格式、空值、重复、未知 Query 与 GET Body 在查询前拒绝（400）。</li>
 * <li>业务规则：决策点停用时不可配置（400）。</li>
 *
 * @see DecisionFlowQueryApi#authoringContext(String, String, String)
 */
class FlowAuthoringContextApiAutoTest extends MockTestBase {
    private static final String PATH = "/decision-flows/v1/authoring-context";

    private static final List<String> TEXT_OPERATORS = List.of("EQ", "NE", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL");

    private static final List<String> ORDERED_OPERATORS = List.of("EQ", "NE", "GT", "GTE", "LT", "LTE", "IN",
        "NOT_IN", "BETWEEN", "IS_NULL", "IS_NOT_NULL");

    /** 种子目录中当前合同（ORDER_PREPARATION v5）可用于流程条件的系统输入，按变量编码排序；旧合同的 INPUT_LOCALE 不在其中。 */
    private static final List<ExpectedField> SEED_FIELDS = List.of(
        input("client_id", "Client 标识", "STRING", "POSITIVE_ID", "INPUT_CLIENT_ID"),
        input("consumer_country_code", "国家/地区", "STRING", "UPPER_ALPHA_2", "INPUT_COUNTRY_CODE"),
        input("currency", "币种", "STRING", "UPPER_ALPHA_3", "INPUT_CURRENCY"),
        input("discount_amount", "折扣金额", "NUMBER", "NONE", "INPUT_DISCOUNT_AMOUNT"),
        input("entity_no", "经营主体编号", "STRING", "TEXT", "INPUT_ENTITY_NO"),
        input("entity_source_type", "实体来源", "STRING", "TOKEN", "INPUT_ENTITY_SOURCE_TYPE"),
        input("industry_code", "行业代码", "STRING", "TOKEN", "INPUT_INDUSTRY_CODE"),
        input("line_count", "明细行数", "NUMBER", "NONE", "INPUT_LINE_COUNT"),
        optionalInput("local_partner_id", "Local Partner 标识", "STRING", "POSITIVE_ID", "INPUT_LOCAL_PARTNER_ID"),
        input("mcc_code", "MCC", "STRING", "DIGITS_4", "INPUT_MCC_CODE"),
        input("merchandise_amount", "商品金额", "NUMBER", "NONE", "INPUT_MERCHANDISE_AMOUNT"),
        input("net_amount", "实付金额", "NUMBER", "NONE", "INPUT_NET_AMOUNT"));

    /** 种子目录中的规则集节点输出。 */
    private static final List<ExpectedField> SEED_OUTPUTS = List.of(
        new ExpectedField("RULE_SET", "decision", "本节点处理结果", "STRING", false, TEXT_OPERATORS, null,
            "NODE_DECISION"),
        new ExpectedField("RULE_SET", "matched", "本节点是否命中", "BOOLEAN", true, TEXT_OPERATORS, null,
            "NODE_MATCHED"));

    @Autowired
    private FlowMocker flows;

    /** 缺省决策点为 CHECKOUT_PREPARE。 */
    @Test
    void test_normalFlow() {
        read("").assertCatalog(SEED_FIELDS, SEED_OUTPUTS);
    }

    /** 显式指定 CHECKOUT_PREPARE。 */
    @Test
    void test_normalFlow_explicitPoint() {
        read("?pointCode=CHECKOUT_PREPARE").assertCatalog(SEED_FIELDS, SEED_OUTPUTS);
    }

    /** 新增别名变量：启用与停用的流程条件变量列出，未声明流程用途或已删除的不列出。 */
    @Test
    void test_normalFlow_variableCatalogFilters() {
        String active = flows.mockInputAlias("Alias active", true, VariableStatus.ACTIVE);
        String disabled = flows.mockInputAlias("Alias disabled", true, VariableStatus.DISABLED);
        flows.mockInputAlias("Alias rule only", false, VariableStatus.ACTIVE);
        flows.mockInputAlias("Alias deleted", true, VariableStatus.DELETED);
        List<ExpectedField> expected = new ArrayList<>(SEED_FIELDS);
        expected.add(input("consumer_country_code", "Alias active", "STRING", "UPPER_ALPHA_2", active));
        expected.add(input("consumer_country_code", "Alias disabled", "STRING", "UPPER_ALPHA_2", disabled));
        expected.sort(Comparator.comparing(ExpectedField::variableCode));

        read("").assertCatalog(expected, SEED_OUTPUTS);
    }

    /** 合法格式但不受支持的决策点。 */
    @Test
    void test_noDataFound() {
        read("?pointCode=PAYOUT_CREATION").assertNotFound();
    }

    /** 查询串非法时在查询前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidQueries")
    void test_invalidParameter(String scenario, String query) {
        read(query).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 携带 Body 的 GET 请求。 */
    @Test
    void test_invalidParameter_getWithBody() {
        String requestId = nextRequestId();

        new FlowAuthoringContextResponseChecker(http.exchange(HttpMethod.GET, PATH, headers(requestId), "{}"),
            objectMapper, requestId).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 决策点停用时不可配置。 */
    @Test
    void test_businessRuleViolation_inactivePoint() {
        flows.deactivatePoint();

        read("").assertProblem(400, INVALID_REQUEST.getCode());
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(Arguments.of("pointCode 小写", "?pointCode=checkout_prepare"),
            Arguments.of("pointCode 数字开头", "?pointCode=1CHECKOUT"),
            Arguments.of("pointCode 为空值", "?pointCode="),
            Arguments.of("pointCode 重复", "?pointCode=CHECKOUT_PREPARE&pointCode=CHECKOUT_PREPARE"),
            Arguments.of("未知参数", "?pointCode=CHECKOUT_PREPARE&usage=FLOW_CONDITION"));
    }

    private static ExpectedField input(String fieldCode, String label, String valueType, String literalDomain,
        String variableCode) {
        return new ExpectedField(null, fieldCode, label, valueType, true,
            "NUMBER".equals(valueType) ? ORDERED_OPERATORS : TEXT_OPERATORS, literalDomain, variableCode);
    }

    /** 合同中可选的输入字段，如仅 PARTNER 来源才有的 Local Partner。 */
    private static ExpectedField optionalInput(String fieldCode, String label, String valueType, String literalDomain,
        String variableCode) {
        return new ExpectedField(null, fieldCode, label, valueType, false,
            "NUMBER".equals(valueType) ? ORDERED_OPERATORS : TEXT_OPERATORS, literalDomain, variableCode);
    }

    private FlowAuthoringContextResponseChecker read(String query) {
        HttpHeaders headers = headers(nextRequestId());
        return new FlowAuthoringContextResponseChecker(http.get(PATH + query, headers), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }

    /** Gateway 转发的 Operations 可信身份。 */
    private static HttpHeaders headers(String requestId) {
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Gateway-Actor-Subject", "70001");
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        return headers;
    }

    private static String nextRequestId() {
        return "risk-flow-authoring-" + UUID.randomUUID();
    }
}
