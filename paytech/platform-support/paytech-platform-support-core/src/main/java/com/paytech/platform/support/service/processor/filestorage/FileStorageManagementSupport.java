package com.paytech.platform.support.service.processor.filestorage;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.api.enums.FileStorageCredentialMode;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationChanged;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationField;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationFieldChange;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.filedocument.core.configuration.FileStorageProperties;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.domain.storage.StorageInstanceSettings;
import com.paytech.platform.support.filedocument.integration.storage.FileStorageRegistry;
import com.paytech.platform.support.filedocument.integration.storage.StorageTrust;

import lombok.AllArgsConstructor;

/**
 * 存储管理处理器共用的鉴权、事务、锁、审计与输入校验
 * <li>file-document 不依赖鉴权与审计模块，由 core 组合：会话与权限重检、file-document 写入和审计事实同一事务提交。</li>
 * <li>Vault 与存储调用之前的鉴权使用不登记审计的只读事务，审计只绑定请求最后的写事务。</li>
 * <li>写入提交后立即刷新本进程的存储快照，其他实例由定时刷新收敛。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class FileStorageManagementSupport {

    /** 查看存储管理的权限。 */
    public static final String READ_PERMISSION = "FILE_STORAGE_READ";

    /** 维护存储管理的权限。 */
    public static final String MANAGE_PERMISSION = "FILE_STORAGE_MANAGE";

    private final Clock clock;
    private final OperationsAuditService audit;
    private final FileStorageRegistry registry;
    private final FileStorageProperties properties;
    private final PlatformTransactionManager transactionManager;
    private final OperationsManagementPolicyService policy;

    /** 实例及其存储桶的写入共用一把锁，按不可变实例代码协调。 */
    static String instanceLockKey(String instanceCode) {
        return "file-storage:instance:" + instanceCode;
    }

    /** 写入路由按来源系统协调。 */
    static String routeLockKey(String sourceSystem) {
        return "file-storage:route:" + sourceSystem;
    }

    /** TRY_LOCK 等待毫秒数。 */
    int lockWaitMillis() {
        return Math.toIntExact(properties.getLockWaitTime().toMillis());
    }

    /** 锁租约毫秒数。 */
    int lockLeaseMillis() {
        return Math.toIntExact(properties.getLockLeaseTime().toMillis());
    }

    /** 本次保存的 UTC 微秒时间。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * 外部步骤之前：只读事务内重检维护权限并执行本库预检，不绑定审计
     *
     * @param check 本库预检，返回外部步骤所需的值
     */
    <T> T precheck(String actor, String session, Supplier<T> check) {
        return readOnly().execute(status -> {
            policy.requirePermission(actor, session, MANAGE_PERMISSION);
            return check.get();
        });
    }

    /** 只读查询：查看或维护权限任一即可，权限与读取同一快照。 */
    <T> T read(String actor, String session, Supplier<T> query) {
        return readOnly().execute(status -> {
            audit.observeTransaction();
            policy.requirePermission(actor, session, READ_PERMISSION, MANAGE_PERMISSION);
            return query.get();
        });
    }

    /**
     * 写入：同一事务内重检维护权限、调用 file-document 写入并记录配置审计；提交后刷新本进程快照
     *
     * @param reason 操作原因
     * @param now 本次写入时间，与写入命令一致
     * @param write file-document 写入，返回字段差异
     * @return file-document 写入结果
     */
    FileStorageChange write(String actor, String session, String reason, Instant now,
        Supplier<FileStorageChange> write) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(15);
        FileStorageChange change;
        try {
            change = transaction.execute(status -> writeWithinTransaction(actor, session, reason, now, write));
        } catch (ConcurrencyFailureException failure) {
            throw new ConcurrentRequestException("发生并发修改，请刷新后重试", failure);
        }
        registry.refresh();
        return change;
    }

    /** 授权、写入与审计事实；审计在写入成功后冻结差异，不含密钥或 Token。 */
    FileStorageChange writeWithinTransaction(String actor, String session, String reason, Instant now,
        Supplier<FileStorageChange> write) {
        audit.observeTransaction();
        var context = policy.authorizeManagement(actor, session, null, List.of(), null, null, false, MANAGE_PERMISSION);
        FileStorageChange change = write.get();
        var fact = new ConfigurationChanged(new Identity(actor, context.member().tenantId()),
            new Target(change.getObjectType(), change.getObjectCode()), change.getBeforeVersion(),
            change.getAfterVersion(), change.getChanges().stream().map(c -> new ConfigurationFieldChange(
                ConfigurationField.valueOf(c.getField().getCode()), c.getBefore(), c.getAfter())).toList(),
            reason, now);
        audit.record(() -> fact);
        return change;
    }

    /** 只读、可重复读、15 秒超时的事务模板。 */
    TransactionTemplate readOnly() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setTimeout(15);
        return transaction;
    }

    /** 地址必须为 HTTPS 根地址；证书必须能解析出 X.509 证书。只依赖输入。 */
    static void requireSettings(StorageInstanceSettings settings) {
        String endpoint = settings.getEndpoint();
        if (endpoint != null && !httpsRoot(endpoint)) {
            throw new InvalidParameterException("endpoint 必须为不含路径、查询与用户信息的 HTTPS 根地址");
        }
        if (settings.getTrustedCertificatePem() != null) {
            try {
                StorageTrust.fromPemText(settings.getTrustedCertificatePem());
            } catch (IllegalArgumentException invalid) {
                throw new InvalidParameterException("trustedCertificatePem 不是合法的 X.509 PEM 证书");
            }
        }
    }

    /** STATIC 必须同时提供访问账号与密钥，IAM_ROLE 不能提供。只依赖输入。 */
    static void requireCredentialInput(FileStorageCredentialMode mode, String accessKey, String secretKey) {
        boolean provided = notBlank(accessKey) && notBlank(secretKey);
        boolean absent = accessKey == null && secretKey == null;
        if (mode == FileStorageCredentialMode.STATIC ? !provided : !absent) {
            throw new InvalidParameterException("STATIC 必须同时提供 accessKey 与 secretKey，IAM_ROLE 不能提供");
        }
    }

    /** HTTPS、有主机，无用户信息、查询、片段与非根路径。 */
    static boolean httpsRoot(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null
                && uri.getQuery() == null && uri.getFragment() == null
                && (uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /** 非空且非空白。 */
    static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
