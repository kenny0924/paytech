package com.paytech.platform.support.service.processor.filestorage;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.api.enums.FileStorageCredentialMode;
import com.paytech.platform.support.filedocument.domain.storage.CreateStorageInstanceCommand;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.domainservice.service.FileStorageManagementService;
import com.paytech.platform.support.filedocument.service.processor.ProtectStorageCredentialProcessor;
import com.paytech.platform.support.filedocument.service.processor.request.ProtectStorageCredentialRequest;
import com.paytech.platform.support.service.processor.filestorage.request.CreateStorageInstanceRequest;

import lombok.AllArgsConstructor;

/**
 * 新建 DRAFT 存储实例：鉴权与代码查重 → 事务外把 STATIC 密钥存入 Vault → 短事务写库并审计
 * <li>按实例代码持资源锁；代码唯一索引仍为最终保护。Vault 失败不写库；写库失败时新 Token 不被引用（Vault V1 无 Retire）。</li>
 * <li>密钥只在内存中传递，不进入日志、审计与响应。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateStorageInstanceProcessor
    extends AbstractLockProcessor<CreateStorageInstanceRequest, FileStorageChange> {

    private final IdGenerator ids;
    private final FileStorageManagementSupport support;
    private final FileStorageManagementService management;
    private final ProtectStorageCredentialProcessor protect;

    /** 设置格式、凭据组合与类型限制只依赖输入。 */
    @Override
    protected void validate(CreateStorageInstanceRequest request) {
        if (request.getSettings().getStorageType() == null) {
            throw new InvalidParameterException("storageType 必须为 MINIO 或 S3");
        }
        FileStorageManagementSupport.requireSettings(request.getSettings());
        FileStorageManagementSupport.requireCredentialInput(request.getCredentialMode(), request.getAccessKey(),
            request.getSecretKey());
    }

    /** 与同一实例的其他写入共用实例锁。 */
    @Override
    protected LockCommand lockCommand(CreateStorageInstanceRequest request) {
        return LockCommand.tryLock(FileStorageManagementSupport.instanceLockKey(request.getInstanceCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected FileStorageChange processWithinLock(CreateStorageInstanceRequest request) {
        // 鉴权与代码查重
        support.precheck(request.getActor(), request.getSession(), () -> {
            management.requireInstanceAbsent(request.getInstanceCode());
            return null;
        });

        // 事务外存入 Vault
        long instanceId = ids.nextId();
        String token = protect(instanceId, request);

        // 短事务写库并审计
        Instant now = support.now();
        return support.write(request.getActor(), request.getSession(), request.getReason(), now,
            () -> management.createInstance(new CreateStorageInstanceCommand(instanceId, request.getInstanceCode(),
                request.getSettings(), request.getCredentialMode(), token, now)));
    }

    /** STATIC 返回新 Token；IAM_ROLE 不访问 Vault。 */
    String protect(long instanceId, CreateStorageInstanceRequest request) {
        if (request.getCredentialMode() != FileStorageCredentialMode.STATIC) {
            return null;
        }
        return protect.process(new ProtectStorageCredentialRequest(instanceId, request.getAccessKey(),
            request.getSecretKey())).getCredentialToken();
    }
}
