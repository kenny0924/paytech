package com.paytech.platform.support.filedocument.domainservice.service;
import java.time.Instant;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.domain.file.FileModels.*;
/** 文件 Owner 发布的同步能力；调用者持有本地短事务。 */
public interface FileLifecycleService {
    /** 返回准确原文件和上传绑定，不选择最新申请或修改扫描任务。 */
    Owned read(long fileId,long accountId);

    /** 读取原账号的原 Upload，不替换为最新申请。 */
    Upload owned(long uploadId,long accountId);
    /** 锁定并校验冻结目标，允许先检查原命令回执。 */
    Upload lockOwned(long uploadId,long accountId,long tenantId);
    /** 新完成操作只允许有效且未完成的 Upload。 */
    void requireOpen(Upload upload,Instant now);
    /** 原子创建 File、Owner 并关闭原 Upload。 */
    void complete(long fileId,long ownerRowId,Upload upload,Verified proof,Instant now);
    /** 锁定文件，恢复原策略的尚未完成要求。 */
    Scan beginScan(long fileId,Instant now);
    /** 只追加原检查终局；同一身份已存在时不让旧工作者覆盖。 */
    void recordResult(long resultId,Result result,Instant now);
}
