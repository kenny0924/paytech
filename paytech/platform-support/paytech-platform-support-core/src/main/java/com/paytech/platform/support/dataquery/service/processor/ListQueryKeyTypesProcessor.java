package com.paytech.platform.support.dataquery.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeDetail;
import com.paytech.platform.support.dataquery.domainservice.service.QueryKeyTypeService;
import com.paytech.platform.support.dataquery.service.processor.request.ListQueryKeyTypesRequest;

import lombok.AllArgsConstructor;

/** 分页查询键类型：复核 DATA_QUERY_READ 后读取，每项附 ACTIVE 模板当前版本的引用数，不开事务。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListQueryKeyTypesProcessor
    extends AbstractProcessor<ListQueryKeyTypesRequest, PagingResponseBody<QueryKeyTypeDetail>> {

    private final QueryKeyTypeService keyTypes;
    private final DataQueryManagementSupport support;

    @Override
    protected PagingResponseBody<QueryKeyTypeDetail> processRequest(ListQueryKeyTypesRequest request) {
        support.requirePermission(request.getActor(), request.getSession(), DataQueryManagementSupport.READ_PERMISSION);
        return keyTypes.listQueryKeyTypes(request.getStatus(), request.getKeyword(), request.getPaging());
    }
}
