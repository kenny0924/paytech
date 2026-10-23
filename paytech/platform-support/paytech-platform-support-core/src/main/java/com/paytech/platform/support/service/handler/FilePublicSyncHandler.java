package com.paytech.platform.support.service.handler;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.platform.support.service.processor.files.SyncPublicFileProcessor;
import com.paytech.platform.support.service.processor.files.request.SyncPublicFileRequest;

import lombok.AllArgsConstructor;

/**
 * 公开文件替换的同步任务：开始替换时与替换上传原子登记，替换结束后保证公开地址读到的是文件当前版本
 * <li>事件键 {@code fileId:uploadId}；替换未结束返回可重试，结束后比较并按需恢复，成功即删除任务。</li>
 * <li>重试窗口覆盖上传期限（最长 900 秒）与候选版本检查：首次 30 秒、最长 5 分钟退避、最多 30 次（约 2 小时），耗尽后保留 FAILED。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "files.enabled", havingValue = "true")
public final class FilePublicSyncHandler implements CompensationEventHandler {

    /** 持久化事件类型。 */
    public static final String EVENT_TYPE = "FILE_PUBLIC_SYNC";

    private final SyncPublicFileProcessor processor;

    /** 与替换上传原子登记，只保存可恢复身份。 */
    public static CompensationEventRequest buildEvent(long fileId, long uploadId) {
        if (fileId <= 0 || uploadId <= 0) {
            throw new IllegalArgumentException("Invalid public file sync identity");
        }
        return new CompensationEventRequest(fileId + ":" + uploadId, EVENT_TYPE, "-");
    }

    /** 固定持久化类型，不因类名修改而改变。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 重试窗口覆盖上传期限与候选版本检查。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(25), 30, Duration.ofSeconds(30),
            Duration.ofMinutes(5), 2, 0.2);
    }

    /** 存储比较与复制在事务外由同步 Processor 完成；替换锁被占用时稍后重试。 */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive() || event.payloadJson() != null
            || !event.eventKey().matches("[1-9][0-9]{0,18}:[1-9][0-9]{0,18}")) {
            return invalid();
        }
        String[] identity = event.eventKey().split(":");
        try {
            var response = processor.process(new SyncPublicFileRequest(Long.parseLong(identity[0]),
                Long.parseLong(identity[1])));
            return response.isFinished() ? CompensationHandlerResult.success() : pending();
        } catch (NumberFormatException overflow) {
            return invalid();
        } catch (ConcurrentRequestException locked) {
            return pending();
        } catch (FailException notFound) {
            return invalid();
        } catch (RuntimeException unavailable) {
            return CompensationHandlerResult.retryableFailure("FILE_PUBLIC_SYNC_UNAVAILABLE",
                "Public file sync requires recovery");
        }
    }

    /** 替换尚未结束或替换锁被占用。 */
    private CompensationHandlerResult pending() {
        return CompensationHandlerResult.retryableFailure("FILE_PUBLIC_SYNC_PENDING",
            "Public file replacement not finished");
    }

    /** 不可恢复的任务身份损坏明确留下失败记录。 */
    private CompensationHandlerResult invalid() {
        return CompensationHandlerResult.terminalFailure("FILE_PUBLIC_SYNC_IDENTITY_INVALID",
            "Public file sync identity requires investigation");
    }
}
