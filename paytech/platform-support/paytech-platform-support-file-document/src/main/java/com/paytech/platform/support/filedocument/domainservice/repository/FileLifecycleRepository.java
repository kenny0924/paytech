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

    /** 当前读锁住任一来源的原 Upload。 */
    FileUpload lockSourceUpload(long id);
    /** 文件元数据组合读取；不存在为空。 */
    FileDescription describeFile(long fileId);
    /** 版本上传登记的展示名；非上传来源为空。 */
    String versionDisplayName(long fileId, int versionNo);
    /** 锁住文件，防止失去 Lease 的旧工作者覆盖新事实。 */
    FileObject lockFile(long id);
    /** 读取版本固定策略的要求及该版本的终局结果。 */
    List<FileScanRequirement> requirements(long fileId,int versionNo,long policyId);
    /** 插入唯一文件。 */
    void insertFile(long id,Upload upload,Verified proof,Instant now);
    /** 原 Upload 状态 CAS。 */
    int completeUpload(long id,Instant now);
    /** 文件扫描状态更新，调用者必须已持有文件行锁。 */
    void scanState(long id,FileScanStatus status,Instant now);
    /** 镜像改为新版本；当前版本号必须仍为调用方读取的值。 */
    void promote(FileVersion version,int currentVersionNo,Instant now);
    /** 只追加同一检查的唯一终局结果。 */
    void insertResult(long id,int versionNo,long policyId,Result result,Instant now);
}
