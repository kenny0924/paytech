package com.paytech.platform.support.core.reliability.service;

import java.time.Instant;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileModels.FileCompletionReceipt;

/** Client 文件命令复用 Platform 现有幂等表，隔离 Operations 命名空间。 */
public interface ClientFileCommandService {
    /** 在本地事务外计算受控 HMAC，不把文件名原文写入幂等表。 */
    Prepared prepare(long accountId, String key, Object request);
    /** 在调用者事务内读取同内容原回执，异内容明确冲突。 */
    Long previous(Prepared command);
    /** 与 Upload 在同一事务保存稳定结果。 */
    void complete(Prepared command, long uploadId, Instant now);
    /** 完成命令的独立操作命名空间，HMAC 位于事务外。 */
    Prepared prepareCompletion(long accountId,String key,long uploadId);
    /** 读取原成功完成回执。 */
    FileCompletionReceipt previousCompletion(Prepared command);
    /** 与 File/Owner/扫描任务一起保存不可变完成回执。 */
    FileCompletionReceipt completeFile(Prepared command,long uploadId,long fileId,Instant now);
    /** 仅组合根内使用的不可变命令身份。 */
    record Prepared(String callerScope, String key, String requestHash,String operation) {
        /** 不把摘要和调用方命令身份打印到普通日志。 */
        @Override public String toString() { return "Prepared[REDACTED]"; }
    }
}
