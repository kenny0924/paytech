package com.paytech.platform.support.test.base;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.platform.support.dataquery.integration.DataSourcePoolRegistry;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.dal.DataQueryTestMapper;
import com.paytech.platform.support.test.dataquery.DataQueryFixture;
import com.paytech.platform.support.test.dataquery.DataQueryMocker;

/**
 * 数据查询入口测试的公共调用与准备：被查库就绪检查、Vault DTO 空基线，以及管理与执行两类路径。
 * <li>每例在全局准备之后检查被查库 mor_data_query_fixture 可用，缺失时立即失败并给出准备方法。</li>
 */
public abstract class DataQueryApiTestBase extends OperationsApiTestBase {

    /** 管理接口前缀。 */
    protected static final String ADMIN = "/data-query-admin/v1";

    /** 执行接口前缀。 */
    protected static final String EXECUTION = "/data-query/v1";

    /** mock 配置中登记的执行调用方。 */
    protected static final String CALLER = "operations-console-bff";

    @Autowired
    protected DataQueryFixture fixture;

    @Autowired
    protected DataQueryMocker dataQuery;

    @Autowired
    protected DataQueryTestMapper dataQueryRows;

    @Autowired
    protected DataSourcePoolRegistry pools;

    /** 被查库就绪检查；Vault DTO 的空基线与意外调用检查在 MockTestBase。 */
    @BeforeEach
    protected final void setUpDataQueryMocks() {
        fixture.assertReady();
    }

    /** 以给定调用方执行模板。 */
    protected PlatformResponseChecker execute(String caller, Operator operator, String templateCode, Object body) {
        HttpHeaders headers = headers(operator);
        if (caller != null) {
            headers.set("X-Caller-Service", caller);
        }
        return call(HttpMethod.POST, EXECUTION + "/templates/" + templateCode + "/executions", headers, body);
    }

    /** 以登记的调用方执行模板。 */
    protected PlatformResponseChecker execute(Operator operator, String templateCode, Object body) {
        return execute(CALLER, operator, templateCode, body);
    }
}
