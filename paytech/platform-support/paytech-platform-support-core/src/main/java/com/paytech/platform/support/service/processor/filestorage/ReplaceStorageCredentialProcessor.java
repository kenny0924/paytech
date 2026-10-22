package com.paytech.platform.support.service.processor.filestorage;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.api.enums.FileStorageCredentialMode;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.domain.storage.ReplaceStorageCredentialCommand;
import com.paytech.platform.support.filedocument.domainservice.service.FileStorageManagementService;
import com.paytech.platform.support.filedocument.service.processor.ProtectStorageCredentialProcessor;
import com.paytech.platform.support.filedocument.service.processor.request.ProtectStorageCredentialRequest;
import com.paytech.platform.support.service.processor.filestorage.request.ReplaceStorageCredentialRequest;

import lombok.AllArgsConstructor;

/**
 * 更换存储实例密钥或切换凭据方式：鉴权与版本检查 → 事务外把新密钥存入 Vault → 短事务替换 Token 并审计
 * <li>Vault 调用在数据库事务外；写入时按版本再次保护。Vault 失败实例不变；Vault 成功但版本冲突返回 409，新 Token 不被引用。</li>
 * <li>之后的存储操作读取新 Token；旧 Token 不再被引用。密钥不进入日志、审计与响应。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ReplaceStorageCredentialProcessor
    extends AbstractLockProcessor<ReplaceStorageCredentialRequest, FileStorageChange> {

    private final FileStorageManagementSupport support;
    private final FileStorageManagementService management;
    private final ProtectStorageCredentialProcessor protect;

    /** 凭据组合只依赖输入。 */
    @Override
    protected void validate(ReplaceStorageCredentialRequest request) {
        FileStorageManagementSupport.requireCredentialInput(request.getCredentialMode(), request.getAccessKey(),
            request.getSecretKey());
    }

    /** 与同一实例的其他写入共用实例锁，锁覆盖 Vault 调用。 */
    @Override
    protected LockCommand lockCommand(ReplaceStorageCredentialRequest request) {
        return LockCommand.tryLock(FileStorageManagementSupport.instanceLockKey(request.getInstanceCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected FileStorageChange processWithinLock(ReplaceStorageCredentialRequest request) {
        // 鉴权与版本检查
        long instanceId = support.precheck(request.getActor(), request.getSession(),
            () -> management.requireInstanceVersion(request.getInstanceCode(), request.getExpectedVersion()));

        // 事务外存入 Vault
        String token = request.getCredentialMode() == FileStorageCredentialMode.STATIC
            ? protect.process(new ProtectStorageCredentialRequest(instanceId, request.getAccessKey(),
                request.getSecretKey())).getCredentialToken()
            : null;

        // 短事务替换 Token 并审计
        Instant now = support.now();
        return support.write(request.getActor(), request.getSession(), request.getReason(), now,
            () -> management.replaceCredential(new ReplaceStorageCredentialCommand(request.getInstanceCode(),
                request.getCredentialMode(), token, request.getExpectedVersion(), now)));
    }
}
