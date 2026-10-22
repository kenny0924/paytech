package com.paytech.platform.support.service.processor;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientOnboardingAccessService;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerAuthenticationService;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.core.integration.files.CctFileTargetClient;
import com.paytech.platform.support.filedocument.domain.file.FileModels.File;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Owned;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.filedocument.integration.storage.FileDownloadStorage.Download;
import com.paytech.platform.support.filedocument.integration.storage.FileObjectStorage;
import com.paytech.platform.support.filedocument.integration.storage.FileStorageRegistry;
import com.paytech.platform.support.service.processor.request.OnboardingFileAccessRequest;
import com.paytech.platform.support.service.processor.response.DownloadOnboardingFileResponse;

/**
 * 客户下载本人入驻文件：授权 → 事务外签发准确版本的短时 GET 授权 → 网络等待后重新授权并核对文件未变。
 * <li>每次请求独立签发，最长 60 秒；授权只回传给本次调用，不落库、不写审计。</li>
 * <li>不写业务数据、不加锁；CCT 与存储调用均在本库只读事务之外。</li>
 * <li>签发前后文件事实不一致返回状态冲突；签发结果越界或非 HTTPS 返回不可用。</li>
 */
@Component
@ConditionalOnProperty(name = "files.onboarding.enabled", havingValue = "true")
public class DownloadOnboardingFileProcessor
    extends AbstractOnboardingFileAccessProcessor<DownloadOnboardingFileResponse> {

    /** 下载授权的最长有效秒数。 */
    static final long DOWNLOAD_SECONDS = 60;

    /** 下载 URL 的最大长度。 */
    static final int MAX_URL_LENGTH = 8192;

    private final Clock clock;

    private final FileStorageRegistry registry;

    /** 注入公共授权步骤与下载签发的依赖。 */
    public DownloadOnboardingFileProcessor(Clock clock, CctFileTargetClient targets, FileLifecycleService files,
        FileStorageRegistry registry, ClientOnboardingAccessService access,
        CustomerAuthenticationService authentication, OperationsTransactionExecutor transactions) {
        super(targets, files, access, authentication, transactions);
        this.clock = clock;
        this.registry = registry;
    }

    @Override
    protected DownloadOnboardingFileResponse processRequest(OnboardingFileAccessRequest request) {
        // 授权并确认可下载
        Owned owned = authorized(request);
        requireDownloadable(owned);

        // 事务外签发下载授权
        Instant expiresAt = clock.instant().truncatedTo(ChronoUnit.MICROS).plusSeconds(DOWNLOAD_SECONDS);
        Download grant = requestDownload(owned.file(), expiresAt);

        // 网络等待后重新授权并核对文件未变
        Owned current = authorized(request);
        requireDownloadable(current);
        if (!owned.file().equals(current.file())) {
            throw new FailException(OnboardingFileErrorCode.FILE_STATE_CONFLICT);
        }

        // 校验签发结果
        requireValidGrant(grant, expiresAt);
        return new DownloadOnboardingFileResponse(grant.url(), grant.expiresAt());
    }

    /** 事务外按原文件记录的实例与桶签发原版本的只读授权；实例或桶不可读、适配器失败统一报告不可用并保留原因。 */
    Download requestDownload(File file, Instant expiresAt) {
        try (FileObjectStorage storage = registry.openForRead(file.provider(), file.container())) {
            return storage.authorize(file, expiresAt);
        } catch (ServiceUnavailableException unavailable) {
            throw unavailable;
        } catch (RuntimeException unavailable) {
            throw new ServiceUnavailableException(unavailable);
        }
    }

    /** 授权仍有效且不晚于签发期限，URL 为有主机、无用户信息与片段的有界 HTTPS 地址。 */
    void requireValidGrant(Download grant, Instant expiresAt) {
        if (grant == null || grant.expiresAt() == null) {
            throw new ServiceUnavailableException();
        }
        if (!clock.instant().isBefore(grant.expiresAt()) || grant.expiresAt().isAfter(expiresAt)) {
            throw new ServiceUnavailableException();
        }
        if (!safeUrl(grant.url())) {
            throw new ServiceUnavailableException();
        }
    }

    /** HTTPS、有主机、无用户信息与片段，长度不超过上限；无法解析视为不安全。 */
    boolean safeUrl(String url) {
        if (url == null || url.length() > MAX_URL_LENGTH) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null
                && uri.getFragment() == null;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
