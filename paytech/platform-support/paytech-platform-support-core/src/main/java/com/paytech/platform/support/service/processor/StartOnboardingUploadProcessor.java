package com.paytech.platform.support.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientOnboardingAccessService;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerAuthenticationService;
import com.paytech.platform.support.core.integration.files.CctFileTargetClient;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Grant;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Start;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.domainservice.service.FileUploadService;
import com.paytech.platform.support.filedocument.integration.storage.UploadStorage;
import com.paytech.platform.support.service.processor.request.StartOnboardingUploadRequest;
import com.paytech.platform.support.service.processor.response.StartOnboardingUploadResponse;

import lombok.AllArgsConstructor;

/**
 * 开始入驻文件上传：锁定上传目标 → 校验会话 → 事务外向 CCT 授权 → 登记 Upload → 事务外签发 PUT 授权。
 * <li>资源锁按账号、申请与主体串行同一上传目标；账号行锁在 Lease 失效后继续串行 Tenant 解析。</li>
 * <li>每次请求新建 Upload，没有可供比较的原业务操作身份；HTTP 请求重放由 Gateway 处理。</li>
 * <li>会话查询、CCT 授权与存储签发均在数据库事务之外；IAM 锁定与 Upload 登记在同一本地事务提交。</li>
 * <li>存储配置在登记事务内使用时读取；配置无效或适配器不唯一时返回不可用，不签发授权。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "files.onboarding.enabled", havingValue = "true")
public class StartOnboardingUploadProcessor
    extends AbstractLockProcessor<StartOnboardingUploadRequest, StartOnboardingUploadResponse> {

    /** 展示文件名的最大 Unicode 字符数。 */
    static final int MAX_FILENAME_CODE_POINTS = 255;

    /** 未配置上传有效期时使用的秒数。 */
    static final int DEFAULT_UPLOAD_SECONDS = 900;

    private final Clock clock;

    private final IdGenerator ids;

    private final Environment environment;

    private final FileUploadService files;

    private final CctFileTargetClient targets;

    private final ObjectProvider<UploadStorage> adapters;

    private final ClientOnboardingAccessService access;

    private final CustomerAuthenticationService authentication;

    private final OperationsTransactionExecutor transactions;

    /** 文件名长度与归一化后的展示名只依赖输入，在加锁前拒绝。 */
    @Override
    protected void validate(StartOnboardingUploadRequest request) {
        String filename = request.getFilename();
        if (filename.codePointCount(0, filename.length()) > MAX_FILENAME_CODE_POINTS) {
            throw new InvalidParameterException("文件名最多 255 个字符");
        }
        String displayName = request.displayName();
        if (displayName.isBlank() || ".".equals(displayName) || "..".equals(displayName)) {
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
        List<UploadStorage> available = adapters.orderedStream().toList();

        // 登记上传
        Upload upload = transactions.write(() -> createUpload(request, clientId, available));

        // 事务外签发上传授权
        Grant grant = authorizeUpload(upload, available);
        return new StartOnboardingUploadResponse(upload.uploadId(), grant, upload.expiresAt());
    }

    /** 事务内：锁定账号与唯一入驻 Tenant，确认存储配置后按当前有效策略登记 Upload。 */
    Upload createUpload(StartOnboardingUploadRequest request, long clientId, List<UploadStorage> available) {
        long tenantId = access.lockUploadTenant(request.getAccountId(), request.getSessionId(), clientId);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String provider = environment.getProperty("files.onboarding.storage-provider", "");
        String container = environment.getProperty("files.onboarding.storage-container-ref", "");
        String policy = environment.getProperty("files.onboarding.scan-policy-code", "");
        int lifetime = environment.getProperty("files.onboarding.upload-seconds", Integer.class, DEFAULT_UPLOAD_SECONDS);
        if (!validStorageSettings(provider, container, policy, lifetime, available)) {
            throw new ServiceUnavailableException();
        }
        Start start = new Start(request.getApplicationId(), request.getSubjectPartyId(), request.displayName(),
            request.getMimeType(), request.getSizeBytes());
        return files.create(ids.nextId(), request.getAccountId(), tenantId, start, policy, provider, container, now,
            now.plusSeconds(lifetime));
    }

    /** 供应商与策略代码格式合法、容器有界、有效期 60..900 秒，且恰有一个对应的存储适配器。 */
    boolean validStorageSettings(String provider, String container, String policy, int lifetime,
        List<UploadStorage> available) {
        if (!provider.matches("[A-Z][A-Z0-9_]{0,63}") || !policy.matches("[A-Z][A-Z0-9_]{0,63}")) {
            return false;
        }
        if (container.isBlank() || container.length() > 255 || lifetime < 60 || lifetime > 900) {
            return false;
        }
        return available.stream().filter(adapter -> provider.equals(adapter.providerCode())).count() == 1;
    }

    /** 事务外：由原 Upload 固定的存储适配器签发 PUT 授权，授权不得晚于 Upload 到期且必须为 HTTPS。 */
    Grant authorizeUpload(Upload upload, List<UploadStorage> available) {
        List<UploadStorage> matching =
            available.stream().filter(adapter -> upload.storageProvider().equals(adapter.providerCode())).toList();
        if (matching.size() != 1) {
            throw new ServiceUnavailableException();
        }
        Grant grant;
        try {
            grant = matching.getFirst().authorize(upload);
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
