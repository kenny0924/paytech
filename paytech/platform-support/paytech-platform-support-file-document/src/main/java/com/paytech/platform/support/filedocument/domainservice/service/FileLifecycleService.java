package com.paytech.platform.support.filedocument.domainservice.service;
import java.time.Instant;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Completed;
import com.paytech.platform.support.filedocument.domain.file.StoredFileView;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.domain.file.FileModels.*;
/** 文件 Owner 发布的同步能力；调用者持有本地短事务。 */
public interface FileLifecycleService {
    /** 读取任一来源的原 Upload；不存在返回 FILE_NOT_FOUND。 */
    Upload sourceUpload(long uploadId);
    /** 当前读锁住任一来源的原 Upload；不存在返回 FILE_NOT_FOUND。 */
    Upload lockSourceUpload(long uploadId);
    /** 按文件 ID 查询元数据；不存在返回 FILE_NOT_FOUND。 */
    StoredFileView describe(long fileId);
    /** 按文件 ID 读取不可变坐标与当前状态；不存在返回 FILE_NOT_FOUND。 */
    File file(long fileId);

    /** 已完成 Upload 产生的唯一文件，供重复完成返回原结果；未完成为空，已完成却缺文件视为数据不一致。 */
    Completed completedFile(Upload upload);
    /** 新完成操作只允许有效且未完成的 Upload。 */
    void requireOpen(Upload upload,Instant now);
    /** 原子创建 File 并关闭原 Upload。 */
    void complete(long fileId,Upload upload,Verified proof,Instant now);
    /** 锁定文件，恢复原策略的尚未完成要求。 */
    Scan beginScan(long fileId,Instant now);
    /** 只追加原检查终局；同一身份已存在时不让旧工作者覆盖。 */
    void recordResult(long resultId,Result result,Instant now);
}
