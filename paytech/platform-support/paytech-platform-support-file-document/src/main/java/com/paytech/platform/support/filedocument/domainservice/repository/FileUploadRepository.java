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
}
