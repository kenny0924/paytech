package com.paytech.platform.support.accesscontrol.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ApiScope;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientDeveloperPermissions;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ApiScopeRepository;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientContextRequest;

import lombok.AllArgsConstructor;

/** 创建凭证时可选的 Scope 目录（最多 200 项，按编码排序）；需要凭证读取权限。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class ListClientApiScopesProcessor extends AbstractProcessor<ClientContextRequest, List<ApiScope>> {
    private final ClientWorkspaceAccessSupport access;
    private final ApiScopeRepository scopes;

    @Override
    protected List<ApiScope> processRequest(ClientContextRequest request) {
        return access.readTransaction().execute(status -> {
            access.require(request.getAccountId(), request.getSessionId(), ClientDeveloperPermissions.API_CREDENTIAL_READ);
            return scopes.scopes(null, 0, 200);
        });
    }
}
