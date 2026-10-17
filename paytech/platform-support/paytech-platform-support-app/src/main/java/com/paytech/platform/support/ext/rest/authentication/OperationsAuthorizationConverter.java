package com.paytech.platform.support.ext.rest.authentication;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.platform.support.accesscontrol.domain.operations.OperationsNavigationResourceView;
import com.paytech.platform.support.accesscontrol.service.processor.request.AuthorizeOperationsRequest;
import com.paytech.platform.support.accesscontrol.service.processor.request.QueryOperationsNavigationRequest;
import com.paytech.platform.support.api.authorization.request.AuthorizationRequestBody;
import com.paytech.platform.support.api.authorization.response.AuthorizationDecisionResponseBody;
import com.paytech.platform.support.api.authorization.response.ConsoleResource;
import com.paytech.platform.support.api.authorization.response.NavigationResponseBody;

/** 只转换可信身份、路由及导航协议字段，不执行授权或持久化。 */
@Component
public class OperationsAuthorizationConverter {
    /** 组装授权请求，保留原路由值，身份有效性由会话规则确认。 */
    public AuthorizeOperationsRequest authorize(String accountId, String sessionId, AuthorizationRequestBody body) {
        return new AuthorizeOperationsRequest(accountId, sessionId, body.getRouteCode());
    }

    /** 将无 Body 的导航查询组装为模块请求。 */
    public QueryOperationsNavigationRequest navigation(String accountId, String sessionId) {
        return new QueryOperationsNavigationRequest(accountId, sessionId);
    }

    /** 只返回当前路由的明确授权结果。 */
    public AuthorizationDecisionResponseBody decision(boolean allowed) {
        return new AuthorizationDecisionResponseBody(allowed);
    }

    /** 保持查询顺序和父子关系，不在协议层重新过滤权限。 */
    public NavigationResponseBody navigation(List<OperationsNavigationResourceView> resources) {
        return new NavigationResponseBody(resources.stream().map(this::resource).toList());
    }

    /** 资源主键转为字符串，闭合枚举转为稳定 Code。 */
    ConsoleResource resource(OperationsNavigationResourceView resource) {
        return new ConsoleResource(Long.toString(resource.getId()), resource.getCode(), resource.getType().getCode(),
            resource.getParentId() == null ? null : resource.getParentId().toString(), resource.getDisplayName(),
            resource.getFrontendRoute(), resource.getComponentKey(), resource.getSortOrder());
    }
}
