package com.paytech.platform.support.test.dataquery;

import java.util.List;

import com.paytech.platform.support.api.dataquery.request.CreateDataSourceRequestBody;
import com.paytech.platform.support.api.dataquery.request.CreateQueryTemplateRequestBody;
import com.paytech.platform.support.api.dataquery.request.QueryTemplateColumn;
import com.paytech.platform.support.api.dataquery.request.QueryTemplateConstraints;
import com.paytech.platform.support.api.dataquery.request.UpdateDataSourceRequestBody;
import com.paytech.platform.support.api.dataquery.request.UpdateQueryTemplateRequestBody;
import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import com.paytech.platform.support.dataquery.core.enums.QueryPagingMode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 数据查询请求 Body 的合法默认值；只组装对象，不访问外部资源，负例由用例覆盖字段。 */
public final class DataQueryRequestBuilder {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 退款列表默认正文：可选 Client 条件，按退款 ID 排序。 */
    public static final String REFUND_BODY = """
        SELECT refund_id, refund_no, client_id, refund_status, refund_amount, currency, create_time
        FROM t_refund_fixture
        {{#join " AND " prefix="WHERE "}}
          {{#if clientId}}client_id = #{clientId}{{/if}}
        {{/join}}
        ORDER BY refund_id""";

    /** 默认正文的参数声明。 */
    public static final String REFUND_PARAMETERS = "[{\"name\":\"clientId\",\"type\":\"INTEGER\"}]";

    private DataQueryRequestBuilder() {
    }

    /** 以给定账号连接被查库的数据源新建请求。 */
    public static CreateDataSourceRequestBody createSource(DataQueryFixture fixture, String code, String username,
        String password) {
        CreateDataSourceRequestBody body = new CreateDataSourceRequestBody();
        body.setDataSourceCode(code);
        body.setDataSourceName(code + " 名称");
        body.setEngineType(DataSourceEngineType.MYSQL.getCode());
        body.setHost(fixture.host());
        body.setPort(fixture.port());
        body.setDatabaseName(DataQueryFixture.DATABASE);
        body.setUsername(username);
        body.setPassword(password);
        body.setMaxPoolSize(2);
        return body;
    }

    /** 修改请求：复制连接参数，密码留空表示保留。 */
    public static UpdateDataSourceRequestBody updateSource(DataQueryFixture fixture, String username, int maxPoolSize,
        int expectedVersion) {
        UpdateDataSourceRequestBody body = new UpdateDataSourceRequestBody();
        body.setDataSourceName("修改后的名称");
        body.setEngineType(DataSourceEngineType.MYSQL.getCode());
        body.setHost(fixture.host());
        body.setPort(fixture.port());
        body.setDatabaseName(DataQueryFixture.DATABASE);
        body.setUsername(username);
        body.setMaxPoolSize(maxPoolSize);
        body.setExpectedVersion(expectedVersion);
        return body;
    }

    /** 不分页约束。 */
    public static QueryTemplateConstraints none(int maxRows) {
        QueryTemplateConstraints constraints = new QueryTemplateConstraints();
        constraints.setMaxRows(maxRows);
        constraints.setTimeoutSeconds(5);
        constraints.setPagingMode(QueryPagingMode.NONE.getCode());
        return constraints;
    }

    /** 模板新建请求。 */
    public static CreateQueryTemplateRequestBody createTemplate(String code, long dataSourceId, String body,
        String parameters, QueryTemplateConstraints constraints) {
        CreateQueryTemplateRequestBody request = new CreateQueryTemplateRequestBody();
        request.setTemplateCode(code);
        request.setTemplateName(code + " 名称");
        request.setDataSourceId(Long.toString(dataSourceId));
        request.setBody(body);
        request.setParameters(json(parameters));
        request.setConstraints(constraints);
        return request;
    }

    /** 模板修改请求。 */
    public static UpdateQueryTemplateRequestBody updateTemplate(long dataSourceId, String body, String parameters,
        QueryTemplateConstraints constraints, String changeNote, int expectedVersion) {
        UpdateQueryTemplateRequestBody request = new UpdateQueryTemplateRequestBody();
        request.setTemplateName("修改后的模板");
        request.setDataSourceId(Long.toString(dataSourceId));
        request.setBody(body);
        request.setParameters(json(parameters));
        request.setConstraints(constraints);
        request.setChangeNote(changeNote);
        request.setExpectedVersion(expectedVersion);
        return request;
    }

    /** 列声明。 */
    public static QueryTemplateColumn column(String name, String label, String mask, Integer keepStart,
        Integer keepEnd) {
        QueryTemplateColumn column = new QueryTemplateColumn();
        column.setName(name);
        column.setLabel(label);
        column.setMask(mask);
        column.setKeepStart(keepStart);
        column.setKeepEnd(keepEnd);
        return column;
    }

    /** 列声明列表。 */
    public static List<QueryTemplateColumn> columns(QueryTemplateColumn... columns) {
        return List.of(columns);
    }

    /** 解析 JSON 文本。 */
    public static JsonNode json(String text) {
        return text == null ? null : JSON.readTree(text);
    }
}
