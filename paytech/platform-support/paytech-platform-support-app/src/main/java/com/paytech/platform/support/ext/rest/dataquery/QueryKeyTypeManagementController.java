package com.paytech.platform.support.ext.rest.dataquery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.dataquery.QueryKeyTypeManagementApi;
import com.paytech.platform.support.api.dataquery.request.ChangeQueryKeyTypeStatusRequestBody;
import com.paytech.platform.support.api.dataquery.request.CreateQueryKeyTypeRequestBody;
import com.paytech.platform.support.api.dataquery.request.ReorderQueryKeyTypesRequestBody;
import com.paytech.platform.support.api.dataquery.request.UpdateQueryKeyTypeRequestBody;
import com.paytech.platform.support.api.dataquery.response.QueryKeyTypeResponseBody;
import com.paytech.platform.support.api.dataquery.response.ReorderQueryKeyTypesResponseBody;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyTypeStatus;
import com.paytech.platform.support.dataquery.service.processor.ChangeQueryKeyTypeStatusProcessor;
import com.paytech.platform.support.dataquery.service.processor.CreateQueryKeyTypeProcessor;
import com.paytech.platform.support.dataquery.service.processor.ListQueryKeyTypesProcessor;
import com.paytech.platform.support.dataquery.service.processor.ReorderQueryKeyTypesProcessor;
import com.paytech.platform.support.dataquery.service.processor.UpdateQueryKeyTypeProcessor;
import com.paytech.platform.support.dataquery.service.processor.request.ChangeQueryKeyTypeStatusRequest;
import com.paytech.platform.support.dataquery.service.processor.request.CreateQueryKeyTypeRequest;
import com.paytech.platform.support.dataquery.service.processor.request.ListQueryKeyTypesRequest;
import com.paytech.platform.support.dataquery.service.processor.request.ReorderQueryKeyTypesRequest;
import com.paytech.platform.support.dataquery.service.processor.request.UpdateQueryKeyTypeRequest;
import com.paytech.platform.support.ext.rest.audit.Audit;

import lombok.AllArgsConstructor;

/** 联合查询键类型维护的协议适配；规则、权限与事务委托 dataquery 的 Processor。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class QueryKeyTypeManagementController implements QueryKeyTypeManagementApi {

    private final DataQueryConverter converter;
    private final JointQueryConverter jointConverter;
    private final ListQueryKeyTypesProcessor listProcessor;
    private final CreateQueryKeyTypeProcessor createProcessor;
    private final UpdateQueryKeyTypeProcessor updateProcessor;
    private final ReorderQueryKeyTypesProcessor reorderProcessor;
    private final ChangeQueryKeyTypeStatusProcessor statusProcessor;

    /** 解析筛选与分页后查询。 */
    @Override
    public PagingResponseBody<QueryKeyTypeResponseBody> listQueryKeyTypes(String actor, String session, String status,
        String keyword, Integer page, Integer size) {
        var request = new ListQueryKeyTypesRequest(actor, session,
            converter.optionalCode(QueryKeyTypeStatus.class, status, "status"), DataQueryConverter.text(keyword),
            converter.paging(page, size));
        return listProcessor.process(request).map(JointQueryConverter::keyType);
    }

    /** 转换请求后委托新建。 */
    @Audit(action = "OPERATIONS_QUERY_KEY_TYPE_CREATE", resourceType = "QUERY_KEY_TYPE",
        fallbackResourceId = "QUERY_KEY_TYPE_CATALOG")
    @Override
    public ConfigurationMutationResponseBody createQueryKeyType(String actor, String session,
        CreateQueryKeyTypeRequestBody request) {
        return JointQueryConverter.mutation(createProcessor.process(new CreateQueryKeyTypeRequest(actor, session,
            request.getKeyTypeCode(), jointConverter.definition(request))));
    }

    /** 转换请求后委托修改。 */
    @Audit(action = "OPERATIONS_QUERY_KEY_TYPE_UPDATE", resourceType = "QUERY_KEY_TYPE",
        resourceIdParameter = "keyTypeId", fallbackResourceId = "QUERY_KEY_TYPE_CATALOG")
    @Override
    public ConfigurationMutationResponseBody updateQueryKeyType(String actor, String session, String keyTypeId,
        UpdateQueryKeyTypeRequestBody request) {
        return JointQueryConverter.mutation(updateProcessor.process(new UpdateQueryKeyTypeRequest(actor, session,
            converter.id(keyTypeId, "keyTypeId"), request.getExpectedVersion(), jointConverter.definition(request))));
    }

    /** 转换目标状态后委托启停。 */
    @Audit(action = "OPERATIONS_QUERY_KEY_TYPE_STATUS", resourceType = "QUERY_KEY_TYPE",
        resourceIdParameter = "keyTypeId", fallbackResourceId = "QUERY_KEY_TYPE_CATALOG")
    @Override
    public ConfigurationMutationResponseBody changeQueryKeyTypeStatus(String actor, String session, String keyTypeId,
        ChangeQueryKeyTypeStatusRequestBody request) {
        return JointQueryConverter.mutation(statusProcessor.process(new ChangeQueryKeyTypeStatusRequest(actor, session,
            converter.id(keyTypeId, "keyTypeId"), request.getExpectedVersion(),
            converter.code(QueryKeyTypeStatus.class, request.getStatus(), "status"))));
    }

    /** 转换顺序后委托整体重排。 */
    @Audit(action = "OPERATIONS_QUERY_KEY_TYPE_ORDER", resourceType = "QUERY_KEY_TYPE",
        fallbackResourceId = "QUERY_KEY_TYPE_ORDER")
    @Override
    public ReorderQueryKeyTypesResponseBody reorderQueryKeyTypes(String actor, String session,
        ReorderQueryKeyTypesRequestBody request) {
        return JointQueryConverter.reorder(reorderProcessor.process(new ReorderQueryKeyTypesRequest(actor, session,
            jointConverter.orderItems(request.getItems()))));
    }
}
