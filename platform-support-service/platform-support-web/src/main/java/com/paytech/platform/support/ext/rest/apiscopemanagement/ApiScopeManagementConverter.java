package com.paytech.platform.support.ext.rest.apiscopemanagement;

import org.springframework.stereotype.Component;

import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementInputs;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementModels.Mutation;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementModels.Scope;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationInputs;
import com.paytech.platform.support.accesscontrol.service.processor.request.CreateApiScopeRequest;
import com.paytech.platform.support.accesscontrol.service.processor.request.GetApiScopeRequest;
import com.paytech.platform.support.accesscontrol.service.processor.request.ListApiScopesRequest;
import com.paytech.platform.support.accesscontrol.service.processor.request.UpdateApiScopeRequest;
import com.paytech.platform.support.api.apiscopemanagement.request.CreateApiScopeRequestBody;
import com.paytech.platform.support.api.apiscopemanagement.request.UpdateApiScopeRequestBody;
import com.paytech.platform.support.api.apiscopemanagement.response.ApiScopeDetailResponseBody;
import com.paytech.platform.support.api.apiscopemanagement.response.ApiScopeMutationResponseBody;
import com.paytech.platform.support.api.apiscopemanagement.response.ApiScopeOptionResponseBody;
import com.paytech.platform.support.api.apiscopemanagement.response.ApiScopeSummaryResponseBody;

/** Scope 管理的协议解析与投影，不访问数据库或处理请求幂等。 */
@Component
public class ApiScopeManagementConverter {

    /** 补齐分页并把查询通配符转为文字，保留每页最多 100 条。 */
    public ListApiScopesRequest toListRequest(String actor, String session, Integer page, Integer size, String keyword) {
        return new ListApiScopesRequest(actor, session, ClientApiManagementInputs.paging(page, size),
            ClientApiManagementInputs.contains(keyword, 255, "keyword"));
    }

    /** 校验 Path 中的精确编码，不自动改写大小写。 */
    public GetApiScopeRequest toDetailRequest(String actor, String session, String code) {
        return new GetApiScopeRequest(actor, session, ClientApiManagementInputs.scopeCode(code));
    }

    /** Body 已由入口校验；保留文本规范化规则，不携带 HTTP 幂等键。 */
    public CreateApiScopeRequest toCreateRequest(String actor, String session, CreateApiScopeRequestBody body) {
        return new CreateApiScopeRequest(actor, session, body.getScopeCode(),
            ConfigurationInputs.text(body.getDisplayName(), 255, "displayName"),
            ClientApiManagementInputs.description(body.getDescription()), ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 解析不可变 Path 编码并规范化编辑内容，null 说明表示清空。 */
    public UpdateApiScopeRequest toUpdateRequest(String actor, String session, String code, UpdateApiScopeRequestBody body) {
        return new UpdateApiScopeRequest(actor, session, ClientApiManagementInputs.scopeCode(code),
            ConfigurationInputs.text(body.getDisplayName(), 255, "displayName"),
            ClientApiManagementInputs.description(body.getDescription()), body.getExpectedVersion(),
            ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 投影列表字段，身份按十进制字符串输出。 */
    public ApiScopeSummaryResponseBody toSummary(Scope scope) {
        return new ApiScopeSummaryResponseBody(scope.apiScopeId().toString(), scope.scopeCode(), scope.displayName(),
            scope.description(), scope.version());
    }

    /** 详情只增加创建与更新时间，不聚合跨权限引用。 */
    public ApiScopeDetailResponseBody toDetail(Scope scope) {
        return new ApiScopeDetailResponseBody(scope.apiScopeId().toString(), scope.scopeCode(), scope.displayName(),
            scope.description(), scope.version(), scope.createTime(), scope.updateTime());
    }

    /** 路由选项只包含编码和名称。 */
    public ApiScopeOptionResponseBody toOption(Scope scope) {
        return new ApiScopeOptionResponseBody(scope.scopeCode(), scope.displayName());
    }

    /** 从本次保存结果生成响应，不查询或重放历史回执。 */
    public ApiScopeMutationResponseBody toMutation(Mutation result) {
        return new ApiScopeMutationResponseBody(Long.toString(result.objectId()), result.objectCode(), result.version());
    }
}
