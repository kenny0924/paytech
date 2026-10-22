package com.paytech.platform.support.dataquery.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.dataquery.domain.DataSourceDetail;
import com.paytech.platform.support.dataquery.domainservice.service.DataSourceService;
import com.paytech.platform.support.dataquery.service.processor.request.GetDataSourceRequest;

import lombok.AllArgsConstructor;

/** 数据源详情：复核 DATA_QUERY_READ 后读取，不存在返回 404；含 DELETED。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetDataSourceProcessor extends AbstractProcessor<GetDataSourceRequest, DataSourceDetail> {

    private final DataSourceService dataSources;
    private final DataQueryManagementSupport support;

    @Override
    protected DataSourceDetail processRequest(GetDataSourceRequest request) {
        support.requirePermission(request.getActor(), request.getSession(), DataQueryManagementSupport.READ_PERMISSION);
        return DataQueryManagementSupport.requireFound(dataSources.findDataSource(request.getDataSourceId()));
    }
}
