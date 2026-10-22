package com.paytech.platform.support.filedocument.core.dal.mapper;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileUpload;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileScanPolicy;

/** 文件表由文件能力独占；此入口只读策略并创建原 Upload。 */
public interface FileUploadMapper {
    /** 读取任一来源的原上传。 */
    FileUpload findById(@Param("id") long id);
    /** 按明确策略代码读取当前有效且已冻结的唯一修订。 */
    List<FileScanPolicy> policies(@Param("code") String code, @Param("now") Instant now);
    /** 登记 Upload；需要与其他写入原子提交时由调用方开启事务。 */
    void insert(@Param("upload") FileUpload upload, @Param("now") Instant now);
}
