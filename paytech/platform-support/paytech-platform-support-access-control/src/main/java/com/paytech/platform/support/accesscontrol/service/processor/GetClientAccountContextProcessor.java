package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAccountView;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerAuthenticationService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientContextRequest;

import lombok.AllArgsConstructor;

/** 查询当前 Client 账号上下文。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class GetClientAccountContextProcessor extends AbstractProcessor<ClientContextRequest, ClientAccountView> {
    private final PlatformTransactionManager transactionManager;
    private final CustomerAuthenticationService authentication;

    /** 在同一只读快照中核对账号、凭据与会话，不取得排他资源锁。 */
    @Override
    protected ClientAccountView processRequest(ClientContextRequest request) {
        TransactionTemplate query = new TransactionTemplate(transactionManager);
        query.setReadOnly(true);
        query.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        query.setTimeout(15);
        return query.execute(status -> authentication.currentContext(request.getAccountId(), request.getSessionId()));
    }

}
