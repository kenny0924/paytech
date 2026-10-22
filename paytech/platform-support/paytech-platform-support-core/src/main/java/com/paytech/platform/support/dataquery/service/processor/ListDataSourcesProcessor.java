package com.paytech.platform.support.dataquery.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.dataquery.domain.DataSourceDetail;
import com.paytech.platform.support.dataquery.domainservice.service.DataSourceService;
import com.paytech.platform.support.dataquery.service.processor.request.ListDataSourcesRequest;

import lombok.AllArgsConstructor;

/** 分页查询数据源：复核 DATA_QUERY_READ 后读取，不开事务。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListDataSourcesProcessor
    extends AbstractProcessor<ListDataSourcesRequest, PagingResponseBody<DataSourceDetail>> {

    private final DataSourceService dataSources;
    private final DataQueryManagementSupport support;

    @Override
    protected PagingResponseBody<DataSourceDetail> processRequest(ListDataSourcesRequest request) {
        support.requirePermission(request.getActor(), request.getSession(), DataQueryManagementSupport.READ_PERMISSION);
        return dataSources.listDataSources(request.getStatus(), request.getEngineType(), request.getKeyword(),
            request.getPaging());
    }
}
