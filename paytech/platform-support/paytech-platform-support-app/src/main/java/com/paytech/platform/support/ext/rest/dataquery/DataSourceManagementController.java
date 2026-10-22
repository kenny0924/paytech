package com.paytech.platform.support.ext.rest.dataquery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.dataquery.DataSourceManagementApi;
import com.paytech.platform.support.api.dataquery.request.ChangeDataSourceStatusRequestBody;
import com.paytech.platform.support.api.dataquery.request.CreateDataSourceRequestBody;
import com.paytech.platform.support.api.dataquery.request.TestDataSourceConnectionRequestBody;
import com.paytech.platform.support.api.dataquery.request.UpdateDataSourceRequestBody;
import com.paytech.platform.support.api.dataquery.response.DataSourceConnectionTestResponseBody;
import com.paytech.platform.support.api.dataquery.response.DataSourceResponseBody;
import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;
import com.paytech.platform.support.dataquery.service.processor.ChangeDataSourceStatusProcessor;
import com.paytech.platform.support.dataquery.service.processor.CreateDataSourceProcessor;
import com.paytech.platform.support.dataquery.service.processor.DeleteDataSourceProcessor;
import com.paytech.platform.support.dataquery.service.processor.GetDataSourceProcessor;
import com.paytech.platform.support.dataquery.service.processor.ListDataSourcesProcessor;
import com.paytech.platform.support.dataquery.service.processor.TestDataSourceConnectionProcessor;
import com.paytech.platform.support.dataquery.service.processor.UpdateDataSourceProcessor;
import com.paytech.platform.support.dataquery.service.processor.request.ChangeDataSourceStatusRequest;
import com.paytech.platform.support.dataquery.service.processor.request.CreateDataSourceRequest;
import com.paytech.platform.support.dataquery.service.processor.request.GetDataSourceRequest;
import com.paytech.platform.support.dataquery.service.processor.request.ListDataSourcesRequest;
import com.paytech.platform.support.dataquery.service.processor.request.TestDataSourceConnectionRequest;
import com.paytech.platform.support.dataquery.service.processor.request.UpdateDataSourceRequest;
import com.paytech.platform.support.ext.rest.audit.Audit;

import lombok.AllArgsConstructor;

/** 数据源维护的协议适配；权限、事务、Vault 与连接检查委托 dataquery 的 Processor。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class DataSourceManagementController implements DataSourceManagementApi {

    private final DataQueryConverter converter;
    private final GetDataSourceProcessor getProcessor;
    private final ListDataSourcesProcessor listProcessor;
    private final CreateDataSourceProcessor createProcessor;
    private final UpdateDataSourceProcessor updateProcessor;
    private final DeleteDataSourceProcessor deleteProcessor;
    private final ChangeDataSourceStatusProcessor statusProcessor;
    private final TestDataSourceConnectionProcessor testProcessor;

    /** 解析筛选与分页后查询。 */
    @Override
    public PagingResponseBody<DataSourceResponseBody> listDataSources(String actor, String session, String status,
        String engineType, String keyword, Integer page, Integer size) {
        var request = new ListDataSourcesRequest(actor, session,
            converter.optionalCode(DataSourceStatus.class, status, "status"),
            converter.optionalCode(DataSourceEngineType.class, engineType, "engineType"), DataQueryConverter.text(keyword),
            converter.paging(page, size));
        return listProcessor.process(request).map(converter::dataSource);
    }

    /** 解析 ID 后查询。 */
    @Override
    public DataSourceResponseBody getDataSource(String actor, String session, String dataSourceId) {
        return converter.dataSource(getProcessor.process(new GetDataSourceRequest(actor, session,
            converter.id(dataSourceId, "dataSourceId"))));
    }

    /** 转换请求后委托新建。 */
    @Audit(action = "OPERATIONS_DATA_SOURCE_CREATE", resourceType = "DATA_SOURCE",
        fallbackResourceId = "DATA_SOURCE_CATALOG")
    @Override
    public ConfigurationMutationResponseBody createDataSource(String actor, String session,
        CreateDataSourceRequestBody request) {
        return converter.mutation(createProcessor.process(new CreateDataSourceRequest(actor, session,
            request.getDataSourceCode(), DataQueryConverter.text(request.getDataSourceName()),
            converter.connection(request.getEngineType(), request.getHost(), request.getPort(), request.getDatabaseName(),
                request.getUsername(), request.getConnectionParams()),
            request.getPassword(), request.getMaxPoolSize(), DataQueryConverter.text(request.getDescription()))));
    }

    /** 转换请求后委托修改；空密码表示保留原密码。 */
    @Audit(action = "OPERATIONS_DATA_SOURCE_UPDATE", resourceType = "DATA_SOURCE", resourceIdParameter = "dataSourceId",
        fallbackResourceId = "DATA_SOURCE_CATALOG")
    @Override
    public ConfigurationMutationResponseBody updateDataSource(String actor, String session, String dataSourceId,
        UpdateDataSourceRequestBody request) {
        return converter.mutation(updateProcessor.process(new UpdateDataSourceRequest(actor, session,
            converter.id(dataSourceId, "dataSourceId"), request.getExpectedVersion(),
            DataQueryConverter.text(request.getDataSourceName()),
            converter.connection(request.getEngineType(), request.getHost(), request.getPort(), request.getDatabaseName(),
                request.getUsername(), request.getConnectionParams()),
            request.getPassword() == null || request.getPassword().isEmpty() ? null : request.getPassword(),
            request.getMaxPoolSize(), DataQueryConverter.text(request.getDescription()))));
    }

    /** 转换请求后委托连接测试。 */
    @Audit(action = "OPERATIONS_DATA_SOURCE_CONNECTION_TEST", resourceType = "DATA_SOURCE",
        fallbackResourceId = "DATA_SOURCE_CATALOG")
    @Override
    public DataSourceConnectionTestResponseBody testDataSourceConnection(String actor, String session,
        TestDataSourceConnectionRequestBody request) {
        var result = testProcessor.process(new TestDataSourceConnectionRequest(actor, session,
            converter.optionalId(request.getDataSourceId(), "dataSourceId"),
            converter.connection(request.getEngineType(), request.getHost(), request.getPort(), request.getDatabaseName(),
                request.getUsername(), request.getConnectionParams()),
            request.getPassword() == null || request.getPassword().isEmpty() ? null : request.getPassword()));
        return converter.connectionTest(result.getCheck(), result.getCheckedAt());
    }

    /** 转换目标状态后委托启停。 */
    @Audit(action = "OPERATIONS_DATA_SOURCE_STATUS", resourceType = "DATA_SOURCE", resourceIdParameter = "dataSourceId",
        fallbackResourceId = "DATA_SOURCE_CATALOG")
    @Override
    public ConfigurationMutationResponseBody changeDataSourceStatus(String actor, String session, String dataSourceId,
        ChangeDataSourceStatusRequestBody request) {
        return converter.mutation(statusProcessor.process(new ChangeDataSourceStatusRequest(actor, session,
            converter.id(dataSourceId, "dataSourceId"), request.getExpectedVersion(),
            converter.code(DataSourceStatus.class, request.getStatus(), "status"))));
    }

    /** 委托删除。 */
    @Audit(action = "OPERATIONS_DATA_SOURCE_DELETE", resourceType = "DATA_SOURCE", resourceIdParameter = "dataSourceId",
        fallbackResourceId = "DATA_SOURCE_CATALOG")
    @Override
    public ConfigurationMutationResponseBody deleteDataSource(String actor, String session, String dataSourceId,
        Integer expectedVersion) {
        return converter.mutation(deleteProcessor.process(new ChangeDataSourceStatusRequest(actor, session,
            converter.id(dataSourceId, "dataSourceId"), expectedVersion == null ? 0 : expectedVersion,
            DataSourceStatus.DELETED)));
    }
}
