package com.paytech.platform.support.core.audit;

import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditProjector;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 立即冻结类型化业务事实；生命周期、投影及异常隔离不暴露给业务调用方。 */
@Service
@AllArgsConstructor
@ConditionalOnExpression("${operations.authentication.enabled:false} || ${client.authentication.enabled:false}")
public class OperationsAuditServiceImpl implements OperationsAuditService {
    private final AuditContext context;

    /** 委托既有线程审计上下文观察本地事务，不执行审计持久化或外部调用。 */
    @Override
    public void observeTransaction() {
        context.observeTransaction();
    }

    @Override
    public void identify(Supplier<Identity> identity) {
        context.capture(() -> context.identify(identity.get()));
    }

    @Override
    public void target(Supplier<Target> target) {
        context.capture(() -> context.target(target.get()));
    }

    @Override
    public void record(Supplier<? extends OperationsAuditFact> fact) {
        context.capture(() -> context.record(OperationsAuditProjector.project(fact.get())));
    }

    @Override
    public void suppressReplay() {
        context.replay();
    }
}
