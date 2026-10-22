package com.paytech.platform.support.dataquery.engine;

import java.util.Set;

import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import com.paytech.platform.support.dataquery.domain.ConnectionCheckResult;
import com.paytech.platform.support.dataquery.domain.DataSourceConnectionConfig;
import com.paytech.platform.support.dataquery.template.RenderedQuery;
import com.paytech.platform.support.dataquery.template.RenderedQueryCheck;
import com.paytech.platform.support.dataquery.template.ValueBinder;

/**
 * 查询引擎 SPI：值的输出方式、渲染结果的校验与改写、在数据源上执行，以及启用前的连接检查。
 *
 * <p>首期只有 {@link JdbcQueryEngine}（MySQL、Doris 方言）。以后的 ES / Mongo 实现自己的绑定器与执行器，模板语法与调用合同不变。</p>
 */
public interface QueryEngine {

    /**
     * 本引擎支持的数据源类型。
     *
     * @return 数据源类型
     */
    Set<DataSourceEngineType> engineTypes();

    /**
     * 值绑定器：语法树值节点的输出方式。
     *
     * @return 值绑定器
     */
    ValueBinder valueBinder();

    /**
     * 保存校验对每个展开组合调用的渲染结果检查，不访问数据源。
     *
     * @param source 数据源的引擎类型与默认库
     * @return 渲染结果检查
     */
    RenderedQueryCheck renderedQueryCheck(QuerySource source);

    /**
     * 渲染结果的只读校验与分页改写，不访问数据源。
     *
     * @param rendered 渲染结果
     * @param source 数据源的引擎类型与默认库，决定方言与未限定库名的处理
     * @param paging 已按模板约束与系统上限确定的分页参数
     * @return 可执行的查询
     * @throws com.paytech.foundation.common.error.BizCheckFailException 只读校验未通过
     */
    PreparedQuery prepare(RenderedQuery rendered, QuerySource source, QueryPaging paging);

    /**
     * 在数据源上执行，受超时、行数与响应大小限制；调用方在数据库事务外调用。
     *
     * @param source 已借出的数据源，调用方负责归还
     * @param query 可执行的查询
     * @param budget 超时与响应大小上限
     * @return 已转换值类型的原始结果（未屏蔽）
     * @throws com.paytech.foundation.common.error.FailException DATA_QUERY_TIMEOUT、DATA_QUERY_RESULT_TOO_LARGE
     * @throws com.paytech.foundation.common.error.ServiceUnavailableException DATA_SOURCE_UNAVAILABLE
     */
    QueryResult execute(DataSourceHandle source, PreparedQuery query, QueryBudget budget);

    /**
     * 启用前检查连通性与账号权限：单个连接（不用连接池）执行 SELECT 1，再检查授权；在数据库事务外调用。
     *
     * @param config 连接参数
     * @param password 密码原文，只用于本次连接
     * @return 检查结果；连接失败也作为结果返回
     */
    ConnectionCheckResult check(DataSourceConnectionConfig config, String password);
}
