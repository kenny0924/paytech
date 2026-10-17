package com.paytech.platform.support.ext.rest.apicredentialmanagement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.service.processor.GetApiCredentialProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.ListApiCredentialScopesProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.ListApiCredentialsProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.RevokeApiCredentialProcessor;
import com.paytech.platform.support.api.apicredentialmanagement.ApiCredentialManagementApi;
import com.paytech.platform.support.api.apicredentialmanagement.request.RevokeApiCredentialRequestBody;
import com.paytech.platform.support.api.apicredentialmanagement.response.ApiCredentialDetailResponseBody;
import com.paytech.platform.support.api.apicredentialmanagement.response.ApiCredentialRevokeResponseBody;
import com.paytech.platform.support.api.apicredentialmanagement.response.ApiCredentialScopeGrantResponseBody;
import com.paytech.platform.support.api.apicredentialmanagement.response.ApiCredentialSummaryResponseBody;
import com.paytech.platform.support.ext.rest.audit.Audit;

/** Client API 管理的协议入口，规则和事务委托 Owner 用例。 */
@RestController
@lombok.AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ApiCredentialManagementController implements ApiCredentialManagementApi {
    private final GetApiCredentialProcessor detailProcessor;
    private final ListApiCredentialScopesProcessor scopesProcessor;
    private final RevokeApiCredentialProcessor revokeProcessor;
    private final ApiCredentialManagementConverter converter;
    private final ListApiCredentialsProcessor listProcessor;
    private final ApiCredentialListConverter listConverter;

    /** 接受可信 Operations 身份并委托凭证管理入口；请求沿用严格 HTTP 校验。 */
    @Audit(action = "OPERATIONS_API_CREDENTIALS", resourceType = "API_CREDENTIAL", fallbackResourceId = "API_CREDENTIAL_CATALOG", recordSuccess = false)
    @Override
    public PagingResponseBody<ApiCredentialSummaryResponseBody> credentials(String actor, String session, Integer page, Integer size, String clientId, String clientName, String credentialId, String keyPrefix, String status, String scopeCode) {
        var request = listConverter.toRequest(actor, session, page, size, clientId, clientName, credentialId, keyPrefix, status, scopeCode);
        return listProcessor.process(request).map(listConverter::toResponseBody);
    }

    /** 接受可信 Operations 身份并委托凭证管理入口；请求沿用严格 HTTP 校验。 */
    @Audit(action = "OPERATIONS_API_CREDENTIAL", resourceType = "API_CREDENTIAL", fallbackResourceId = "API_CREDENTIAL_CATALOG", recordSuccess = false, resourceIdParameter = "credentialId")
    @Override
    public ApiCredentialDetailResponseBody credential(String actor, String session, String credentialId) {
        return converter
            .toResponseBody(detailProcessor.process(converter.toDetailRequest(actor, session, credentialId)));
    }

    /** 接受可信 Operations 身份并委托凭证管理入口；请求沿用严格 HTTP 校验。 */
    @Audit(action = "OPERATIONS_API_CREDENTIAL_SCOPES", resourceType = "API_CREDENTIAL", fallbackResourceId = "API_CREDENTIAL_CATALOG", recordSuccess = false, resourceIdParameter = "credentialId")
    @Override
    public PagingResponseBody<ApiCredentialScopeGrantResponseBody> scopes(String actor, String session,
        String credentialId, Integer page, Integer size) {
        return scopesProcessor.process(converter.toScopesRequest(actor, session, credentialId, page, size))
            .map(converter::toResponseBody);
    }

    /** 接受可信 Operations 身份并委托凭证管理入口；请求沿用严格 HTTP 校验。 */
    @Audit(action = "OPERATIONS_API_CREDENTIAL_REVOKE", resourceType = "API_CREDENTIAL", fallbackResourceId = "API_CREDENTIAL_CATALOG", recordSuccess = true, resourceIdParameter = "credentialId")
    @Override
    public ApiCredentialRevokeResponseBody revoke(String actor, String session, String credentialId, RevokeApiCredentialRequestBody request) {
        return converter.toResponseBody(
            revokeProcessor.process(converter.toRevokeRequest(actor, session, credentialId, request)));
    }
}
