package com.paytech.platform.support.service.processor.files;

import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 内部文件写入的事务策略：读已提交、15 秒超时
 * <li>不依赖人工认证，也不登记 Operations 审计；内部文件接口不写 Operations 审计。</li>
 */
@Component
@ConditionalOnProperty(name = "files.enabled", havingValue = "true")
public class FileTransactions {

    private final TransactionTemplate write;

    /** 绑定同库事务管理器。 */
    public FileTransactions(@Qualifier("transactionManager") PlatformTransactionManager manager) {
        write = new TransactionTemplate(manager);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        write.setTimeout(15);
    }

    /** 在读已提交事务中执行本库写入并返回结果。 */
    public <T> T write(Supplier<T> operation) {
        return write.execute(status -> operation.get());
    }
}
