package com.paytech.platform.support.accesscontrol.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientWorkspaceView;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerAuthenticationService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientContextRequest;

import lombok.AllArgsConstructor;

/**
 * 查询当前账号的 CLIENT 工作区（PLT-I16 选择前的候选）。
 * <li>只读快照内先核对会话，再列出非 PENDING 租户中的成员；不取得资源锁。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class GetClientWorkspacesProcessor extends AbstractProcessor<ClientContextRequest, List<ClientWorkspaceView>> {
    private final PlatformTransactionManager transactionManager;
    private final CustomerAuthenticationService authentication;

    @Override
    protected List<ClientWorkspaceView> processRequest(ClientContextRequest request) {
        TransactionTemplate query = new TransactionTemplate(transactionManager);
        query.setReadOnly(true);
        query.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        query.setTimeout(15);
        return query.execute(status -> authentication.workspaces(request.getAccountId(), request.getSessionId()));
    }
}
