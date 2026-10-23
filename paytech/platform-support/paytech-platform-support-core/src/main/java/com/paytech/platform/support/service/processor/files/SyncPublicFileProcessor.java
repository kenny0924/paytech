package com.paytech.platform.support.service.processor.files;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.filedocument.domain.file.FileModels.File;
import com.paytech.platform.support.filedocument.domain.file.FileModels.PublicSync;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.filedocument.integration.storage.FileObjectStorage;
import com.paytech.platform.support.filedocument.integration.storage.FileStorageRegistry;
import com.paytech.platform.support.service.processor.files.request.SyncPublicFileRequest;
import com.paytech.platform.support.service.processor.files.response.SyncPublicFileResponse;

import lombok.AllArgsConstructor;

/**
 * 公开文件替换结束后的同步：公开地址读的是存储最新版本，替换不通过、过期或作废后必须把当前版本恢复为最新版本
 * <li>持有 {@code file:replace:<fileId>}（与开始替换共用，立即尝试，未获取返回 409 由任务重试），比较与复制不会与新替换交错。</li>
 * <li>替换未结束（上传授权未到期或候选版本检查中）返回未完成；之后已开始新替换时由新替换的同步任务收敛，本任务直接完成。</li>
 * <li>最新版本即为当前版本或由其恢复复制得到时不再复制；只读本库事实与存储，不写数据库。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "files.enabled", havingValue = "true")
public class SyncPublicFileProcessor extends AbstractLockProcessor<SyncPublicFileRequest, SyncPublicFileResponse> {

    private final Clock clock;

    private final FileLifecycleService files;

    private final FileStorageRegistry registry;

    /** 与开始替换共用文件的替换锁。 */
    @Override
    protected LockCommand lockCommand(SyncPublicFileRequest request) {
        return LockCommand.tryLock(StartFileReplacementProcessor.replaceLock(request.getFileId()));
    }

    @Override
    protected SyncPublicFileResponse processWithinLock(SyncPublicFileRequest request) {
        // 判断替换是否结束、是否已被更新的替换接管
        PublicSync sync = files.publicSync(request.getFileId(), request.getUploadId(), clock.instant());
        if (!sync.ended()) {
            return new SyncPublicFileResponse(false, false);
        }
        if (sync.superseded()) {
            return new SyncPublicFileResponse(true, false);
        }

        // 事务外比较存储最新版本与当前版本，不一致则恢复
        return new SyncPublicFileResponse(true, restoreIfDiverged(sync.current()));
    }

    /** 最新版本不再对外提供当前版本时，把当前版本复制为最新版本。 */
    boolean restoreIfDiverged(File current) {
        try (FileObjectStorage storage = registry.openForRead(current.provider(), current.container())) {
            if (storage.latest(current.container(), current.objectRef()).serves(current.verified().objectVersion())) {
                return false;
            }
            storage.restore(current);
            return true;
        }
    }
}
