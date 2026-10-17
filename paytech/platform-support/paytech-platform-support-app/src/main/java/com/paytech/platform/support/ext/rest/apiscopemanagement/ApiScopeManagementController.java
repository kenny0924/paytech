package com.paytech.platform.support.ext.rest.apiscopemanagement;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.apiscopemanagement.ApiScopeManagementApi;
import com.paytech.platform.support.api.apiscopemanagement.request.CreateApiScopeRequestBody;
import com.paytech.platform.support.api.apiscopemanagement.request.UpdateApiScopeRequestBody;
import com.paytech.platform.support.api.apiscopemanagement.response.ApiScopeDetailResponseBody;
import com.paytech.platform.support.api.apiscopemanagement.response.ApiScopeMutationResponseBody;
import com.paytech.platform.support.api.apiscopemanagement.response.ApiScopeOptionResponseBody;
import com.paytech.platform.support.api.apiscopemanagement.response.ApiScopeSummaryResponseBody;
import com.paytech.platform.support.accesscontrol.service.processor.CreateApiScopeProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.GetApiScopeProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.ListApiScopeOptionsProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.ListApiScopesProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.UpdateApiScopeProcessor;
import com.paytech.platform.support.ext.rest.audit.Audit;

/** Client API 管理的协议入口，规则和事务委托 Owner 用例。 */
@RestController
@lombok.AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ApiScopeManagementController implements ApiScopeManagementApi {
    private final GetApiScopeProcessor detailProcessor;
    private final ListApiScopesProcessor listProcessor;
    private final CreateApiScopeProcessor createProcessor;
    private final UpdateApiScopeProcessor updateProcessor;
    private final ApiScopeManagementConverter converter;
    private final ListApiScopeOptionsProcessor optionsProcessor;

    /** 转换可信 Operations 身份及请求，委托对应 Owner 用例。 */
    @Audit(action = "OPERATIONS_API_SCOPES", resourceType = "API_SCOPE", fallbackResourceId = "API_SCOPE_CATALOG", recordSuccess = false)
    @Override
    public PagingResponseBody<ApiScopeSummaryResponseBody> scopes(String actor, String session, Integer page, Integer size, String keyword) {
        return listProcessor.process(converter.toListRequest(actor, session, page, size, keyword)).map(converter::toSummary);
    }

    /** 转换可信 Operations 身份及请求，委托对应 Owner 用例。 */
    @Audit(action = "OPERATIONS_API_SCOPE", resourceType = "API_SCOPE", fallbackResourceId = "API_SCOPE_CATALOG", recordSuccess = false, resourceIdParameter = "scopeCode")
    @Override
    public ApiScopeDetailResponseBody scope(String actor, String session, String scopeCode) {
        return converter.toDetail(detailProcessor.process(converter.toDetailRequest(actor, session, scopeCode)));
    }

    /** 转换可信 Operations 身份及请求，委托对应 Owner 用例。 */
    @Audit(action = "OPERATIONS_API_SCOPE_OPTIONS", resourceType = "API_SCOPE", fallbackResourceId = "API_SCOPE_CATALOG", recordSuccess = false)
    @Override
    public PagingResponseBody<ApiScopeOptionResponseBody> options(String actor, String session, Integer page, Integer size, String keyword) {
        return optionsProcessor.process(converter.toListRequest(actor, session, page, size, keyword)).map(converter::toOption);
    }

    /** 转换可信 Operations 身份及请求，委托对应 Owner 用例。 */
    @Audit(action = "OPERATIONS_API_SCOPE_CREATE", resourceType = "API_SCOPE", fallbackResourceId = "API_SCOPE_CATALOG", recordSuccess = true)
    @Override
    public ApiScopeMutationResponseBody create(String actor, String session, CreateApiScopeRequestBody request) {
        return converter.toMutation(createProcessor.process(converter.toCreateRequest(actor, session, request)));
    }

    /** 转换可信 Operations 身份及请求，委托对应 Owner 用例。 */
    @Audit(action = "OPERATIONS_API_SCOPE_UPDATE", resourceType = "API_SCOPE", fallbackResourceId = "API_SCOPE_CATALOG", recordSuccess = true, resourceIdParameter = "scopeCode")
    @Override
    public ApiScopeMutationResponseBody update(String actor, String session, String scopeCode, UpdateApiScopeRequestBody request) {
        return converter.toMutation(updateProcessor.process(converter.toUpdateRequest(actor, session, scopeCode, request)));
    }
}
