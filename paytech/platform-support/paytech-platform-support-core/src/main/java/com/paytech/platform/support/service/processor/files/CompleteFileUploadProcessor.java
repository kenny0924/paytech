package com.paytech.platform.support.service.processor.files;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Verified;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Completed;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.filedocument.integration.storage.CompletedUploadVerifier;
import com.paytech.platform.support.service.handler.FileScanHandler;
import com.paytech.platform.support.service.processor.files.request.CompleteFileUploadRequest;
import com.paytech.platform.support.service.processor.files.response.CompleteFileUploadResponse;

import lombok.AllArgsConstructor;

/**
 * 完成内部文件上传：核对来源 → 已完成返回原回执 → 事务外核对存储 → 登记文件与扫描任务
 * <li>资源锁按 uploadId（Key 沿用 {@code file:upload:complete:<uploadId>}，滚动发布期间新旧实例互斥）；上传行当前读与状态 CAS
 * 继续保护锁租约失效后的竞争。</li>
 * <li>只有开始上传的来源系统可以完成，其他来源按不存在处理，防止误完成别的服务的上传。</li>
 * <li>存储核对按上传记录上的实例与桶读取，在事务外；文件登记与 FILE_SCAN 补偿任务在同一短事务提交。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "files.enabled", havingValue = "true")
public class CompleteFileUploadProcessor
    extends AbstractLockProcessor<CompleteFileUploadRequest, CompleteFileUploadResponse> {

    /** 获取资源锁的最长等待时间，超时返回并发冲突。 */
    static final Duration LOCK_WAIT_TIME = Duration.ofMillis(500);

    /** 资源锁的有限租约，覆盖一次存储核对与登记短事务。 */
    static final Duration LOCK_LEASE_TIME = Duration.ofSeconds(30);

    private final Clock clock;

    private final IdGenerator ids;

    private final FileTransactions transactions;

    private final FileLifecycleService files;

    private final CompletedUploadVerifier verifier;

    private final CompensationEventRegistrar events;

    /** 按 uploadId 加资源锁：一次上传只产生一个文件。 */
    @Override
    protected LockCommand lockCommand(CompleteFileUploadRequest request) {
        return LockCommand.tryLocks(List.of("file:upload:complete:" + request.getUploadId()), LOCK_WAIT_TIME,
            LOCK_LEASE_TIME);
    }

    @Override
    protected CompleteFileUploadResponse processWithinLock(CompleteFileUploadRequest request) {
        // 读取原上传并核对来源
        Upload upload = requireSource(files.sourceUpload(request.getUploadId()), request);

        // 已完成返回原回执
        Completed completed = files.completedFile(upload);
        if (completed != null) {
            return response(request, completed.fileId(), completed.completedAt());
        }
        files.requireOpen(upload, clock.instant());

        // 事务外核对存储
        Verified verified = verifier.verify(upload);

        // 登记文件与扫描任务
        return transactions.write(() -> register(request, verified));
    }

    /** 事务内：当前读锁定原上传，并发下已完成则返回原回执，否则登记文件并注册扫描任务。 */
    CompleteFileUploadResponse register(CompleteFileUploadRequest request, Verified verified) {
        Upload current = requireSource(files.lockSourceUpload(request.getUploadId()), request);
        Completed completed = files.completedFile(current);
        if (completed != null) {
            return response(request, completed.fileId(), completed.completedAt());
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        long fileId = ids.nextId();
        files.complete(fileId, current, verified, now);
        events.register(FileScanHandler.buildEvent(fileId));
        return response(request, fileId, now);
    }

    /** 来源系统不一致按不存在处理。 */
    Upload requireSource(Upload upload, CompleteFileUploadRequest request) {
        if (!request.getSourceSystem().equals(upload.sourceSystem())) {
            throw new FailException(FileErrorCode.FILE_NOT_FOUND);
        }
        return upload;
    }

    /** 完成操作身份即 uploadId，首次与重复完成返回同一组值。 */
    CompleteFileUploadResponse response(CompleteFileUploadRequest request, long fileId, Instant completedAt) {
        return new CompleteFileUploadResponse(request.getUploadId(), fileId, completedAt);
    }
}
