package com.paytech.platform.support.filedocument.domainservice.service;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
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
    /** 按文件 ID 查询当前版本元数据、访问级别与公开地址；不存在返回 FILE_NOT_FOUND。 */
    StoredFileView describe(long fileId);
    /**
     * 按文件 ID 批量查询元数据，一次数据库读取
     *
     * @param fileIds 文件 ID，可为空集合
     * @return 文件 ID 到元数据；不存在的文件不出现在结果中
     */
    Map<Long, StoredFileView> describeAll(Collection<Long> fileIds);
    /** 按文件 ID 读取当前版本坐标与状态；不存在返回 FILE_NOT_FOUND。 */
    File file(long fileId);
    /**
     * 可下载的版本坐标与展示名
     * <li>versionNo 为空时取当前版本，要求扫描 AVAILABLE、对象 ACTIVE；指定版本时要求该版本为 CURRENT 或 SUPERSEDED 且检查
     * AVAILABLE、对象 ACTIVE。不满足返回 FILE_STATE_CONFLICT，文件或版本不存在返回 FILE_NOT_FOUND。</li>
     */
    Downloadable downloadable(long fileId, Integer versionNo);

    /** 已完成 Upload 产生的唯一文件版本，供重复完成返回原结果；未完成为空，已完成却缺版本视为数据不一致。 */
    Completed completedFile(Upload upload);
    /** 新完成操作只允许有效且未完成的 Upload。 */
    void requireOpen(Upload upload,Instant now);
    /** 原子创建 File 与 v1 候选版本并关闭原 Upload；v1 版本主键取 fileId。 */
    void complete(long fileId,Upload upload,Verified proof,Instant now);
    /**
     * 原子关闭替换 Upload 并登记新的候选版本；文件当前版本不变
     *
     * @param versionId 预先生成的版本主键
     * @return 新版本号
     */
    int completeReplacement(long versionId,Upload upload,Verified proof,Instant now);
    /** 作废尚未完成的替换上传（核对发现多余写入）；状态已变化时不改。 */
    void cancelReplacement(Upload upload,Instant now);
    /**
     * 核对文件可被来源系统替换并返回替换沿用的事实
     * <li>其他来源或不存在返回 FILE_NOT_FOUND；文件未 AVAILABLE、对象非 ACTIVE、已有未结束的替换（上传未完成或候选版本检查中）
     * 返回 FILE_STATE_CONFLICT。</li>
     */
    Replaceable replaceable(long fileId,String sourceSystem,Instant now);
    /** 锁定文件与版本，恢复该版本冻结策略的尚未完成要求。 */
    Scan beginScan(long fileId,int versionNo,Instant now);
    /** 只追加该版本原检查终局；同一身份已存在时不让旧工作者覆盖；通过后提升或拒绝该版本。 */
    void recordResult(long resultId,int versionNo,Result result,Instant now);
    /**
     * 公开文件替换的同步判断：替换是否已结束、是否已有更新的替换，以及文件当前版本
     * <li>上传不是公开文件的替换时返回 FILE_NOT_FOUND。</li>
     */
    PublicSync publicSync(long fileId,long uploadId,Instant now);
}
