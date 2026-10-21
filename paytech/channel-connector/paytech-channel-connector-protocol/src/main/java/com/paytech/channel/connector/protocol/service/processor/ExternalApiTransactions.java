package com.paytech.channel.connector.protocol.service.processor;

import java.util.function.Supplier;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ConcurrentRequestException;

/**
 * 外部 API 配置的本地事务模板：写入 READ_COMMITTED，详情只读 REPEATABLE_READ，均 15 秒上限
 * <li>基于 channel-connector 唯一的事务管理器；变更记录与业务写入在同一写事务中提交。</li>
 */
@Component
public class ExternalApiTransactions {

    private final TransactionTemplate query;
    private final TransactionTemplate write;

    /** 模板初始化后不再修改。 */
    public ExternalApiTransactions(PlatformTransactionManager manager) {
        this.query = transaction(manager, TransactionDefinition.ISOLATION_REPEATABLE_READ, true);
        this.write = transaction(manager, TransactionDefinition.ISOLATION_READ_COMMITTED, false);
    }

    /** 详情的主表、字段与引用来自同一快照，避免组合出从未同时存在的配置。 */
    <T> T query(Supplier<T> operation) {
        return query.execute(status -> operation.get());
    }

    /** 行锁读取、引用校验、保存与变更记录同属一个事务；事务内只访问本库。 */
    <T> T write(Supplier<T> operation) {
        try {
            return write.execute(status -> operation.get());
        } catch (ConcurrencyFailureException failure) {
            throw new ConcurrentRequestException("外部 API 配置发生并发修改，请刷新后重试", failure);
        }
    }

    /** 固定隔离级别与超时。 */
    TransactionTemplate transaction(PlatformTransactionManager manager, int isolation, boolean readOnly) {
        var template = new TransactionTemplate(manager);
        template.setIsolationLevel(isolation);
        template.setReadOnly(readOnly);
        template.setTimeout(15);
        return template;
    }
}
