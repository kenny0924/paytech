package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsContextView;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService;
import com.paytech.platform.support.accesscontrol.service.processor.request.OperationsContextRequest;
import com.paytech.platform.support.api.enums.OperationsAccountType;

import lombok.AllArgsConstructor;

/** 在同一数据库快照中读取账号、凭据、成员和会话资格。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetOperationsContextProcessor extends AbstractProcessor<OperationsContextRequest, OperationsContextView> {
    private final OperationsSessionService sessions;
    private final PlatformTransactionManager transactionManager;

    /** 保留可重复读快照，避免把不同提交时点组合成有效身份。 */
    @Override
    protected OperationsContextView processRequest(OperationsContextRequest request) {
        TransactionTemplate query = new TransactionTemplate(transactionManager);
        query.setReadOnly(true);
        query.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        query.setTimeout(15);
        return query.execute(status -> {
            var current =
                sessions.requireCurrent(Long.toString(request.getAccountId()), Long.toString(request.getSessionId()));
            return new OperationsContextView(current.account().id(), current.session().id(), current.account().type(),
                current.account().emailMasked(), current.account().status(),
                current.member() == null ? null : current.member().tenantId(),
                current.member() == null ? null : current.member().id(), current.password().forceChange(),
                current.account().type() == OperationsAccountType.BOOTSTRAP_ADMIN && !current.password().forceChange(),
                "DEVELOPMENT_MOCK");
        });
    }
}
