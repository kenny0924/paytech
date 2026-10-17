package com.paytech.platform.support.accesscontrol.service.processor;

import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

/** 权限配置的本地写事务策略及公共数据库并发异常转换。 */
@Component
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class PermissionManagementTransactions {
    private final OperationsAuditService audit;
    private final TransactionTemplate write;

    /** 保留原配置写入 SERIALIZABLE 隔离及 15 秒上限。 */
    public PermissionManagementTransactions(OperationsAuditService audit, PlatformTransactionManager manager) {
        this.audit = audit;
        this.write = new TransactionTemplate(manager);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        write.setTimeout(15);
    }

    /** 事务内只操作本库；审计登记观察提交结果，持久化仍由已有尽力机制完成。 */
    <T> T write(Supplier<T> operation) {
        try {
            return write.execute(status -> {
                audit.observeTransaction();
                return operation.get();
            });
        } catch (ConcurrencyFailureException failure) {
            throw new ConcurrentRequestException("配置发生并发修改，请刷新后重试", failure);
        }
    }
}
