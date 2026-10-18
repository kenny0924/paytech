package com.paytech.operations.console.bff.service;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.operations.console.bff.integration.PlatformApiScopeManagementClient;
import com.paytech.operations.console.bff.integration.PlatformApiCredentialManagementClient;
import com.paytech.operations.console.bff.integration.PlatformGatewayConfigurationClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.ApiScopePageModels.*;
import com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.GatewayRouteQueryRequestBody;

/** 页面组合留在 BFF，每次按用户选择读取一种引用，由各 Owner 查询独立授权。 */
@org.springframework.stereotype.Component
@lombok.AllArgsConstructor
public class ApiScopeDetailsProcessor {
    private final PlatformApiScopeManagementClient scopes;
    private final PlatformGatewayConfigurationClient routes;
    private final PlatformApiCredentialManagementClient credentials;

    /** 首先读取目录，未授权引用不转换为空列表。 */
    public ApiScopePageResponseBody details(OperatorContext context, String code, String referenceKind, Integer page, Integer size) {
        String kind = referenceKind == null ? "NONE" : referenceKind;
        if (!java.util.Set.of("NONE", "ROUTE", "CREDENTIAL").contains(kind)
            || "NONE".equals(kind) && (page != null || size != null))
            { throw new InvalidParameterException("引用类型或分页参数不符合合同"); }
        var paging = PagingRequestBody.of(page, size);
        if (paging.size() > 100) { throw new InvalidParameterException("每页最多 100 条"); }
        var scope = scopes.scope(context, code);
        ScopeReferences references = switch (kind) {
            case "NONE" -> null;
            case "ROUTE" -> new RouteReferences(kind, routes.routes(context, paging.page(), paging.size(),
                new GatewayRouteQueryRequestBody(null, null, null, null, code)));
            case "CREDENTIAL" -> new CredentialReferences(kind, credentials.credentials(context, paging.page(), paging.size(),
                null, null, null, null, null, code));
            default -> throw new InvalidParameterException("未知引用类型");
        };
        return new ApiScopePageResponseBody(scope, references);
    }
}
