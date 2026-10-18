package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsDirectoryRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService;
import com.paytech.platform.support.accesscontrol.service.processor.request.AuthorizeOperationsRequest;
import com.paytech.platform.support.api.enums.GatewayAuthorizationMode;
import com.paytech.platform.support.api.enums.OperationsAccountType;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.Outcome;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** PLT-I02 当前路由授权，按同一数据库快照组合身份、路由和权限事实。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class AuthorizeOperationsProcessor extends AbstractProcessor<AuthorizeOperationsRequest, Boolean> {
    private final Clock clock;
    private final OperationsAuditService audit;
    private final OperationsSessionService sessions;
    private final PlatformTransactionManager transactionManager;
    private final OperationsDirectoryRepository directory;

    /** 保留只读快照和原超时，避免不同提交时点的事实组合成错误授权。 */
    @Override
    protected Boolean processRequest(AuthorizeOperationsRequest request) {
        TransactionTemplate query = new TransactionTemplate(transactionManager);
        query.setReadOnly(true);
        query.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        query.setTimeout(15);
        return query.execute(status -> authorize(request));
    }

    /** 有效身份通过后按路由模式决策，明确拒绝继续采集原细分审计原因。 */
    boolean authorize(AuthorizeOperationsRequest request) {
        audit.observeTransaction();
        String accountId = request.getAccountId();
        String routeCode = request.getRouteCode();
        var current = sessions.requireCurrent(accountId, request.getSessionId());
        if (directory.routeAuthorizationMode(routeCode) == GatewayAuthorizationMode.HUMAN_AUTHENTICATED) {
            boolean allowed = authenticatedRouteAllowed(current, routeCode);
            if (!allowed) {
                audit.record(() -> Outcome.denied(accountId, current.session().tenantId(),
                    new Target("ROUTE", routeCode), "CONTEXT_RESTRICTED", now()));
            }
            return allowed;
        }
        sessions.requireOrdinary(current);
        String permission = directory.routePermission(routeCode);
        boolean allowed = permission != null && directory.permissions(current.member().id()).contains(permission);
        if (!allowed) {
            audit.record(() -> Outcome.denied(accountId, current.member().tenantId(), new Target("ROUTE", routeCode),
                "PERMISSION_DENIED", now()));
        }
        return allowed;
    }

    /** 按初始密码和 Bootstrap 阶段限制免权限路由，始终允许当前身份及退出。 */
    boolean authenticatedRouteAllowed(OperationsSessionService.SessionContext current, String routeCode) {
        if (Set.of("operations-auth-context", "operations-auth-logout").contains(routeCode)) {
            return true;
        }
        if (current.password().forceChange()) {
            return routeCode.equals("operations-auth-password");
        }
        if (current.account().type() == OperationsAccountType.BOOTSTRAP_ADMIN) {
            return routeCode.equals("operations-bootstrap-administrator");
        }
        // 权限选择器按 usage 在业务服务检查真实 READ 权限；这里只允许普通会话进入该接口。
        return Set
            .of("operations-auth-password", "operations-navigation", "operations-configuration-permission-options")
            .contains(routeCode);
    }

    /** 审计时间保持 UTC 微秒精度。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
