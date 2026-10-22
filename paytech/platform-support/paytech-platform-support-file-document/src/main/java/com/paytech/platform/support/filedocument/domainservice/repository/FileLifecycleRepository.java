package com.paytech.platform.support.filedocument.domainservice.repository;
import java.time.Instant;
import java.util.List;
import com.paytech.platform.support.filedocument.core.dal.dataobject.*;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.domain.file.FileModels.*;
import com.paytech.platform.support.api.enums.FileScanStatus;
/** 文件登记与扫描仅操作本服务数据库。 */
public interface FileLifecycleRepository {
    /** 文件当前状态的纯查询。 */
    FileObject readFile(long id);

    /** 上传已产生的唯一文件；未完成为空。 */
    CompletedUploadFile completedFile(long uploadId);
    /** 当前读锁住任一来源的原 Upload。 */
    FileUpload lockSourceUpload(long id);
    /** 文件与上传记录组合的元数据；不存在为空。 */
    com.paytech.platform.support.filedocument.domain.file.StoredFileView describeFile(long fileId);
    /** 锁住文件，防止失去 Lease 的旧工作者覆盖新事实。 */
    FileObject lockFile(long id);
    /** 读取固定策略和要求及终局结果。 */
    List<FileScanRequirement> requirements(long fileId,long policyId);
    /** 插入唯一文件。 */
    void insertFile(long id,Upload upload,Verified proof,Instant now);
    /** 原 Upload 状态 CAS。 */
    int completeUpload(long id,Instant now);
    /** 文件扫描状态更新，调用者必须已持有文件行锁。 */
    void scanState(long id,FileScanStatus status,Instant now);
    /** 只追加同一检查的唯一终局结果。 */
    void insertResult(long id,long policyId,Result result,Instant now);
}
