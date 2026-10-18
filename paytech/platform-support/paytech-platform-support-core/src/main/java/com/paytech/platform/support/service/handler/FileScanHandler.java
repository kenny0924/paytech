package com.paytech.platform.support.service.handler;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.compensation.api.*;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.filedocument.domain.file.FileModels.File;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Requirement;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Result;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.filedocument.integration.scanning.FileScanner;
import com.paytech.platform.support.service.processor.OperationsTransactionExecutor;
/** 持久化文件扫描入口；恢复同一次检查，不把补偿执行次数当作新的扫描尝试。 */
@Component
@lombok.AllArgsConstructor
@ConditionalOnProperty(name="files.onboarding.enabled",havingValue="true")
public final class FileScanHandler implements CompensationEventHandler {
    public static final String EVENT_TYPE="FILE_SCAN";
    private final FileLifecycleService files;
    private final OperationsTransactionExecutor transactions;
    private final ObjectProvider<FileScanner> scanners;
    private final IdGenerator ids;
    private final Clock clock;
    /** 与 File 原子登记，只保存可恢复身份，不复制资料或存储信息。 */
    public static CompensationEventRequest buildEvent(long fileId) {
        if (fileId<=0) throw new IllegalArgumentException("Invalid file identity");
        return new CompensationEventRequest(Long.toString(fileId),EVENT_TYPE,"-");
    }
    /** 固定持久化类型，不因类名修改而改变。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
    /** 依赖失败有界退避；耗尽后保留 FAILED 由既有指标暴露。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(25), 12, Duration.ofSeconds(5),
            Duration.ofMinutes(30), 2, 0.2);
    }
    /** 扫描远程调用必须在事务外，终局结果在短事务中追加。 */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) return invalid();
        try {
            if (!event.eventKey().matches("[1-9][0-9]{0,18}") || event.payloadJson()!=null) return invalid();
            long fileId=Long.parseLong(event.eventKey());
            while (true) {
                var scan=transactions.write(() -> files.beginScan(fileId,clock.instant()));
                if (scan.pending().isEmpty()) {
                    return CompensationHandlerResult.success();
                }
                var requirement=scan.pending().getFirst();
                var matching=scanners.orderedStream().filter(s -> requirement.scannerCode().equals(s.scannerCode())).toList();
                if (matching.size() != 1) {
                    return retry();
                }
                var adapter=matching.getFirst();
                var result=adapter.recoverOrScan(scan.file(),requirement,1);
                if (!valid(scan.file(),requirement,result) || !adapter.acceptsRuntime(requirement.minimumVersion(),result.runtimeVersion())) return retry();
                transactions.write(() -> files.recordResult(ids.nextId(),result,clock.instant()));
            }
        } catch (NumberFormatException invalid) {
            return invalid();
        } catch (RuntimeException unavailable) {
            return retry();
        }
    }
    /** 只接受原版本、原摘要与原检查身份的完整终局证据。 */
    private boolean valid(File file,Requirement requirement,Result result) {
        return result!=null && result.fileId()==file.fileId() && result.requirementId()==requirement.id() && result.attemptNo()==1
            && file.verified().objectVersion().equals(result.objectVersion()) && file.verified().contentHash().equals(result.contentHash())
            && result.runtimeVersion()!=null && !result.runtimeVersion().isBlank() && result.runtimeVersion().length()<=64
            && result.resultCode()!=null && result.resultCode().matches("[A-Z][A-Z0-9_]{0,63}")
            && result.scannedAt()!=null && !result.scannedAt().isAfter(clock.instant());
    }
    /** 错误摘要不复制供应商响应或敏感字段。 */
    private CompensationHandlerResult retry() {
        return CompensationHandlerResult.retryableFailure("FILE_SCAN_UNAVAILABLE", "File scan requires recovery");
    }
    /** 不可恢复的任务身份损坏明确留下失败记录。 */
    private CompensationHandlerResult invalid() {
        return CompensationHandlerResult.terminalFailure("FILE_SCAN_IDENTITY_INVALID",
            "File scan identity requires investigation");
    }
}
