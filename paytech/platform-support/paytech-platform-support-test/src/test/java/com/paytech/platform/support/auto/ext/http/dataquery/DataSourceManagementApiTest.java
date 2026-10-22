package com.paytech.platform.support.auto.ext.http.dataquery;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.dataquery.DataSourceManagementApi;
import com.paytech.platform.support.api.dataquery.request.ChangeDataSourceStatusRequestBody;
import com.paytech.platform.support.api.dataquery.request.CreateDataSourceRequestBody;
import com.paytech.platform.support.api.dataquery.request.TestDataSourceConnectionRequestBody;
import com.paytech.platform.support.api.dataquery.request.UpdateDataSourceRequestBody;
import com.paytech.platform.support.api.dataquery.response.DataSourceConnectionTestResponseBody;
import com.paytech.platform.support.api.dataquery.response.DataSourceResponseBody;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.dataquery.core.enums.DataSourceCheckResult;
import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;
import com.paytech.platform.support.dataquery.core.enums.QueryPagingMode;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.dataquery.domainservice.service.DataSourceService;
import com.paytech.platform.support.dataquery.engine.DataSourceHandle;
import com.paytech.platform.support.test.base.DataQueryApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.dataquery.DataQueryMocker;
import com.paytech.platform.support.test.dataquery.DataQueryRequestBuilder;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * 数据源维护：新建、测试连接、启用；写权限账号拒绝启用；修改后不重启即生效；停用时进行中的查询正常结束；
 * 有 ACTIVE 模板引用时不能删除；版本冲突 409；驱动参数白名单与权限复核。
 * <p>角色授权（GRANT `role`）拒绝启用由 data-query 模块的 {@code ConnectionPrivilegeCheckTest} 覆盖：本地被查库没有带角色的账号。
 *
 * @see DataSourceManagementApi#createDataSource(String, String, CreateDataSourceRequestBody)
 * @see DataSourceManagementApi#updateDataSource(String, String, String, UpdateDataSourceRequestBody)
 * @see DataSourceManagementApi#testDataSourceConnection(String, String, TestDataSourceConnectionRequestBody)
 * @see DataSourceManagementApi#changeDataSourceStatus(String, String, String, ChangeDataSourceStatusRequestBody)
 * @see DataSourceManagementApi#deleteDataSource(String, String, String, Integer)
 * @see DataSourceManagementApi#getDataSource(String, String, String)
 */
class DataSourceManagementApiTest extends DataQueryApiTestBase {

    private static final String SOURCES = ADMIN + "/data-sources";

    @Autowired
    DataSourceService dataSourceService;

    private Operator admin;

    /** 本类公共：持有全部目录权限的平台管理员。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = operationsMocker.operator(OperatorBuilder.administrator());
    }

    /** 新建为 INACTIVE v1、密码只存 Token；测试连接通过并回写；启用后 v2、本地建立 v2 连接池；详情不回显密码。 */
    @Test
    void test_createTestAndActivate() {
        var created = post(SOURCES, admin, DataQueryRequestBuilder.createSource(fixture, "FIXTURE_READER",
            fixture.readerUsername(), fixture.readerPassword())).assertOk().as(ConfigurationMutationResponseBody.class);
        long id = Long.parseLong(created.getObjectId());
        Map<String, Object> row = dataQueryRows.dataSource(id);
        assertAll(() -> assertEquals(DataSourceStatus.INACTIVE.getCode(), created.getStatus()),
            () -> assertEquals(1, created.getVersion()),
            () -> assertEquals(fixture.readerPassword(), passwordVault().password((String) row.get("passwordToken"))),
            () -> assertNull(pools.poolVersion(id)));

        var test = post(ADMIN + "/data-source-connection-tests", admin, connectionTest(id, fixture.readerUsername()))
            .assertOk().as(DataSourceConnectionTestResponseBody.class);
        assertAll(() -> assertEquals(DataSourceCheckResult.PASSED.getCode(), test.getResult()),
            () -> assertTrue(test.isConnected()), () -> assertTrue(test.getViolations().isEmpty()),
            () -> assertEquals(DataSourceCheckResult.PASSED.getCode(), dataQueryRows.dataSource(id)
                .get("lastCheckResult")));

        var activated = post(SOURCES + "/" + id + "/status", admin, status(DataSourceStatus.ACTIVE, 1)).assertOk()
            .as(ConfigurationMutationResponseBody.class);
        assertAll(() -> assertEquals(DataSourceStatus.ACTIVE.getCode(), activated.getStatus()),
            () -> assertEquals(2, activated.getVersion()), () -> assertEquals(2, pools.poolVersion(id)));

        var detail = get(SOURCES + "/" + id, admin).assertOk();
        assertAll(() -> assertTrue(detail.json().path("password").isMissingNode()),
            () -> assertTrue(detail.json().path("passwordToken").isMissingNode()),
            () -> assertEquals(fixture.readerUsername(), detail.as(DataSourceResponseBody.class).getUsername()));
    }

    /** 带写权限的账号：测试连接列出 INSERT/UPDATE 违规；启用返回 400 且状态不变，最后一次检查记为 FAILED。 */
    @Test
    void test_writerAccountRejected() {
        long id = dataQuery.source("FIXTURE_WRITER", fixture.writerUsername(), fixture.writerPassword(),
            "mor_data_query_fixture", DataSourceStatus.INACTIVE);

        var test = post(ADMIN + "/data-source-connection-tests", admin, connectionTest(id, fixture.writerUsername()))
            .assertOk().as(DataSourceConnectionTestResponseBody.class);
        assertAll(() -> assertEquals(DataSourceCheckResult.FAILED.getCode(), test.getResult()),
            () -> assertTrue(test.getViolations().stream().anyMatch(reason -> reason.contains("INSERT"))),
            () -> assertTrue(test.getViolations().stream().anyMatch(reason -> reason.contains("UPDATE"))));

        var rejected = post(SOURCES + "/" + id + "/status", admin, status(DataSourceStatus.ACTIVE, 1))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        Map<String, Object> row = dataQueryRows.dataSource(id);
        assertAll(() -> assertTrue(rejected.json().path("detail").asString().contains("INSERT")),
            () -> assertEquals(DataSourceStatus.INACTIVE.getCode(), row.get("status")),
            () -> assertEquals(1, ((Number) row.get("version")).intValue()),
            () -> assertEquals(DataSourceCheckResult.FAILED.getCode(), row.get("lastCheckResult")),
            () -> assertNull(pools.poolVersion(id)));
    }

    /** ACTIVE 数据源修改后版本加一，本实例提交后即切到新版本连接池；其他实例的定时刷新也收敛到同一版本（AC-01）。 */
    @Test
    void test_updateTakesEffectWithoutRestart() {
        long id = dataQuery.activeReaderSource("FIXTURE_ACTIVE");
        long template = dataQuery.template("REFUNDS_ACTIVE", id, "SELECT refund_id FROM t_refund_fixture ORDER BY refund_id",
            "[]", null, DataQueryMocker.constraints(5, 10, QueryPagingMode.NONE, null, false), QueryTemplateStatus.ACTIVE);
        execute(admin, "REFUNDS_ACTIVE", Map.of()).assertOk();
        assertEquals(1, pools.poolVersion(id));

        var updated = put(SOURCES + "/" + id, admin, DataQueryRequestBuilder.updateSource(fixture,
            fixture.readerUsername(), 3, 1)).assertOk().as(ConfigurationMutationResponseBody.class);
        assertAll(() -> assertEquals(2, updated.getVersion()), () -> assertEquals(2, pools.poolVersion(id)),
            () -> assertEquals(3, ((Number) dataQueryRows.dataSource(id).get("maxPoolSize")).intValue()));

        // 其他实例：注册表清空后的定时刷新按数据库版本重建。
        pools.destroy();
        pools.refresh();
        assertEquals(2, pools.poolVersion(id));
        execute(admin, "REFUNDS_ACTIVE", Map.of()).assertOk();
        assertTrue(template > 0);
    }

    /** 停用后新查询被拒绝；停用前借出的连接仍可完成查询，归还后连接池关闭（AC-03）。 */
    @Test
    void test_deactivateWhileQueryInFlight() throws Exception {
        long id = dataQuery.activeReaderSource("FIXTURE_INFLIGHT");
        dataQuery.template("REFUNDS_INFLIGHT", id, "SELECT refund_id FROM t_refund_fixture ORDER BY refund_id", "[]",
            null, DataQueryMocker.constraints(5, 10, QueryPagingMode.NONE, null, false), QueryTemplateStatus.ACTIVE);

        try (DataSourceHandle inFlight = pools.lease(dataSourceService.findDataSource(id))) {
            post(SOURCES + "/" + id + "/status", admin, status(DataSourceStatus.INACTIVE, 1)).assertOk();
            assertNull(pools.poolVersion(id));
            try (Connection connection = inFlight.connection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM t_refund_fixture")) {
                rows.next();
                assertEquals(10, rows.getInt(1));
            }
        }
        execute(admin, "REFUNDS_INFLIGHT", Map.of()).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
    }

    /** 被 ACTIVE 模板引用时不能删除，消息列出模板编码；停用模板后可删除，状态 DELETED 且编码不能复用。 */
    @Test
    void test_deleteRejectedWhileReferenced() {
        long id = dataQuery.source("FIXTURE_DELETE", fixture.readerUsername(), fixture.readerPassword(),
            "mor_data_query_fixture", DataSourceStatus.INACTIVE);
        long template = dataQuery.template("REFERENCING_TEMPLATE", id, "SELECT 1", "[]", null,
            DataQueryMocker.constraints(5, 10, QueryPagingMode.NONE, null, false), QueryTemplateStatus.ACTIVE);

        var rejected = delete(SOURCES + "/" + id + "?expectedVersion=1", admin)
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        assertTrue(rejected.json().path("detail").asString().contains("REFERENCING_TEMPLATE"));
        assertEquals(DataSourceStatus.INACTIVE.getCode(), dataQueryRows.dataSource(id).get("status"));

        post(ADMIN + "/templates/" + template + "/status", admin, templateStatus(QueryTemplateStatus.INACTIVE, 1))
            .assertOk();
        var deleted = delete(SOURCES + "/" + id + "?expectedVersion=1", admin).assertOk()
            .as(ConfigurationMutationResponseBody.class);
        assertEquals(DataSourceStatus.DELETED.getCode(), deleted.getStatus());
        post(SOURCES, admin, DataQueryRequestBuilder.createSource(fixture, "FIXTURE_DELETE", fixture.readerUsername(),
            fixture.readerPassword())).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
    }

    /** 原版本不符返回 409，数据不变。 */
    @Test
    void test_versionConflict() {
        long id = dataQuery.source("FIXTURE_CONFLICT", fixture.readerUsername(), fixture.readerPassword(),
            "mor_data_query_fixture", DataSourceStatus.INACTIVE);

        put(SOURCES + "/" + id, admin, DataQueryRequestBuilder.updateSource(fixture, fixture.readerUsername(), 2, 7))
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        post(SOURCES + "/" + id + "/status", admin, status(DataSourceStatus.ACTIVE, 7))
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        assertEquals(1, ((Number) dataQueryRows.dataSource(id).get("version")).intValue());
    }

    /** 驱动参数不在白名单、连接池超过上限、编码格式错误时拒绝，不调用 Vault、不写库。 */
    @Test
    void test_invalidParameter() {
        var unsafe = DataQueryRequestBuilder.createSource(fixture, "FIXTURE_UNSAFE", fixture.readerUsername(),
            fixture.readerPassword());
        unsafe.setConnectionParams(Map.of("allowLoadLocalInfile", "true"));
        post(SOURCES, admin, unsafe).assertProblem(400, CommonErrorCode.INVALID_REQUEST);

        var oversized = DataQueryRequestBuilder.createSource(fixture, "FIXTURE_OVERSIZED", fixture.readerUsername(),
            fixture.readerPassword());
        oversized.setMaxPoolSize(11);
        post(SOURCES, admin, oversized).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        var badCode = DataQueryRequestBuilder.createSource(fixture, "lower", fixture.readerUsername(),
            fixture.readerPassword());
        post(SOURCES, admin, badCode).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("dataSourceCode");

        assertAll(() -> assertEquals(0, passwordVault().protectCount()),
            () -> assertNull(dataQueryRows.dataSourceId("FIXTURE_UNSAFE")),
            () -> assertNull(dataQueryRows.dataSourceId("FIXTURE_OVERSIZED")));
    }

    /** 只读人员不能维护数据源，也不会触发 Vault。 */
    @Test
    void test_permissionDenied() {
        Operator viewer = operationsMocker.operator(OperatorBuilder.viewer());

        post(SOURCES, viewer, DataQueryRequestBuilder.createSource(fixture, "FIXTURE_DENIED", fixture.readerUsername(),
            fixture.readerPassword())).assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        get(SOURCES, viewer).assertOk();
        assertEquals(0, passwordVault().protectCount());
    }

    /** 测试连接请求：带已保存数据源 ID，不传密码。 */
    TestDataSourceConnectionRequestBody connectionTest(long dataSourceId, String username) {
        TestDataSourceConnectionRequestBody body = new TestDataSourceConnectionRequestBody();
        body.setDataSourceId(Long.toString(dataSourceId));
        body.setEngineType(DataSourceEngineType.MYSQL.getCode());
        body.setHost(fixture.host());
        body.setPort(fixture.port());
        body.setDatabaseName("mor_data_query_fixture");
        body.setUsername(username);
        return body;
    }

    /** 数据源启停请求。 */
    static ChangeDataSourceStatusRequestBody status(DataSourceStatus status, int expectedVersion) {
        ChangeDataSourceStatusRequestBody body = new ChangeDataSourceStatusRequestBody();
        body.setStatus(status.getCode());
        body.setExpectedVersion(expectedVersion);
        return body;
    }

    /** 模板启停请求。 */
    static com.paytech.platform.support.api.dataquery.request.ChangeQueryTemplateStatusRequestBody templateStatus(
        QueryTemplateStatus status, int expectedVersion) {
        var body = new com.paytech.platform.support.api.dataquery.request.ChangeQueryTemplateStatusRequestBody();
        body.setStatus(status.getCode());
        body.setExpectedVersion(expectedVersion);
        return body;
    }

    /** 全局基类中的数据源密码 Vault Mock。 */
    com.paytech.platform.support.test.dataquery.DataSourcePasswordVaultMock passwordVault() {
        return dataSourcePasswordVault;
    }
}
