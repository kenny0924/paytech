package com.paytech.platform.support.service.processor.files;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.file.FileDisplayName;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Replaceable;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Grant;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.ReplacementStart;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.filedocument.domainservice.service.FileUploadService;
import com.paytech.platform.support.filedocument.integration.storage.FileObjectStorage;
import com.paytech.platform.support.filedocument.integration.storage.FileStorageRegistry;
import com.paytech.platform.support.filedocument.integration.storage.LatestVersion;
import com.paytech.platform.support.service.handler.FilePublicSyncHandler;
import com.paytech.platform.support.service.processor.files.request.StartFileReplacementRequest;
import com.paytech.platform.support.service.processor.files.response.StartFileUploadResponse;

import lombok.AllArgsConstructor;

/**
 * 开始替换文件内容：核对来源与可替换状态 → 事务外读取存储最新版本 → 短事务登记替换上传 → 事务外签发只签 Content-Type 的 PUT
 * <li>资源锁 {@code file:replace:<fileId>}（立即尝试，未获取返回 409），与公开同步任务的恢复复制共用，二者不会交错执行；
 * 同一文件同时只允许一个未结束的替换（数据库事实判断），唯一键 (实例, 桶, 对象名, 替换序号) 兜底。</li>
 * <li>公开文件要求存储中的最新版本仍对外提供当前版本（即为当前版本或由其恢复复制），否则说明上一次替换的同步尚未完成，返回 409；
 * 公开文件的替换上传与 FILE_PUBLIC_SYNC 任务在同一短事务登记。</li>
 * <li>内部 HTTP 入口与 Platform 内部模块（来源系统 platform-support）共用；新内容按原文件策略代码当前的 ACTIVE 修订核对。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "files.enabled", havingValue = "true")
public class StartFileReplacementProcessor
    extends AbstractLockProcessor<StartFileReplacementRequest, StartFileUploadResponse> {

    private final Clock clock;

    private final IdGenerator ids;

    private final FileTransactions transactions;

    private final FileUploadService uploads;

    private final FileLifecycleService files;

    private final FileStorageRegistry registry;

    private final CompensationEventRegistrar events;

    /** 替换锁：同一文件的开始替换与公开恢复复制互斥。 */
    public static String replaceLock(long fileId) {
        return "file:replace:" + fileId;
    }

    /** 文件名长度与归一化后的展示名只依赖输入。 */
    @Override
    protected void validate(StartFileReplacementRequest request) {
        if (!FileDisplayName.withinLimit(request.getFilename())) {
            throw new InvalidParameterException("文件名最多 255 个字符");
        }
        if (!FileDisplayName.usable(request.displayName())) {
            throw new InvalidParameterException("文件展示名无效");
        }
    }

    /** 立即尝试获取文件的替换锁。 */
    @Override
    protected LockCommand lockCommand(StartFileReplacementRequest request) {
        return LockCommand.tryLock(replaceLock(request.getFileId()));
    }

    @Override
    protected StartFileUploadResponse processWithinLock(StartFileReplacementRequest request) {
        // 核对来源、文件状态与未结束的替换
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Replaceable file = files.replaceable(request.getFileId(), request.getSourceSystem(), now);
        int uploadSeconds = registry.replacementUploadSeconds(file.sourceSystem(), file.accessLevel());

        try (FileObjectStorage storage = registry.openForWrite(file.provider(), file.container())) {
            // 事务外读取存储最新版本；公开文件须已与当前版本同步
            LatestVersion latest = storage.latest(file.container(), file.objectRef());
            requireSynchronized(file, latest);

            // 登记替换上传（公开文件同时登记公开同步任务）
            ReplacementStart start = new ReplacementStart(file, latest.versionId(), request.getUploaderAccountId(),
                request.displayName(), request.getMimeType(), request.getSizeBytes(), uploadSeconds);
            Upload upload = transactions.write(() -> register(start, now));

            // 事务外签发 PUT 授权
            return new StartFileUploadResponse(upload.uploadId(), authorize(storage, upload), upload.expiresAt());
        }
    }

    /** 公开地址读的是存储最新版本：它必须仍对外提供文件的当前版本。 */
    void requireSynchronized(Replaceable file, LatestVersion latest) {
        if (file.accessLevel() == FileAccessLevel.PUBLIC && !latest.serves(file.currentObjectVersion())) {
            throw new FailException(FileErrorCode.FILE_STATE_CONFLICT,
                "公开文件的上一次替换尚未完成同步，请稍后重试: " + file.fileId());
        }
    }

    /** 事务内：登记替换上传；公开文件同时登记 FILE_PUBLIC_SYNC。 */
    Upload register(ReplacementStart start, Instant now) {
        Upload upload = uploads.createReplacement(ids.nextId(), start, now);
        if (upload.accessLevel() == FileAccessLevel.PUBLIC) {
            events.register(FilePublicSyncHandler.buildEvent(upload.replacesFileId(), upload.uploadId()));
        }
        return upload;
    }

    /** 签发只签 Content-Type 的 PUT 授权；适配器失败或授权越界返回不可用。 */
    Grant authorize(FileObjectStorage storage, Upload upload) {
        Grant grant;
        try {
            grant = storage.authorize(upload);
        } catch (ServiceUnavailableException unavailable) {
            throw unavailable;
        } catch (RuntimeException unavailable) {
            throw new ServiceUnavailableException(unavailable);
        }
        if (grant == null || !grant.usableFor(upload, clock.instant())) {
            throw new ServiceUnavailableException();
        }
        return grant;
    }
}
