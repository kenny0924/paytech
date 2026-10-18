package com.paytech.platform.support.accesscontrol.service.processor;

import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.api.enums.TenantProjectionErrorCode;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

/** 来源投影的本地事务与异常边界，不获取 Redis 锁或访问 CCT。 */
@Component
@ConditionalOnProperty(name = "tenant.projection.enabled", havingValue = "true")
public class TenantProjectionTransactions {
    private final TransactionTemplate query;
    private final TransactionTemplate write;
    private final OperationsAuditService audit;

    /** 沿用 RR 查询、READ_COMMITTED 写入及 15 秒有限超时。 */
    public TenantProjectionTransactions(OperationsAuditService audit, PlatformTransactionManager manager) {
        this.audit = audit;
        query = transaction(manager, TransactionDefinition.ISOLATION_REPEATABLE_READ, true);
        write = transaction(manager, TransactionDefinition.ISOLATION_READ_COMMITTED, false);
    }

    <T> T query(Supplier<T> operation) { return execute(query, operation); }

    /** 同一缺失来源在不同申请人下竞争时，整体回滚后只转换来源唯一键冲突。 */
    <T> T provision(Supplier<T> operation) {
        try {
            return write(operation);
        } catch (DuplicateKeyException failure) {
            String message = failure.getMostSpecificCause().getMessage();
            if (message != null && message.contains("uk_access_tenant"))
                throw new FailException(TenantProjectionErrorCode.TENANT_PROJECTION_CONFLICT);
            throw failure;
        }
    }

    /** 数据库竞争统一转为可识别冲突，保留原异常原因。 */
    <T> T write(Supplier<T> operation) {
        try { return execute(write, operation); }
        catch (ConcurrencyFailureException failure) {
            throw new ConcurrentRequestException("租户投影已被并发更新，请重试原来源命令", failure);
        }
    }

    private <T> T execute(TransactionTemplate template, Supplier<T> operation) {
        return template.execute(status -> {
            audit.observeTransaction();
            return operation.get();
        });
    }

    private TransactionTemplate transaction(PlatformTransactionManager manager, int isolation, boolean readOnly) {
        var template = new TransactionTemplate(manager);
        template.setIsolationLevel(isolation);
        template.setReadOnly(readOnly);
        template.setTimeout(15);
        return template;
    }
}
