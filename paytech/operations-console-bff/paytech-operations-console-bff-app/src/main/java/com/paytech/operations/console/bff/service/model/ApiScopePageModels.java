package com.paytech.operations.console.bff.service.model;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.ApiScopeManagementModels.ApiScopeDetailResponseBody;
import com.paytech.operations.console.bff.service.model.ApiCredentialManagementModels.ApiCredentialSummary;
import com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.GatewayRouteSummaryResponseBody;

/** Scope 页面按用户选中的标签组合一次查询。 */
public final class ApiScopePageModels {
    /** 只承载类型或静态转换，禁止创建无业务意义的实例。 */
    private ApiScopePageModels() {}
    /** @param scope 目录事实。 @param references 未选择时为 null，引用权限错误原样失败。 */
    public record ApiScopePageResponseBody(ApiScopeDetailResponseBody scope, ScopeReferences references) {}
    /** 引用类型与其分页数据保持一致。 */
    public sealed interface ScopeReferences permits RouteReferences, CredentialReferences { /** 声明引用页的稳定类型，避免把不同 Owner 的分页混为一页。 */
    String kind(); }
    /** @param kind 固定 ROUTE。 @param page 授权范围内的路由页。 */
    public record RouteReferences(String kind, PagingResponseBody<GatewayRouteSummaryResponseBody> page) implements ScopeReferences {}
    /** @param kind 固定 CREDENTIAL。 @param page 授权范围内的凭证页。 */
    public record CredentialReferences(String kind, PagingResponseBody<ApiCredentialSummary> page) implements ScopeReferences {}
}
