package com.paytech.platform.support.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientOnboardingAccessService;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerAuthenticationService;
import com.paytech.platform.support.core.integration.files.CctFileTargetClient;
import com.paytech.platform.support.filedocument.domain.file.FileDisplayName;
import com.paytech.platform.support.filedocument.domain.file.FileSourceCodes;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Grant;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Start;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.domain.storage.StorageWriteTarget;
import com.paytech.platform.support.filedocument.domainservice.service.FileUploadService;
import com.paytech.platform.support.filedocument.integration.storage.FileObjectStorage;
import com.paytech.platform.support.filedocument.integration.storage.FileStorageRegistry;
import com.paytech.platform.support.service.processor.request.StartOnboardingUploadRequest;
import com.paytech.platform.support.service.processor.response.StartOnboardingUploadResponse;

import lombok.AllArgsConstructor;

/**
 * 开始入驻文件上传：锁定上传目标 → 校验会话 → 事务外向 CCT 授权 → 登记 Upload → 事务外签发 PUT 授权。
 * <li>资源锁按账号、申请与主体串行同一上传目标；账号行锁在 Lease 失效后继续串行 Tenant 解析。</li>
 * <li>每次请求新建 Upload，没有可供比较的原业务操作身份；HTTP 请求重放由 Gateway 处理。</li>
 * <li>会话查询、CCT 授权与存储签发均在数据库事务之外；IAM 锁定与 Upload 登记在同一本地事务提交。</li>
 * <li>存储目标按来源系统 platform-support 的写入路由选择，在事务前确定；没有可用路由或桶时返回不可用，不登记、不签发。</li>
 * <li>上传记录来源系统 platform-support、原因 CLIENT_ONBOARDING_MATERIAL，同时照旧保存上传账号、租户与 CCT 目标。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "files.onboarding.enabled", havingValue = "true")
public class StartOnboardingUploadProcessor
    extends AbstractLockProcessor<StartOnboardingUploadRequest, StartOnboardingUploadResponse> {

    private final Clock clock;

    private final IdGenerator ids;

    private final FileUploadService files;

    private final CctFileTargetClient targets;

    private final FileStorageRegistry registry;

    private final ClientOnboardingAccessService access;

    private final CustomerAuthenticationService authentication;

    private final OperationsTransactionExecutor transactions;

    /** 文件名长度与归一化后的展示名只依赖输入，在加锁前拒绝。 */
    @Override
    protected void validate(StartOnboardingUploadRequest request) {
        String filename = request.getFilename();
        if (!FileDisplayName.withinLimit(filename)) {
            throw new InvalidParameterException("文件名最多 255 个字符");
        }
        if (!FileDisplayName.usable(request.displayName())) {
            throw new InvalidParameterException("文件展示名无效");
        }
    }

    /** 与原注解锁共用完整逻辑 Key、普通 RLock 和有限等待 / 租约。 */
    @Override
    protected LockCommand lockCommand(StartOnboardingUploadRequest request) {
        return LockCommand.tryLocks(
            OnboardingFileLocks.startKeys(request.getAccountId(), request.getApplicationId(),
                request.getSubjectPartyId()),
            OnboardingFileLocks.WAIT_TIME, OnboardingFileLocks.LEASE_TIME);
    }

    @Override
    protected StartOnboardingUploadResponse processWithinLock(StartOnboardingUploadRequest request) {
        // 校验会话
        transactions.query(() -> authentication.context(request.getAccountId(), request.getSessionId()));

        // 事务外向 CCT 授权
        long clientId =
            targets.authorize(request.getAccountId(), request.getApplicationId(), request.getSubjectPartyId());

        // 按写入路由选择存储目标
        StorageWriteTarget target = registry.forWrite(FileSourceCodes.PLATFORM_SUPPORT);

        // 登记上传
        Upload upload = transactions.write(() -> createUpload(request, clientId, target));

        // 事务外签发上传授权
        Grant grant = authorizeUpload(upload);
        return new StartOnboardingUploadResponse(upload.uploadId(), grant, upload.expiresAt());
    }

    /** 事务内：锁定账号与唯一入驻 Tenant，按路由目标与当前有效策略登记 Upload。 */
    Upload createUpload(StartOnboardingUploadRequest request, long clientId, StorageWriteTarget target) {
        long tenantId = access.lockUploadTenant(request.getAccountId(), request.getSessionId(), clientId);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Start start = new Start(tenantId, request.getAccountId(),
            request.getApplicationId() + ":" + request.getSubjectPartyId(), FileSourceCodes.PLATFORM_SUPPORT,
            FileSourceCodes.CLIENT_ONBOARDING_MATERIAL, request.displayName(), request.getMimeType(),
            request.getSizeBytes());
        return files.create(ids.nextId(), start, target, now);
    }

    /** 事务外：由原 Upload 固定的实例与桶签发 PUT 授权，授权不得晚于 Upload 到期且必须为 HTTPS。 */
    Grant authorizeUpload(Upload upload) {
        Grant grant;
        try (FileObjectStorage storage = registry.openForWrite(upload.storageProvider(), upload.containerRef())) {
            grant = storage.authorize(upload);
        } catch (ServiceUnavailableException unavailable) {
            throw unavailable;
        } catch (RuntimeException unavailable) {
            throw new ServiceUnavailableException(unavailable);
        }
        if (!validGrant(grant, upload)) {
            throw new ServiceUnavailableException();
        }
        return grant;
    }

    /** 授权仍在有效期内、不晚于 Upload 到期，且 URL 为 HTTPS。 */
    boolean validGrant(Grant grant, Upload upload) {
        if (grant == null || grant.expiresAt() == null || grant.url() == null) {
            return false;
        }
        if (!clock.instant().isBefore(grant.expiresAt()) || grant.expiresAt().isAfter(upload.expiresAt())) {
            return false;
        }
        return grant.url().startsWith("https://");
    }
}
