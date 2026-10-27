package com.paytech.platform.support.filedocument.core.dal.mapper;
import java.time.Instant;
import java.util.Collection;
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

    /** 当前读锁住任一来源的原 Upload。 */
    FileUpload lockSourceUpload(@Param("id") long id);
    /** 文件、当前版本上传、首次上传与存储桶基础地址组合的元数据。 */
    FileDescription describeFile(@Param("fileId") long fileId);
    /** 同一组合读取的批量形式；只返回存在的文件，调用方保证集合非空。 */
    List<FileDescription> describeFiles(@Param("fileIds") Collection<Long> fileIds);
    /** 版本上传登记的展示名；非上传来源为空。 */
    String versionDisplayName(@Param("fileId") long fileId, @Param("versionNo") int versionNo);
    /** 锁住文件，防止失去 Lease 的旧工作者覆盖新事实。 */
    FileObject lockFile(@Param("id") long id);
    /** 读取版本固定策略的要求及该版本的终局结果。 */
    List<FileScanRequirement> requirements(@Param("fileId") long fileId,@Param("versionNo") int versionNo,@Param("policyId") long policyId);
    /** 插入唯一文件，当前版本为 1。 */
    void insertFile(@Param("id") long id,@Param("upload") Upload upload,@Param("proof") Verified proof,@Param("now") Instant now);
    /** 原 Upload 状态 CAS。 */
    int completeUpload(@Param("id") long id,@Param("now") Instant now);
    /** 文件扫描状态更新，调用者必须已持有文件行锁。 */
    void scanState(@Param("id") long id,@Param("status") FileScanStatus status,@Param("now") Instant now);
    /** 把镜像改为已通过检查的新版本并递增当前版本号，调用者必须已持有文件行锁。 */
    int promote(@Param("version") FileVersion version,@Param("currentVersionNo") int currentVersionNo,@Param("now") Instant now);
    /** 只追加同一检查的唯一终局结果。 */
    void insertResult(@Param("id") long id,@Param("versionNo") int versionNo,@Param("policyId") long policyId,@Param("result") Result result,@Param("now") Instant now);
}
