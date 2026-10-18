package com.paytech.operations.console.bff.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.operations.console.bff.domain.enums.ApiScopeReferenceKind;
import com.paytech.operations.console.bff.integration.PlatformApiCredentialManagementClient;
import com.paytech.operations.console.bff.integration.PlatformApiScopeManagementClient;
import com.paytech.operations.console.bff.integration.PlatformGatewayConfigurationClient;
import com.paytech.operations.console.bff.service.model.ApiScopePageModels.ApiScopePageResponseBody;
import com.paytech.operations.console.bff.service.model.ApiScopePageModels.CredentialReferences;
import com.paytech.operations.console.bff.service.model.ApiScopePageModels.RouteReferences;
import com.paytech.operations.console.bff.service.model.ApiScopePageModels.ScopeReferences;
import com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.GatewayRouteQueryRequestBody;
import com.paytech.operations.console.bff.service.processor.request.ApiScopeDetailsRequest;

import lombok.AllArgsConstructor;

/**
 * API Scope 详情页：先读取 Scope，再按用户选择读取一种引用分页（Gateway 路由或 API 凭证）
 * <li>页面组合留在 BFF；三类数据分别由 Platform 对应查询独立授权，未授权的引用不转换为空列表。</li>
 * <li>NONE 只返回 Scope，不接受分页参数；引用分页沿用公共分页规则，且每页最多 100 条。</li>
 */
@Component
@AllArgsConstructor
public class ApiScopeDetailsProcessor extends AbstractProcessor<ApiScopeDetailsRequest, ApiScopePageResponseBody> {
    /** 引用分页每页条数上限。 */
    static final int MAX_REFERENCE_PAGE_SIZE = 100;

    private final PlatformApiScopeManagementClient scopes;
    private final PlatformGatewayConfigurationClient routes;
    private final PlatformApiCredentialManagementClient credentials;

    /** 引用类型必须在合同内，分页参数与引用类型的组合及每页上限在读取上游前拒绝。 */
    @Override
    protected void validate(ApiScopeDetailsRequest request) {
        ApiScopeReferenceKind kind = request.resolvedReferenceKind();
        boolean pagingProvided = request.getPage() != null || request.getSize() != null;
        if (kind == null || kind == ApiScopeReferenceKind.NONE && pagingProvided) {
            throw new InvalidParameterException("引用类型或分页参数不符合合同");
        }
        if (PagingRequestBody.of(request.getPage(), request.getSize()).size() > MAX_REFERENCE_PAGE_SIZE) {
            throw new InvalidParameterException("每页最多 100 条");
        }
    }

    @Override
    protected ApiScopePageResponseBody processRequest(ApiScopeDetailsRequest request) {
        var scope = scopes.scope(request.getOperator(), request.getScopeCode());
        return new ApiScopePageResponseBody(scope, references(request));
    }

    /** 按引用类型读取一页引用；NONE 不读取上游，返回 null。 */
    ScopeReferences references(ApiScopeDetailsRequest request) {
        var operator = request.getOperator();
        var paging = PagingRequestBody.of(request.getPage(), request.getSize());
        return switch (request.resolvedReferenceKind()) {
            case NONE -> null;
            case ROUTE ->
                new RouteReferences(ApiScopeReferenceKind.ROUTE.getCode(), routes.routes(operator, paging.page(),
                    paging.size(), new GatewayRouteQueryRequestBody(null, null, null, null, request.getScopeCode())));
            case CREDENTIAL ->
                new CredentialReferences(ApiScopeReferenceKind.CREDENTIAL.getCode(), credentials.credentials(operator,
                    paging.page(), paging.size(), null, null, null, null, null, request.getScopeCode()));
        };
    }
}
