package com.paytech.platform.support.filedocument.domainservice.service.impl;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import com.paytech.foundation.common.error.*;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.filedocument.domain.enums.*;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Completed;
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
    /** 文件与上传/Owner 的完整绑定必须仍存在；异常坐标不扩大授权。 */
    @Override public Owned read(long fileId,long accountId) {
        Long uploadId=repository.readUploadId(fileId,accountId);
        if (uploadId==null) throw new FailException(OnboardingFileErrorCode.FILE_NOT_FOUND);
        return owned(fileId,owned(uploadId,accountId));
    }
    /** 与 read 相同的入驻材料范围，仅去掉上传账号条件。 */
    @Override public Owned readForReview(long fileId) {
        Long uploadId=repository.readReviewUploadId(fileId);
        if (uploadId==null) throw new FailException(OnboardingFileErrorCode.FILE_NOT_FOUND);
        var row=uploads.find(uploadId);
        if (row==null) throw new FailException(OnboardingFileErrorCode.FILE_NOT_FOUND);
        return owned(fileId,owned(row,row.uploaderId()));
    }
    /** 组合文件事实与原上传目标；目标格式损坏时失败关闭。 */
    private Owned owned(long fileId,Upload upload) {
        var row=repository.readFile(fileId);
        String[] target=upload.ownerRefId().split(":",-1);
        try {
            if (target.length!=2 || !target[0].matches("[1-9][0-9]{0,18}") || !target[1].matches("[1-9][0-9]{0,18}"))
                throw new IllegalArgumentException();
            return new Owned(file(row),upload,Long.parseLong(target[0]),Long.parseLong(target[1]));
        } catch (IllegalArgumentException invalid) { throw new ServiceUnavailableException(); }
    }
    /** 隐藏其他账号的对象存在性。 */
    @Override public Upload owned(long uploadId,long accountId) { return owned(uploads.find(uploadId),accountId); }
    /** 当前读防止 Redis Lease 过期产生重复文件。 */
    @Override public Upload lockOwned(long uploadId,long accountId,long tenantId) {
        var upload=owned(repository.lockUpload(uploadId),accountId);
        if (upload.tenantId()!=tenantId) throw new FailException(OnboardingFileErrorCode.FILE_TARGET_CONFLICT);
        return upload;
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
            throw new FailException(OnboardingFileErrorCode.FILE_STATE_CONFLICT);
    }
    /** File、Owner、Upload 与调用者的回执和补偿事件处于同一本地事务。 */
    @Override public void complete(long fileId,long ownerRowId,Upload upload,Verified proof,Instant now) {
        requireOpen(upload,now);
        if (repository.completeUpload(upload.uploadId(),now)!=1) throw new FailException(OnboardingFileErrorCode.FILE_STATE_CONFLICT);
        repository.insertFile(fileId,upload,proof,now);
        repository.insertOwner(ownerRowId,fileId,upload.ownerRefId(),now);
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
    /** 最小授权读取，不向其他模块泄露 DataObject。 */
    private Upload owned(FileUpload row,long accountId) {
        if (row==null || row.uploaderId()!=accountId) throw new FailException(OnboardingFileErrorCode.FILE_NOT_FOUND);
        return new Upload(row.uploadId(),row.tenantId(),row.uploaderId(),row.ownerRefId(),row.displayName(),row.expectedSize(),row.expectedMime(),row.storageProvider(),row.containerRef(),row.objectRef(),row.scanPolicyId(),row.expiresAt(),row.status());
    }
    /** 发布存储/扫描所需的固定业务事实。 */
    private File file(FileObject row) {
        return new File(row.fileId(),row.provider(),row.container(),row.objectRef(),new Verified(row.objectVersion(),row.contentHash(),row.sizeBytes(),row.mimeType(),row.encryptionKeyRef()),row.policyId(),row.scanStatus(),row.objectStatus());
    }
}
