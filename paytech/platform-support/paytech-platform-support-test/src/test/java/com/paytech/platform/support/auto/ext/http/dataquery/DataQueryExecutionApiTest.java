package com.paytech.platform.support.auto.ext.http.dataquery;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.dataquery.DataQueryExecutionApi;
import com.paytech.platform.support.api.dataquery.QueryTemplateManagementApi;
import com.paytech.platform.support.api.dataquery.request.ExecuteDataQueryRequestBody;
import com.paytech.platform.support.api.dataquery.request.TrialQueryTemplateRequestBody;
import com.paytech.platform.support.api.dataquery.response.DataQueryResultResponseBody;
import com.paytech.platform.support.api.dataquery.response.TrialQueryTemplateResponseBody;
import com.paytech.platform.support.api.enums.DataQueryErrorCode;
import com.paytech.platform.support.dataquery.core.enums.QueryPagingMode;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.test.base.DataQueryApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.dataquery.DataQueryFixture;
import com.paytech.platform.support.test.dataquery.DataQueryMocker;
import com.paytech.platform.support.test.dataquery.DataQueryRequestBuilder;

/**
 * 执行与试运行：三种分页与总数、行数上限与 hasMore、超时取消、响应大小上限、参数错误的字段路径、列屏蔽、
 * 非登记调用方拒绝、参数值注入作为绑定值、被查库数据在测试前后不变（AC-06）。
 *
 * @see DataQueryExecutionApi#executeDataQuery(String, String, String, String, ExecuteDataQueryRequestBody)
 * @see QueryTemplateManagementApi#trialQueryTemplate(String, String, TrialQueryTemplateRequestBody)
 */
class DataQueryExecutionApiTest extends DataQueryApiTestBase {

    private static final String REFUNDS = "SELECT refund_id, refund_no, client_id, refund_status, refund_amount,"
        + " currency, create_time FROM t_refund_fixture";

    private Operator admin;

    private long dataSourceId;

    private Map<String, String> before;

    /** 本类公共：平台管理员、一个 ACTIVE 只读数据源，并记录被查库测试前的校验和。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = operationsMocker.operator(OperatorBuilder.administrator());
        dataSourceId = dataQuery.activeReaderSource("FIXTURE_EXECUTION");
        before = fixture.snapshot();
    }

    /** 不分页：只返回 maxRows 行，多出时 hasMore；值按类型转换为字符串，时间不带时区。 */
    @Test
    void test_pagingNoneWithRowLimit() {
        template("REFUNDS_NONE", REFUNDS + " ORDER BY refund_id", "[]", null,
            DataQueryMocker.constraints(3, 10, QueryPagingMode.NONE, null, false));

        var result = execute(admin, "REFUNDS_NONE", new ExecuteDataQueryRequestBody()).assertOk()
            .as(DataQueryResultResponseBody.class);
        assertAll(() -> assertEquals(3, result.getRows().size()), () -> assertTrue(result.isHasMore()),
            () -> assertNull(result.getTotal()), () -> assertEquals(1, result.getVersionNo()),
            () -> assertEquals(List.of("2001", "FX-RF-2001", "1001", "SUCCEEDED", "10.00", "USD", "2026-10-05T09:00:00"),
                result.getRows().getFirst()));
    }

    /** 偏移分页：第 2 页 4 行与总数 10；不允许总数的模板拒绝 withTotal。 */
    @Test
    void test_pagingOffsetWithTotal() {
        template("REFUNDS_OFFSET", REFUNDS + " ORDER BY refund_id", "[]", null,
            DataQueryMocker.constraints(100, 10, QueryPagingMode.OFFSET, 4, true));
        template("REFUNDS_NO_COUNT", REFUNDS + " ORDER BY refund_id", "[]", null,
            DataQueryMocker.constraints(100, 10, QueryPagingMode.OFFSET, 4, false));

        ExecuteDataQueryRequestBody request = new ExecuteDataQueryRequestBody();
        request.setPage(2);
        request.setSize(4);
        request.setWithTotal(true);
        var page = execute(admin, "REFUNDS_OFFSET", request).assertOk().as(DataQueryResultResponseBody.class);
        assertAll(() -> assertEquals(4, page.getRows().size()), () -> assertEquals(10L, page.getTotal()),
            () -> assertTrue(page.isHasMore()), () -> assertEquals("2005", page.getRows().getFirst().getFirst()));

        execute(admin, "REFUNDS_NO_COUNT", request).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
    }

    /** 游标分页：模板自写 __cursor 条件，翻到最后一页 hasMore=false、无游标。 */
    @Test
    void test_pagingCursor() {
        template("REFUNDS_CURSOR", REFUNDS + " {{#if __cursor}}WHERE refund_id > #{__cursor.refund_id}{{/if}}"
            + " ORDER BY refund_id", "[]", null,
            DataQueryMocker.constraints(100, 10, QueryPagingMode.CURSOR, 4, false, "refund_id"));

        ExecuteDataQueryRequestBody request = new ExecuteDataQueryRequestBody();
        request.setSize(4);
        var first = execute(admin, "REFUNDS_CURSOR", request).assertOk().as(DataQueryResultResponseBody.class);
        request.setCursor(first.getNextCursor());
        var second = execute(admin, "REFUNDS_CURSOR", request).assertOk().as(DataQueryResultResponseBody.class);
        request.setCursor(second.getNextCursor());
        var last = execute(admin, "REFUNDS_CURSOR", request).assertOk().as(DataQueryResultResponseBody.class);

        assertAll(() -> assertNotNull(first.getNextCursor()), () -> assertEquals("2005", second.getRows().getFirst()
            .getFirst()), () -> assertEquals(2, last.getRows().size()), () -> assertFalse(last.isHasMore()),
            () -> assertNull(last.getNextCursor()));
    }

    /** 三表 CROSS JOIN 求和超过 1 秒被取消，返回 DATA_QUERY_TIMEOUT；不用 COUNT(*)，MySQL 会按行数相乘直接得出结果。 */
    @Test
    void test_timeoutCancelsQuery() {
        template("CROSS_JOIN_TIMEOUT", "SELECT SUM(a.n + b.n + c.n) AS total FROM " + DataQueryFixture.NUMBERS + " a CROSS JOIN "
            + DataQueryFixture.NUMBERS + " b CROSS JOIN " + DataQueryFixture.NUMBERS + " c", "[]", null,
            DataQueryMocker.constraints(10, 1, QueryPagingMode.NONE, null, false));

        execute(admin, "CROSS_JOIN_TIMEOUT", new ExecuteDataQueryRequestBody())
            .assertProblem(400, DataQueryErrorCode.DATA_QUERY_TIMEOUT);
    }

    /** 1 万行、每行 2000 字节超过 10MB 上限，返回 DATA_QUERY_RESULT_TOO_LARGE。 */
    @Test
    void test_responseTooLarge() {
        template("TOO_LARGE", "SELECT REPEAT('x', 2000) AS filler FROM " + DataQueryFixture.NUMBERS + " a CROSS JOIN "
            + DataQueryFixture.NUMBERS + " b", "[]", null,
            DataQueryMocker.constraints(10000, 8, QueryPagingMode.NONE, null, false));

        execute(admin, "TOO_LARGE", new ExecuteDataQueryRequestBody())
            .assertProblem(400, DataQueryErrorCode.DATA_QUERY_RESULT_TOO_LARGE);
    }

    /** 参数错误返回 422 与字段路径：对象集合字段类型不符、未声明参数、调用方传入系统参数。 */
    @Test
    void test_parameterFieldPath() {
        template("REFUNDS_CONDITIONS", REFUNDS + " {{#if conditions}}WHERE ({{#each conditions as c separator=\" OR \"}}"
            + "(currency = #{c.currency} AND refund_amount > #{c.minAmount}){{/each}}){{/if}} ORDER BY refund_id", "[{\"name\":\"conditions\",\"type\":\"LIST\",\"elementType\":\"OBJECT\","
            + "\"fields\":[{\"name\":\"currency\",\"type\":\"STRING\"},{\"name\":\"minAmount\",\"type\":\"DECIMAL\"}],"
            + "\"maxSize\":5}]", null, DataQueryMocker.constraints(100, 10, QueryPagingMode.NONE, null, false));

        execute(admin, "REFUNDS_CONDITIONS", params("{\"conditions\":[{\"currency\":\"USD\",\"minAmount\":\"abc\"}]}"))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("params.conditions[0].minAmount");
        execute(admin, "REFUNDS_CONDITIONS", params("{\"unknown\":1}"))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("params.unknown");
        execute(admin, "REFUNDS_CONDITIONS", params("{\"__operator\":{\"accountId\":1}}"))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("params.__operator");
        var matched = execute(admin, "REFUNDS_CONDITIONS",
            params("{\"conditions\":[{\"currency\":\"AED\",\"minAmount\":\"100\"}]}")).assertOk()
            .as(DataQueryResultResponseBody.class);
        assertEquals(List.of("2007"), matched.getRows().stream().map(row -> row.getFirst()).toList());
    }

    /** 列屏蔽：HIDE 不返回、FULL 全部掩码、KEEP_EDGES 保留首尾；标题使用声明的 label。 */
    @Test
    void test_columnMasking() {
        template("REFUNDS_MASKED", "SELECT refund_no, client_id, currency FROM t_refund_fixture ORDER BY refund_id", "[]",
            "[{\"name\":\"refund_no\",\"label\":\"退款号\",\"mask\":\"KEEP_EDGES\",\"keepStart\":2,\"keepEnd\":2},"
                + "{\"name\":\"client_id\",\"mask\":\"HIDE\",\"keepStart\":0,\"keepEnd\":0},"
                + "{\"name\":\"currency\",\"mask\":\"FULL\",\"keepStart\":0,\"keepEnd\":0}]",
            DataQueryMocker.constraints(1, 10, QueryPagingMode.NONE, null, false));

        var result = execute(admin, "REFUNDS_MASKED", new ExecuteDataQueryRequestBody()).assertOk()
            .as(DataQueryResultResponseBody.class);
        assertAll(() -> assertEquals(List.of("refund_no", "currency"),
                result.getColumns().stream().map(column -> column.getName()).toList()),
            () -> assertEquals("退款号", result.getColumns().getFirst().getLabel()),
            () -> assertEquals(List.of("FX******01", "***"), result.getRows().getFirst()));
    }

    /** 非登记调用方与缺少执行权限的人员被拒绝；不存在的模板 404，停用的模板 400。 */
    @Test
    void test_callerAndTemplateRejected() {
        template("REFUNDS_GUARDED", REFUNDS + " ORDER BY refund_id", "[]", null,
            DataQueryMocker.constraints(3, 10, QueryPagingMode.NONE, null, false));
        dataQuery.template("REFUNDS_OFF", dataSourceId, REFUNDS, "[]", null,
            DataQueryMocker.constraints(3, 10, QueryPagingMode.NONE, null, false), QueryTemplateStatus.INACTIVE);
        Operator reader = operationsMocker.operator(OperatorBuilder.administrator()
            .roles(operationsMocker.customRole("DATA_QUERY_READER", "DATA_QUERY_READ")));

        execute("other-service", admin, "REFUNDS_GUARDED", new ExecuteDataQueryRequestBody())
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        execute(reader, "REFUNDS_GUARDED", new ExecuteDataQueryRequestBody())
            .assertProblem(403, com.paytech.platform.support.api.enums.OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        execute(admin, "NO_SUCH_TEMPLATE", new ExecuteDataQueryRequestBody())
            .assertProblem(404, CommonErrorCode.NOT_FOUND);
        execute(admin, "REFUNDS_OFF", new ExecuteDataQueryRequestBody())
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
    }

    /** 注入尝试只作为绑定值，结果为空；写语句与 FOR UPDATE 的草稿被拒绝；被查库数据全程不变（AC-06）。 */
    @Test
    void test_injectionIsBoundAndDataUnchanged() {
        template("REFUND_BY_NO", REFUNDS + " WHERE refund_no = #{refundNo} ORDER BY refund_id",
            "[{\"name\":\"refundNo\",\"type\":\"STRING\",\"required\":true}]", null,
            DataQueryMocker.constraints(10, 10, QueryPagingMode.NONE, null, false));

        var injected = execute(admin, "REFUND_BY_NO", params("{\"refundNo\":\"' OR 1=1 --\"}")).assertOk()
            .as(DataQueryResultResponseBody.class);
        assertTrue(injected.getRows().isEmpty());

        for (String body : List.of("DELETE FROM t_refund_fixture", "SELECT * FROM t_refund_fixture FOR UPDATE",
            "SELECT 1; UPDATE t_refund_fixture SET refund_status = 'FAILED'",
            "SELECT * INTO OUTFILE '/tmp/x' FROM t_refund_fixture")) {
            post(ADMIN + "/template-trials", admin, trial(body)).assertProblem(422, CommonErrorCode.VALIDATION_ERROR);
        }
        var trial = post(ADMIN + "/template-trials", admin, trial(REFUNDS + " ORDER BY refund_id")).assertOk()
            .as(TrialQueryTemplateResponseBody.class);
        assertAll(() -> assertEquals(0, trial.getVersionNo()), () -> assertTrue(trial.getSql().contains("LIMIT 6")),
            () -> assertEquals(5, trial.getRows().size()));

        assertEquals(before, fixture.snapshot());
    }

    /** 插入 ACTIVE 模板。 */
    void template(String code, String body, String parameters, String columns, String constraints) {
        dataQuery.template(code, dataSourceId, body, parameters, columns, constraints, QueryTemplateStatus.ACTIVE);
    }

    /** 只带参数的执行请求。 */
    static ExecuteDataQueryRequestBody params(String json) {
        ExecuteDataQueryRequestBody request = new ExecuteDataQueryRequestBody();
        request.setParams(DataQueryRequestBuilder.json(json));
        return request;
    }

    /** 试运行草稿：不分页 5 行。 */
    TrialQueryTemplateRequestBody trial(String body) {
        TrialQueryTemplateRequestBody request = new TrialQueryTemplateRequestBody();
        request.setDataSourceId(Long.toString(dataSourceId));
        request.setBody(body);
        request.setParameters(DataQueryRequestBuilder.json("[]"));
        request.setConstraints(DataQueryRequestBuilder.none(5));
        return request;
    }
}
