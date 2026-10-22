package com.paytech.platform.support.service.processor.files;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.filedocument.domain.file.FileModels.File;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.filedocument.integration.storage.FileDownloadStorage.Download;
import com.paytech.platform.support.filedocument.integration.storage.FileObjectStorage;
import com.paytech.platform.support.filedocument.integration.storage.FileStorageRegistry;
import com.paytech.platform.support.service.processor.files.request.AuthorizeFileDownloadRequest;
import com.paytech.platform.support.service.processor.files.response.AuthorizeFileDownloadResponse;

import lombok.AllArgsConstructor;

/**
 * 签发文件原版本的短时只读下载地址；内部系统之间不做文件级权限
 * <li>要求扫描 AVAILABLE、对象 ACTIVE；按文件记录上的实例与桶签发，实例或桶已停用返回 503 且不发起存储调用。</li>
 * <li>地址最长 60 秒，只回传给本次调用，不落库、不写审计；不写业务数据、不加锁、不开事务。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "files.enabled", havingValue = "true")
public class AuthorizeFileDownloadProcessor
    extends AbstractProcessor<AuthorizeFileDownloadRequest, AuthorizeFileDownloadResponse> {

    /** 下载授权的最长有效秒数。 */
    static final long DOWNLOAD_SECONDS = 60;

    private final Clock clock;

    private final FileLifecycleService files;

    private final FileStorageRegistry registry;

    @Override
    protected AuthorizeFileDownloadResponse processRequest(AuthorizeFileDownloadRequest request) {
        // 读取文件并确认可下载
        File file = files.file(request.getFileId());
        if (file.scanStatus() != FileScanStatus.AVAILABLE || file.objectStatus() != FileObjectStatus.ACTIVE) {
            throw new FailException(OnboardingFileErrorCode.FILE_STATE_CONFLICT);
        }

        // 事务外签发原版本授权
        Instant expiresAt = clock.instant().truncatedTo(ChronoUnit.MICROS).plusSeconds(DOWNLOAD_SECONDS);
        Download grant = sign(file, expiresAt);
        return new AuthorizeFileDownloadResponse(grant.url(), grant.expiresAt());
    }

    /** 按记录坐标签发；适配器失败或授权越界返回不可用。 */
    Download sign(File file, Instant expiresAt) {
        Download grant;
        try (FileObjectStorage storage = registry.openForRead(file.provider(), file.container())) {
            grant = storage.authorize(file, expiresAt);
        } catch (ServiceUnavailableException unavailable) {
            throw unavailable;
        } catch (RuntimeException unavailable) {
            throw new ServiceUnavailableException(unavailable);
        }
        if (grant == null || !grant.usableUntil(expiresAt, clock.instant())) {
            throw new ServiceUnavailableException();
        }
        return grant;
    }
}
