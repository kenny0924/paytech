package com.paytech.platform.support.filedocument.domainservice.repository.impl;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;
import com.paytech.platform.support.filedocument.core.dal.mapper.FileLifecycleMapper;
import com.paytech.platform.support.filedocument.core.dal.dataobject.*;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.domain.file.FileModels.*;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.filedocument.domainservice.repository.FileLifecycleRepository;
/** 文件登记与扫描的 SQL 适配。 */
@Repository @lombok.AllArgsConstructor
public class FileLifecycleRepositoryImpl implements FileLifecycleRepository {
 private final FileLifecycleMapper mapper;
 /** 查询文件当前事实，不驱动扫描。 */
 @Override public FileObject readFile(long id) { return mapper.readFile(id); }

 /** 查询上传已产生的文件。 */
 @Override public CompletedUploadFile completedFile(long uploadId) { return mapper.completedFile(uploadId); }
 /** 当前读锁住任一来源的原 Upload。 */
 @Override public FileUpload lockSourceUpload(long id) { return mapper.lockSourceUpload(id); }
 /** 文件元数据组合查询。 */
 @Override public com.paytech.platform.support.filedocument.domain.file.StoredFileView describeFile(long fileId) { return mapper.describeFile(fileId); }
 /** 通过同库 Mapper 执行 lockFile。 */
 @Override public FileObject lockFile(long id) { return mapper.lockFile(id); }
 /** 通过同库 Mapper 执行 requirements。 */
 @Override public List<FileScanRequirement> requirements(long fileId,long policyId) { return mapper.requirements(fileId,policyId); }
 /** 通过同库 Mapper 执行 insertFile。 */
 @Override public void insertFile(long id,Upload upload,Verified proof,Instant now) { mapper.insertFile(id,upload,proof,now); }
 /** 通过同库 Mapper 执行 completeUpload。 */
 @Override public int completeUpload(long id,Instant now) { return mapper.completeUpload(id,now); }
 /** 通过同库 Mapper 执行 scanState。 */
 @Override public void scanState(long id,FileScanStatus status,Instant now) { mapper.scanState(id,status,now); }
 /** 通过同库 Mapper 执行 insertResult。 */
 @Override public void insertResult(long id,long policyId,Result result,Instant now) { mapper.insertResult(id,policyId,result,now); }
}
