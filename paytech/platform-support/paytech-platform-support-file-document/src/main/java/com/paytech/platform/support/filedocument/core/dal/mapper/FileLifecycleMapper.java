package com.paytech.platform.support.filedocument.core.dal.mapper;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.paytech.platform.support.filedocument.core.dal.dataobject.*;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.domain.file.FileModels.*;
import com.paytech.platform.support.api.enums.FileScanStatus;
/** 文件登记与扫描仅操作本服务数据库。 */
public interface FileLifecycleMapper {
    /** 文件当前状态的纯查询。 */
    FileObject readFile(@Param("id") long id);

    /** 上传已产生的唯一文件；未完成为空。 */
    CompletedUploadFile completedFile(@Param("uploadId") long uploadId);
    /** 当前读锁住任一来源的原 Upload。 */
    FileUpload lockSourceUpload(@Param("id") long id);
    /** 文件与上传记录组合的元数据。 */
    com.paytech.platform.support.filedocument.domain.file.StoredFileView describeFile(@Param("fileId") long fileId);
    /** 锁住文件，防止失去 Lease 的旧工作者覆盖新事实。 */
    FileObject lockFile(@Param("id") long id);
    /** 读取固定策略和要求及终局结果。 */
    List<FileScanRequirement> requirements(@Param("fileId") long fileId,@Param("policyId") long policyId);
    /** 插入唯一文件。 */
    void insertFile(@Param("id") long id,@Param("upload") Upload upload,@Param("proof") Verified proof,@Param("now") Instant now);
    /** 原 Upload 状态 CAS。 */
    int completeUpload(@Param("id") long id,@Param("now") Instant now);
    /** 文件扫描状态更新，调用者必须已持有文件行锁。 */
    void scanState(@Param("id") long id,@Param("status") FileScanStatus status,@Param("now") Instant now);
    /** 只追加同一检查的唯一终局结果。 */
    void insertResult(@Param("id") long id,@Param("policyId") long policyId,@Param("result") Result result,@Param("now") Instant now);
}
