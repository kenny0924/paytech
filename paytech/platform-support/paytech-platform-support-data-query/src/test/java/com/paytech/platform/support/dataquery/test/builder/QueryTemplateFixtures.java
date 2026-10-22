package com.paytech.platform.support.dataquery.test.builder;

import java.util.List;

import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import com.paytech.platform.support.dataquery.engine.QuerySource;
import com.paytech.platform.support.dataquery.template.ParameterDeclarationReader;
import com.paytech.platform.support.dataquery.template.QueryParameterDeclaration;
import com.paytech.platform.support.dataquery.template.QueryTemplateEngine;
import com.paytech.platform.support.dataquery.template.TemplateLimits;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 模板测试的公共样例：需求第 7 节示例一的正文与参数声明、默认上限与数据源。 */
public final class QueryTemplateFixtures {

    /** 测试用 JSON 读写。 */
    public static final JsonMapper JSON = JsonMapper.builder().build();

    /** 默认上限：正文 64 KB、集合 1000、展开组合 256。 */
    public static final TemplateLimits LIMITS = new TemplateLimits(64 * 1024, 1000, 256);

    /** 指定了默认库的 MySQL 数据源。 */
    public static final QuerySource COMMERCE_SOURCE = new QuerySource(DataSourceEngineType.MYSQL, "mor_commerce");

    /** 连接整个实例的 MySQL 数据源。 */
    public static final QuerySource WHOLE_INSTANCE_SOURCE = new QuerySource(DataSourceEngineType.MYSQL, null);

    /** 需求第 7 节示例一。 */
    public static final String ORDER_BODY = """
        SELECT order_id, client_id, amount, status, created_at
        FROM t_order
        {{#join " AND " prefix="WHERE "}}
          {{#if clientId}}client_id = #{clientId}{{/if}}
          {{#if statuses}}status IN (#{statuses}){{/if}}
          {{#if from}}created_at >= #{from}{{/if}}
          {{#if conditions}}({{#each conditions as c separator=" OR "}}(channel = #{c.channel} AND amount > #{c.minAmount}){{/each}}){{/if}}
        {{/join}}
        ORDER BY @{sortBy}""";

    /** 技术方案第 3.2 节的参数声明。 */
    public static final String ORDER_PARAMETERS = """
        [
          {"name":"clientId","type":"INTEGER","required":false},
          {"name":"statuses","type":"LIST","elementType":"ENUM","allowedValues":["PAID","REFUNDED"],"maxSize":20},
          {"name":"conditions","type":"LIST","elementType":"OBJECT",
           "fields":[{"name":"channel","type":"STRING","maxLength":32},{"name":"minAmount","type":"DECIMAL"}],"maxSize":10},
          {"name":"from","type":"DATETIME"},
          {"name":"sortBy","type":"FRAGMENT","required":true,"default":"NEWEST",
           "choices":{"NEWEST":"created_at DESC","OLDEST":"created_at ASC"}}
        ]""";

    /** 工具类，不实例化。 */
    private QueryTemplateFixtures() {
    }

    /**
     * 默认上限的模板引擎。
     *
     * @return 模板引擎
     */
    public static QueryTemplateEngine engine() {
        return new QueryTemplateEngine(LIMITS);
    }

    /**
     * 解析 JSON 文本。
     *
     * @param json JSON 文本
     * @return JSON 节点
     */
    public static JsonNode json(String json) {
        return JSON.readTree(json);
    }

    /**
     * 读取参数声明 JSON。
     *
     * @param json parameters_json 文本
     * @return 参数声明
     */
    public static List<QueryParameterDeclaration> declarations(String json) {
        return new ParameterDeclarationReader().read(json(json));
    }
}
