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
/** Owner 内部 SQL 适配。 */
@Repository @lombok.AllArgsConstructor
public class FileLifecycleRepositoryImpl implements FileLifecycleRepository {
 private final FileLifecycleMapper mapper;
 /** 查询原上传授权坐标。 */
 @Override public Long readUploadId(long fileId,long accountId) { return mapper.readUploadId(fileId,accountId); }
 /** 运营审核读取，权限由调用方确认。 */
 @Override public Long readReviewUploadId(long fileId) { return mapper.readReviewUploadId(fileId); }
 /** 查询文件当前事实，不驱动扫描。 */
 @Override public FileObject readFile(long id) { return mapper.readFile(id); }

 /** 查询上传已产生的文件。 */
 @Override public CompletedUploadFile completedFile(long uploadId) { return mapper.completedFile(uploadId); }
 /** 通过同库 Mapper 执行 lockUpload。 */
 @Override public FileUpload lockUpload(long id) { return mapper.lockUpload(id); }
 /** 通过同库 Mapper 执行 lockFile。 */
 @Override public FileObject lockFile(long id) { return mapper.lockFile(id); }
 /** 通过同库 Mapper 执行 requirements。 */
 @Override public List<FileScanRequirement> requirements(long fileId,long policyId) { return mapper.requirements(fileId,policyId); }
 /** 通过同库 Mapper 执行 insertFile。 */
 @Override public void insertFile(long id,Upload upload,Verified proof,Instant now) { mapper.insertFile(id,upload,proof,now); }
 /** 通过同库 Mapper 执行 insertOwner。 */
 @Override public void insertOwner(long id,long fileId,String owner,Instant now) { mapper.insertOwner(id,fileId,owner,now); }
 /** 通过同库 Mapper 执行 completeUpload。 */
 @Override public int completeUpload(long id,Instant now) { return mapper.completeUpload(id,now); }
 /** 通过同库 Mapper 执行 scanState。 */
 @Override public void scanState(long id,FileScanStatus status,Instant now) { mapper.scanState(id,status,now); }
 /** 通过同库 Mapper 执行 insertResult。 */
 @Override public void insertResult(long id,long policyId,Result result,Instant now) { mapper.insertResult(id,policyId,result,now); }
}
