package com.paytech.platform.support.filedocument.domainservice.repository;

import java.time.Instant;
import java.util.List;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileUpload;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileScanPolicy;

/** 文件能力内部持久化端口。 */
public interface FileUploadRepository {
    /** 查询任一来源的原上传。 */
    FileUpload findById(long id);
    /** 查询精确策略目录。 */
    List<FileScanPolicy> policies(String code, Instant now);
    /** 创建上传事实，参与调用者本地事务。 */
    void insert(FileUpload upload, Instant now);
    /** 文件仍在授权期内、尚未完成的替换上传数。 */
    int countOpenReplacements(long fileId, Instant now);
    /** 文件已有替换上传的最大替换序号；没有为 0。 */
    int maxReplacementNo(long fileId);
    /** 作废尚未完成的上传；状态已变化时返回 false。 */
    boolean cancel(long id, Instant now);
}
