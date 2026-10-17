package com.paytech.platform.support.accesscontrol.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientAuthorizeRequest;

import lombok.AllArgsConstructor;

/**
 * CLIENT 权限判断（PLT-I02 CLIENT 版）：返回当前工作区成员被授予的权限码。
 * <li>同一只读快照内重证租户上下文会话并查询授权；不取得资源锁、不写审计。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class AuthorizeClientProcessor extends AbstractProcessor<ClientAuthorizeRequest, List<String>> {
    private final ClientWorkspaceAccessSupport access;

    @Override
    protected List<String> processRequest(ClientAuthorizeRequest request) {
        return access.readTransaction().execute(status -> access.granted(
            access.workspace(request.getAccountId(), request.getSessionId()), request.getPermissionCodes()));
    }
}
