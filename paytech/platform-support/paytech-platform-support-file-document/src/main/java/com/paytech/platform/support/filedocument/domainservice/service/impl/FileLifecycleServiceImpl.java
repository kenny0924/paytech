package com.paytech.platform.support.filedocument.domainservice.service.impl;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import com.paytech.foundation.common.error.*;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.filedocument.domain.enums.*;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Completed;
import com.paytech.platform.support.filedocument.domain.file.StoredFileView;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.domain.file.FileModels.*;
import com.paytech.platform.support.filedocument.core.dal.dataobject.*;
import com.paytech.platform.support.filedocument.domainservice.repository.*;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import tools.jackson.databind.ObjectMapper;
/** 只处理本库事实和规则，存储与扫描 I/O 由用例入口安排在事务外。 */
@Service @lombok.AllArgsConstructor
public class FileLifecycleServiceImpl implements FileLifecycleService {
    private final FileUploadRepository uploads;
    private final FileLifecycleRepository repository;
    private final ObjectMapper json;
    /** 任一来源的原上传。 */
    @Override public Upload sourceUpload(long uploadId) { return upload(uploads.findById(uploadId)); }
    /** 当前读防止 Redis Lease 过期产生重复文件。 */
    @Override public Upload lockSourceUpload(long uploadId) { return upload(repository.lockSourceUpload(uploadId)); }
    /** 文件与上传记录组合的元数据。 */
    @Override public StoredFileView describe(long fileId) {
        var view=repository.describeFile(fileId);
        if (view==null) throw new FailException(FileErrorCode.FILE_NOT_FOUND);
        return view;
    }
    /** 文件不可变坐标与当前状态。 */
    @Override public File file(long fileId) {
        var row=repository.readFile(fileId);
        if (row==null) throw new FailException(FileErrorCode.FILE_NOT_FOUND);
        return file(row);
    }
    /** 调用者已锁定原 Upload；完成状态与文件在同一事务写入，二者必须同时存在。 */
    @Override public Completed completedFile(Upload upload) {
        if (upload.status()!=FileUploadStatus.COMPLETED) {
            return null;
        }
        var file=repository.completedFile(upload.uploadId());
        if (file==null) {
            throw new ServiceUnavailableException();
        }
        return new Completed(file.fileId(),file.completedAt());
    }
    /** 只有原授权有效期内的新完成请求可以登记。 */
    @Override public void requireOpen(Upload upload,Instant now) {
        if (!(upload.status()==FileUploadStatus.CREATED || upload.status()==FileUploadStatus.UPLOADING) || !now.isBefore(upload.expiresAt()))
            throw new FailException(FileErrorCode.FILE_STATE_CONFLICT);
    }
    /** File、Upload 与调用者的扫描补偿事件处于同一本地事务。 */
    @Override public void complete(long fileId,Upload upload,Verified proof,Instant now) {
        requireOpen(upload,now);
        if (repository.completeUpload(upload.uploadId(),now)!=1) throw new FailException(FileErrorCode.FILE_STATE_CONFLICT);
        repository.insertFile(fileId,upload,proof,now);
    }
    /** 缺失或损坏的冻结规则保持失败关闭，不依赖当前 ACTIVE 默认策略。 */
    @Override public Scan beginScan(long fileId,Instant now) {
        var row=repository.lockFile(fileId);
        if (row==null || row.objectStatus()!=FileObjectStatus.ACTIVE) throw new ServiceUnavailableException();
        var pending=new ArrayList<Requirement>();
        if (terminal(row.scanStatus())) return new Scan(file(row),List.of());
        var requirements=repository.requirements(fileId,row.policyId());
        if (requirements.isEmpty()) throw new ServiceUnavailableException();
        for (var requirement:requirements) {
            var rule=rule(requirement);
            if (requirement.resultCode()==null) pending.add(rule);
            else if (!rule.acceptedCodes().contains(requirement.resultCode())) throw new ServiceUnavailableException();
        }
        var status=pending.isEmpty() ? FileScanStatus.AVAILABLE : FileScanStatus.SCANNING;
        repository.scanState(fileId,status,now);
        return new Scan(file(row),List.copyOf(pending));
    }
    /** 持有文件行锁后再次读取已追加结果；旧执行者不能改写终局。 */
    @Override public void recordResult(long resultId,Result result,Instant now) {
        var row=repository.lockFile(result.fileId());
        if (row==null || row.objectStatus()!=FileObjectStatus.ACTIVE) throw new ServiceUnavailableException();
        if (terminal(row.scanStatus())) return;
        var requirements=repository.requirements(row.fileId(),row.policyId());
        var requirement=requirements.stream().filter(r -> r.id()==result.requirementId()).findFirst().orElseThrow(ServiceUnavailableException::new);
        if (requirement.resultCode()!=null) return;
        var rule=rule(requirement);
        repository.insertResult(resultId,row.policyId(),result,now);
        if (!rule.acceptedCodes().contains(result.resultCode())) {
            repository.scanState(row.fileId(),rule.type()==FileScanType.FORMAT ? FileScanStatus.REJECTED : FileScanStatus.QUARANTINED,now);
        } else {
            beginScan(row.fileId(),now);
        }
    }
    /** 终局文件不会再次启动自动扫描或改写原结果。 */
    private boolean terminal(FileScanStatus status) { return status==FileScanStatus.AVAILABLE || status==FileScanStatus.QUARANTINED || status==FileScanStatus.REJECTED; }
    /** 严格读取接受结果集，未知结构不能变成默认接受。 */
    private Requirement rule(FileScanRequirement row) {
        try {
            var codes=json.readTree(row.acceptedCodes());
            if (!codes.isArray() || codes.isEmpty() || codes.size()>64 || row.minimumVersion()==null || row.minimumVersion().isBlank()) throw new IllegalArgumentException();
            var values=new LinkedHashSet<String>();
            for (var code:codes) {
                if (!code.isTextual() || !code.asText().matches("[A-Z][A-Z0-9_]{0,63}") || !values.add(code.asText())) throw new IllegalArgumentException();
            }
            return new Requirement(row.id(),row.scannerCode(),row.type(),row.minimumVersion(),List.copyOf(values));
        } catch (RuntimeException invalid) { throw new ServiceUnavailableException(); }
    }
    /** 转为文件能力发布的上传事实；不存在按不可访问处理。 */
    private Upload upload(FileUpload row) {
        if (row==null) throw new FailException(FileErrorCode.FILE_NOT_FOUND);
        return new Upload(row.uploadId(),row.tenantId(),row.uploaderId(),row.sourceSystem(),row.reasonCode(),row.displayName(),row.expectedSize(),row.expectedMime(),row.storageProvider(),row.containerRef(),row.objectRef(),row.scanPolicyId(),row.expiresAt(),row.status());
    }
    /** 发布存储/扫描所需的固定业务事实。 */
    private File file(FileObject row) {
        return new File(row.fileId(),row.provider(),row.container(),row.objectRef(),new Verified(row.objectVersion(),row.contentHash(),row.sizeBytes(),row.mimeType(),row.encryptionKeyRef()),row.policyId(),row.scanStatus(),row.objectStatus());
    }
}
