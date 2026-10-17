package com.paytech.platform.support.ext.rest.externalapi;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.externalapi.StandardApiManagementApi;
import com.paytech.platform.support.api.externalapi.request.CreateStandardApiRequestBody;
import com.paytech.platform.support.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateStandardApiRequestBody;
import com.paytech.platform.support.api.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.platform.support.api.externalapi.response.StandardApiResponseBody;
import com.paytech.platform.support.api.externalapi.response.StandardApiSummary;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.integration.service.processor.CreateStandardApiProcessor;
import com.paytech.platform.support.integration.service.processor.DeleteStandardApiProcessor;
import com.paytech.platform.support.integration.service.processor.GetStandardApiProcessor;
import com.paytech.platform.support.integration.service.processor.ListStandardApisProcessor;
import com.paytech.platform.support.integration.service.processor.UpdateStandardApiProcessor;

import lombok.AllArgsConstructor;

/** 标准 API 管理的协议入口，规则和事务委托 integration 模块的 Processor。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class StandardApiManagementController implements StandardApiManagementApi {

    private final GetStandardApiProcessor detailProcessor;
    private final ListStandardApisProcessor listProcessor;
    private final ExternalApiManagementConverter converter;
    private final CreateStandardApiProcessor createProcessor;
    private final UpdateStandardApiProcessor updateProcessor;
    private final DeleteStandardApiProcessor deleteProcessor;

    /** 转换查询参数后委托列表用例。 */
    @Audit(action = "OPERATIONS_STANDARD_APIS", resourceType = "STANDARD_API",
        fallbackResourceId = "STANDARD_API_CATALOG", recordSuccess = false)
    @Override
    public PagingResponseBody<StandardApiSummary> standardApis(String actor, String session, Integer page,
        Integer size, String keyword) {
        return converter.toStandardApiPage(listProcessor.process(
            converter.toListRequest(actor, session, page, size, keyword)));
    }

    /** 转换路径编码后委托详情用例。 */
    @Audit(action = "OPERATIONS_STANDARD_API", resourceType = "STANDARD_API",
        fallbackResourceId = "STANDARD_API_CATALOG", recordSuccess = false)
    @Override
    public StandardApiResponseBody standardApi(String actor, String session, String standardApiCode) {
        return converter.toStandardApi(detailProcessor.process(converter.toGetRequest(actor, session, standardApiCode)));
    }

    /** 转换请求后委托创建用例。 */
    @Audit(action = "OPERATIONS_STANDARD_API_CREATE", resourceType = "STANDARD_API",
        fallbackResourceId = "NEW_STANDARD_API")
    @Override
    public ExternalApiMutationResponseBody createStandardApi(String actor, String session,
        CreateStandardApiRequestBody request) {
        return converter.toMutation(createProcessor.process(converter.toCreateRequest(actor, session, request)));
    }

    /** 转换请求后委托修改用例。 */
    @Audit(action = "OPERATIONS_STANDARD_API_UPDATE", resourceType = "STANDARD_API",
        fallbackResourceId = "STANDARD_API_CATALOG")
    @Override
    public ExternalApiMutationResponseBody updateStandardApi(String actor, String session, String standardApiCode,
        UpdateStandardApiRequestBody request) {
        return converter.toMutation(
            updateProcessor.process(converter.toUpdateRequest(actor, session, standardApiCode, request)));
    }

    /** 转换请求后委托删除用例。 */
    @Audit(action = "OPERATIONS_STANDARD_API_DELETE", resourceType = "STANDARD_API",
        fallbackResourceId = "STANDARD_API_CATALOG")
    @Override
    public ExternalApiMutationResponseBody deleteStandardApi(String actor, String session, String standardApiCode,
        DeleteExternalApiConfigRequestBody request) {
        return converter.toMutation(
            deleteProcessor.process(converter.toDeleteRequest(actor, session, standardApiCode, request)));
    }
}
