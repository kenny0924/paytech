package com.paytech.platform.support.filedocument.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;
import com.paytech.platform.support.filedocument.core.dal.mapper.FileUploadMapper;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileUpload;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileScanPolicy;
import com.paytech.platform.support.filedocument.domainservice.repository.FileUploadRepository;

/** 文件持久化只使用本能力 Mapper。 */
@Repository
@lombok.AllArgsConstructor
public class FileUploadRepositoryImpl implements FileUploadRepository {
    private final FileUploadMapper mapper;
    /** 查询固定 Upload。 */
    @Override public FileUpload find(long id) { return mapper.find(id); }
    /** 查询任一来源的原上传。 */
    @Override public FileUpload findById(long id) { return mapper.findById(id); }
    /** 查询有效策略。 */
    @Override public List<FileScanPolicy> policies(String code, Instant now) { return mapper.policies(code, now); }
    /** 不自行开启事务。 */
    @Override public void insert(FileUpload upload, Instant now) { mapper.insert(upload, now); }
}
