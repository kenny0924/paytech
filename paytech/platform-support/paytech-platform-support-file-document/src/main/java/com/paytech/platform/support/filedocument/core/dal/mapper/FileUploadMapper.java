package com.paytech.platform.support.filedocument.core.dal.mapper;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileUpload;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileScanPolicy;

/** 文件表由文件能力独占；此入口只读策略并创建、作废原 Upload。 */
public interface FileUploadMapper {
    /** 读取任一来源的原上传。 */
    FileUpload findById(@Param("id") long id);
    /** 按明确策略代码读取当前有效且已冻结的唯一修订。 */
    List<FileScanPolicy> policies(@Param("code") String code, @Param("now") Instant now);
    /** 登记 Upload；需要与其他写入原子提交时由调用方开启事务。 */
    void insert(@Param("upload") FileUpload upload, @Param("now") Instant now);
    /** 文件仍在授权期内、尚未完成的替换上传数。 */
    int countOpenReplacements(@Param("fileId") long fileId, @Param("now") Instant now);
    /** 文件已有替换上传的最大替换序号；没有为 0。 */
    int maxReplacementNo(@Param("fileId") long fileId);
    /** 作废尚未完成的上传。 */
    int cancel(@Param("id") long id, @Param("now") Instant now);
}
