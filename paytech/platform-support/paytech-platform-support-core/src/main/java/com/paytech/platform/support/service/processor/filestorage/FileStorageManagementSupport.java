package com.paytech.platform.support.service.processor.filestorage;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.BizCheckFailException;
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
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.domain.storage.StorageInstanceSettings;
import com.paytech.platform.support.filedocument.integration.scanning.FormatFileScanner;
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

    /** 查看文件记录的权限。 */
    public static final String RECORD_READ_PERMISSION = "FILE_RECORD_READ";

    /** 下载文件记录的权限。 */
    public static final String RECORD_DOWNLOAD_PERMISSION = "FILE_RECORD_DOWNLOAD";

    /** 检查策略修订的最大字节数上限（100 MiB）。 */
    static final long MAX_POLICY_SIZE_BYTES = 104_857_600L;

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

    /** 写入路由按来源系统协调；同一来源的两个访问级别共用一把锁（低频配置，沿用原锁名保持滚动发布互斥）。 */
    static String routeLockKey(String sourceSystem) {
        return "file-storage:route:" + sourceSystem;
    }

    /** 检查策略按代码协调，覆盖该代码的全部修订。 */
    static String scanPolicyLockKey(String policyCode) {
        return "file-scan-policy:" + policyCode;
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
        return read(actor, session, query, READ_PERMISSION, MANAGE_PERMISSION);
    }

    /**
     * 只读查询：持有任一所需权限，权限与读取同一快照
     *
     * @param permissions 至少满足一项的权限
     */
    <T> T read(String actor, String session, Supplier<T> query, String... permissions) {
        return readOnly().execute(status -> {
            audit.observeTransaction();
            policy.requirePermission(actor, session, permissions);
            return query.get();
        });
    }

    /**
     * 外部步骤之前：只读事务内重检权限，返回操作人账号 ID（写入下载审计的主体）
     *
     * @param permission 所需权限
     * @return 当前操作人账号 ID
     */
    long requireAccount(String actor, String session, String permission) {
        return readOnly().execute(status -> {
            audit.observeTransaction();
            return policy.requirePermission(actor, session, permission).account().id();
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

    /**
     * 对外基础地址规则：PUBLIC 必填，https:// 开头、有主机、不带用户信息、查询串与片段、不以 / 结尾；PRIVATE 必须为空。只依赖输入
     *
     * @param accessLevel 存储桶访问级别
     * @param publicBaseUrl 去除首尾空白后的基础地址，可空
     */
    static void requirePublicBaseUrl(FileAccessLevel accessLevel, String publicBaseUrl) {
        if (accessLevel == FileAccessLevel.PRIVATE) {
            if (publicBaseUrl != null) {
                throw new BizCheckFailException("私有存储桶不能登记对外基础地址");
            }
            return;
        }
        if (publicBaseUrl == null) {
            throw new BizCheckFailException("公开存储桶必须登记对外基础地址");
        }
        if (!publicBase(publicBaseUrl)) {
            throw new BizCheckFailException(
                "对外基础地址必须为 https:// 开头、不带查询串、片段或用户信息且不以 / 结尾的地址: " + publicBaseUrl);
        }
    }

    /** https、有主机，无用户信息、查询串与片段，不以 / 结尾。 */
    static boolean publicBase(String value) {
        if (value.endsWith("/") || value.contains("?") || value.contains("#")) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /**
     * 检查策略修订内容规则：允许类型非空、不重复、均为格式检查器能识别的类型；最大字节数 1—104857600。只依赖输入
     *
     * @param allowedMimeTypes 允许的规范 MIME
     * @param maxSizeBytes 最大字节数
     */
    static void requireRevisionContent(List<String> allowedMimeTypes, long maxSizeBytes) {
        if (allowedMimeTypes == null || allowedMimeTypes.isEmpty()) {
            throw new BizCheckFailException("允许的类型不能为空");
        }
        if (new HashSet<>(allowedMimeTypes).size() != allowedMimeTypes.size()) {
            throw new BizCheckFailException("允许的类型不能重复");
        }
        for (String type : allowedMimeTypes) {
            if (!FormatFileScanner.SUPPORTED_MIME_TYPES.contains(type)) {
                throw new BizCheckFailException("格式检查无法识别该类型: " + type + "，可选 "
                    + String.join("、", FormatFileScanner.SUPPORTED_MIME_TYPES));
            }
        }
        if (maxSizeBytes < 1 || maxSizeBytes > MAX_POLICY_SIZE_BYTES) {
            throw new BizCheckFailException("最大字节数必须在 1—104857600（100 MiB）之间: " + maxSizeBytes);
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
