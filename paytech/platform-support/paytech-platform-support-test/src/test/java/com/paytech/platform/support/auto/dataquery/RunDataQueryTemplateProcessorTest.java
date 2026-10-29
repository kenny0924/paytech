package com.paytech.platform.support.auto.dataquery;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.api.enums.DataQueryErrorCode;
import com.paytech.platform.support.dataquery.core.enums.QueryPagingMode;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.dataquery.domain.DataQueryResult;
import com.paytech.platform.support.dataquery.service.processor.RunDataQueryTemplateProcessor;
import com.paytech.platform.support.dataquery.service.processor.request.RunDataQueryTemplateRequest;
import com.paytech.platform.support.test.base.DataQueryApiTestBase;
import com.paytech.platform.support.test.dataquery.DataQueryFixture;
import com.paytech.platform.support.test.dataquery.DataQueryMocker;

import tools.jackson.databind.node.JsonNodeFactory;

/**
 * data-query 模块对 Platform 其他模块的执行入口（REQ-05 technique 8.6）：无会话与调用方白名单，操作人可空，
 * 调用方超时上限收紧模板超时；模板不存在 404、停用 400；被查库数据不变。
 *
 * @see RunDataQueryTemplateProcessor#process(Object)
 */
class RunDataQueryTemplateProcessorTest extends DataQueryApiTestBase {

    @Autowired
    private RunDataQueryTemplateProcessor processor;

    private long dataSourceId;

    private Map<String, String> before;

    /** 本类公共：一个 ACTIVE 只读数据源，记录被查库测试前的校验和。 */
    @BeforeEach
    void setUpRunMocks() {
        dataSourceId = dataQuery.activeReaderSource("FIXTURE_RUN");
        before = fixture.snapshot();
    }

    /** 空操作人：__operator.accountId 为 null；参数照常绑定。 */
    @Test
    void test_nullOperator() {
        template("RUN_OPERATOR", "SELECT client_type, #{__operator.accountId} IS NULL AS no_operator"
            + " FROM t_client_fixture WHERE client_id = #{clientId}",
            "[{\"name\":\"clientId\",\"type\":\"INTEGER\",\"required\":true}]", 10, QueryTemplateStatus.ACTIVE);

        DataQueryResult result = processor.process(request("RUN_OPERATOR", 1001, null));

        assertAll(() -> assertEquals(List.of(List.of("ORGANIZATION", "1")), result.getRows()),
            () -> assertEquals(before, fixture.snapshot()));
    }

    /** 调用方上限 1 秒收紧模板的 8 秒超时：三表 CROSS JOIN 在上限内被取消。 */
    @Test
    void test_timeoutCap() {
        template("RUN_SLOW", "SELECT SUM(a.n + b.n + c.n) AS total FROM " + DataQueryFixture.NUMBERS + " a CROSS JOIN "
            + DataQueryFixture.NUMBERS + " b CROSS JOIN " + DataQueryFixture.NUMBERS + " c", "[]", 8,
            QueryTemplateStatus.ACTIVE);

        long startedAt = System.nanoTime();
        FailException timeout = assertThrows(FailException.class, () -> processor.process(request("RUN_SLOW", null, 1)));
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertAll(() -> assertEquals(DataQueryErrorCode.DATA_QUERY_TIMEOUT, timeout.getErrorCode()),
            () -> assertTrue(elapsedMillis < 5_000, "超时应由 1 秒上限触发，实际 " + elapsedMillis + "ms"));
    }

    /** 模板不存在 404；模板停用 400；超时上限超出 1—8 为参数错误。 */
    @Test
    void test_unavailableTemplate() {
        template("RUN_OFF", "SELECT 1 AS one", "[]", 5, QueryTemplateStatus.INACTIVE);

        FailException missing = assertThrows(FailException.class, () -> processor.process(request("RUN_NOPE", null,
            null)));
        assertThrows(BizCheckFailException.class, () -> processor.process(request("RUN_OFF", null, null)));
        assertThrows(com.paytech.foundation.common.error.InvalidParameterException.class,
            () -> processor.process(request("RUN_OFF", null, 9)));

        assertEquals(CommonErrorCode.NOT_FOUND, missing.getErrorCode());
    }

    /** 模块调用请求：操作人为空，调用方标签 workflow。 */
    private static RunDataQueryTemplateRequest request(String code, Integer clientId, Integer cap) {
        var params = JsonNodeFactory.instance.objectNode();
        if (clientId != null) {
            params.put("clientId", clientId);
        }
        return new RunDataQueryTemplateRequest(code, params, null, null, false, null, null, cap, "workflow");
    }

    /** 插入不分页模板。 */
    private void template(String code, String body, String parameters, int timeoutSeconds, QueryTemplateStatus status) {
        dataQuery.template(code, dataSourceId, body, parameters, null,
            DataQueryMocker.constraints(10, timeoutSeconds, QueryPagingMode.NONE, null, false), status);
    }
}
