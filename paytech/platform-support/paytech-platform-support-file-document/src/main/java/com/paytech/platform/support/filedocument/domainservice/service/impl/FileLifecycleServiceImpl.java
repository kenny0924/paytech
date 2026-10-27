package com.paytech.platform.support.filedocument.domainservice.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.filedocument.core.dal.dataobject.CompletedUploadFile;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileDescription;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileObject;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileScanPolicyRevision;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileScanRequirement;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileUpload;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileVersion;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.core.enums.FileVersionStatus;
import com.paytech.platform.support.filedocument.domain.enums.FileScanType;
import com.paytech.platform.support.filedocument.domain.enums.FileUploadStatus;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Downloadable;
import com.paytech.platform.support.filedocument.domain.file.FileModels.File;
import com.paytech.platform.support.filedocument.domain.file.FileModels.PublicSync;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Replaceable;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Requirement;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Result;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Scan;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Verified;
import com.paytech.platform.support.filedocument.domain.file.StoredFileView;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Completed;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.domainservice.repository.FileLifecycleRepository;
import com.paytech.platform.support.filedocument.domainservice.repository.FileScanPolicyRepository;
import com.paytech.platform.support.filedocument.domainservice.repository.FileUploadRepository;
import com.paytech.platform.support.filedocument.domainservice.repository.FileVersionRepository;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;

import lombok.AllArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * 只处理本库事实和规则，存储与扫描 I/O 由用例入口安排在事务外
 * <li>文件表保存文件身份与当前版本镜像，版本表保存每次内容；扫描按版本行执行，v1 检查同时更新镜像，替换版本通过后再提升为当前版本。</li>
 * <li>锁定顺序固定为文件 → 版本，提升、拒绝与扫描结果写入都在文件行锁内完成。</li>
 */
@Service
@AllArgsConstructor
public class FileLifecycleServiceImpl implements FileLifecycleService {
    private final ObjectMapper json;
    private final FileUploadRepository uploads;
    private final FileVersionRepository versions;
    private final FileScanPolicyRepository policies;
    private final FileLifecycleRepository repository;

    /** 任一来源的原上传。 */
    @Override
    public Upload sourceUpload(long uploadId) {
        return upload(uploads.findById(uploadId));
    }

    /** 当前读防止 Redis Lease 过期产生重复文件。 */
    @Override
    public Upload lockSourceUpload(long uploadId) {
        return upload(repository.lockSourceUpload(uploadId));
    }

    /** 当前版本元数据；公开地址只在 PUBLIC 且当前版本 AVAILABLE 时计算。 */
    @Override
    public StoredFileView describe(long fileId) {
        FileDescription row = repository.describeFile(fileId);
        if (row == null) {
            throw new FailException(FileErrorCode.FILE_NOT_FOUND);
        }
        return StoredFileView.of(row);
    }

    /** 空集合不访问数据库；公开地址的计算规则与单条查询相同。 */
    @Override
    public Map<Long, StoredFileView> describeAll(Collection<Long> fileIds) {
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        return repository.describeFiles(Set.copyOf(fileIds)).stream().map(StoredFileView::of)
            .collect(Collectors.toMap(StoredFileView::getFileId, view -> view));
    }

    /** 文件当前版本坐标与状态。 */
    @Override
    public File file(long fileId) {
        return file(requireFile(repository.readFile(fileId)));
    }

    /** 当前版本按镜像判断；历史版本按版本行判断，候选与被拒绝的版本不能下载。 */
    @Override
    public Downloadable downloadable(long fileId, Integer versionNo) {
        FileObject row = requireFile(repository.readFile(fileId));
        if (versionNo == null) {
            if (row.scanStatus() != FileScanStatus.AVAILABLE || row.objectStatus() != FileObjectStatus.ACTIVE) {
                throw new FailException(FileErrorCode.FILE_STATE_CONFLICT);
            }
            return new Downloadable(file(row), repository.describeFile(fileId).getDisplayName());
        }
        FileVersion version = versions.find(fileId, versionNo);
        if (version == null) {
            throw new FailException(FileErrorCode.FILE_NOT_FOUND);
        }
        if (!downloadable(version) || row.objectStatus() != FileObjectStatus.ACTIVE) {
            throw new FailException(FileErrorCode.FILE_STATE_CONFLICT, "该版本不可下载: " + fileId + ":v" + versionNo
                + "，版本状态 " + version.getVersionStatus().getCode());
        }
        return new Downloadable(file(row, version), repository.versionDisplayName(fileId, versionNo));
    }

    /** 调用者已锁定原 Upload；完成状态与版本在同一事务写入，二者必须同时存在。 */
    @Override
    public Completed completedFile(Upload upload) {
        if (upload.status() != FileUploadStatus.COMPLETED) {
            return null;
        }
        CompletedUploadFile done = versions.completedBy(upload.uploadId());
        if (done == null) {
            throw new ServiceUnavailableException();
        }
        return new Completed(done.fileId(), done.versionNo(), done.completedAt(), upload.accessLevel());
    }

    /** 只有原授权有效期内的新完成请求可以登记。 */
    @Override
    public void requireOpen(Upload upload, Instant now) {
        boolean open = upload.status() == FileUploadStatus.CREATED || upload.status() == FileUploadStatus.UPLOADING;
        if (!open || !now.isBefore(upload.expiresAt())) {
            throw new FailException(FileErrorCode.FILE_STATE_CONFLICT);
        }
    }

    /** File、v1 版本、Upload 与调用者的扫描补偿事件处于同一本地事务。 */
    @Override
    public void complete(long fileId, Upload upload, Verified proof, Instant now) {
        requireOpen(upload, now);
        if (repository.completeUpload(upload.uploadId(), now) != 1) {
            throw new FailException(FileErrorCode.FILE_STATE_CONFLICT);
        }
        repository.insertFile(fileId, upload, proof, now);
        versions.insert(candidate(fileId, fileId, 1, upload, proof, now));
    }

    /** 锁定被替换文件以串行分配版本号；文件当前版本与镜像保持不变，候选版本单独检查。 */
    @Override
    public int completeReplacement(long versionId, Upload upload, Verified proof, Instant now) {
        requireOpen(upload, now);
        FileObject row = repository.lockFile(upload.replacesFileId());
        if (row == null || row.objectStatus() != FileObjectStatus.ACTIVE) {
            throw new FailException(FileErrorCode.FILE_STATE_CONFLICT);
        }
        if (repository.completeUpload(upload.uploadId(), now) != 1) {
            throw new FailException(FileErrorCode.FILE_STATE_CONFLICT);
        }
        int versionNo = versions.maxVersionNo(row.fileId()) + 1;
        versions.insert(candidate(versionId, row.fileId(), versionNo, upload, proof, now));
        return versionNo;
    }

    /** 已完成或已过期状态的上传不改写。 */
    @Override
    public void cancelReplacement(Upload upload, Instant now) {
        uploads.cancel(upload.uploadId(), now);
    }

    /** 同一文件同时只允许一个未结束的替换；冻结的策略代码取当前版本的策略修订。 */
    @Override
    public Replaceable replaceable(long fileId, String sourceSystem, Instant now) {
        FileObject row = repository.readFile(fileId);
        if (row == null || !row.sourceSystem().equals(sourceSystem)) {
            throw new FailException(FileErrorCode.FILE_NOT_FOUND);
        }
        if (row.scanStatus() != FileScanStatus.AVAILABLE || row.objectStatus() != FileObjectStatus.ACTIVE) {
            throw new FailException(FileErrorCode.FILE_STATE_CONFLICT, "文件当前不可替换: " + fileId);
        }
        if (uploads.countOpenReplacements(fileId, now) > 0
            || versions.pendingVersionNo(fileId, row.currentVersionNo()) != null) {
            throw new FailException(FileErrorCode.FILE_STATE_CONFLICT, "文件已有未结束的替换: " + fileId);
        }
        FileScanPolicyRevision policy = policies.findById(row.policyId());
        if (policy == null) {
            throw new ServiceUnavailableException();
        }
        return new Replaceable(fileId, row.sourceSystem(), row.reasonCode(), row.accessLevel(), row.provider(),
            row.container(), row.objectRef(), row.objectVersion(), policy.getPolicyCode(),
            uploads.maxReplacementNo(fileId) + 1);
    }

    /** 缺失或损坏的冻结规则保持失败关闭，不依赖当前 ACTIVE 默认策略。 */
    @Override
    public Scan beginScan(long fileId, int versionNo, Instant now) {
        FileObject row = lockActiveFile(fileId);
        FileVersion version = lockVersion(fileId, versionNo);
        if (terminal(version.getScanStatus())) {
            return new Scan(file(row, version), List.of());
        }
        List<FileScanRequirement> requirements = repository.requirements(fileId, versionNo, version.getScanPolicyId());
        if (requirements.isEmpty()) {
            throw new ServiceUnavailableException();
        }
        var pending = new ArrayList<Requirement>();
        for (var requirement : requirements) {
            var rule = rule(requirement);
            if (requirement.resultCode() == null) {
                pending.add(rule);
            } else if (!rule.acceptedCodes().contains(requirement.resultCode())) {
                throw new ServiceUnavailableException();
            }
        }
        applyScan(row, version, pending.isEmpty() ? FileScanStatus.AVAILABLE : FileScanStatus.SCANNING, now);
        return new Scan(file(row, version), List.copyOf(pending));
    }

    /** 持有文件与版本行锁后再次读取已追加结果；旧执行者不能改写终局。 */
    @Override
    public void recordResult(long resultId, int versionNo, Result result, Instant now) {
        FileObject row = lockActiveFile(result.fileId());
        FileVersion version = lockVersion(result.fileId(), versionNo);
        if (terminal(version.getScanStatus())) {
            return;
        }
        var requirement = repository.requirements(row.fileId(), versionNo, version.getScanPolicyId()).stream()
            .filter(r -> r.id() == result.requirementId()).findFirst()
            .orElseThrow(ServiceUnavailableException::new);
        if (requirement.resultCode() != null) {
            return;
        }
        var rule = rule(requirement);
        repository.insertResult(resultId, versionNo, version.getScanPolicyId(), result, now);
        if (!rule.acceptedCodes().contains(result.resultCode())) {
            applyScan(row, version,
                rule.type() == FileScanType.FORMAT ? FileScanStatus.REJECTED : FileScanStatus.QUARANTINED, now);
        } else {
            beginScan(row.fileId(), versionNo, now);
        }
    }

    /** 替换在上传授权到期、且本次产生的候选版本有检查结论后才算结束；之后又开始的替换负责最终收敛。 */
    @Override
    public PublicSync publicSync(long fileId, long uploadId, Instant now) {
        Upload upload = upload(uploads.findById(uploadId));
        if (!upload.replacement() || upload.replacesFileId() != fileId
            || upload.accessLevel() != FileAccessLevel.PUBLIC) {
            throw new FailException(FileErrorCode.FILE_NOT_FOUND);
        }
        FileObject row = requireFile(repository.readFile(fileId));
        boolean superseded = uploads.maxReplacementNo(fileId) > upload.replacementNo();
        CompletedUploadFile done = versions.completedBy(uploadId);
        FileVersion candidate = done == null ? null : versions.find(fileId, done.versionNo());
        boolean checking = candidate != null && candidate.getVersionStatus() == FileVersionStatus.CANDIDATE;
        return new PublicSync(!now.isBefore(upload.expiresAt()) && !checking, superseded, file(row));
    }

    /**
     * 写入版本的扫描结论；正在检查的是当前版本（首次上传）时同步镜像，替换版本通过时提升为当前版本
     *
     * @param row 已锁定的文件
     * @param version 已锁定的版本
     * @param status 新扫描状态
     * @param now 写入时间
     */
    void applyScan(FileObject row, FileVersion version, FileScanStatus status, Instant now) {
        FileVersionStatus target = switch (status) {
            case AVAILABLE -> FileVersionStatus.CURRENT;
            case PENDING_SCAN, SCANNING -> FileVersionStatus.CANDIDATE;
            case QUARANTINED, REJECTED -> FileVersionStatus.REJECTED;
        };
        versions.updateScan(version, status, target, now);
        if (version.getVersionNo() <= row.currentVersionNo()) {
            repository.scanState(row.fileId(), status, now);
            return;
        }
        if (status == FileScanStatus.AVAILABLE) {
            versions.supersede(row.fileId(), row.currentVersionNo(), now);
            repository.promote(version, row.currentVersionNo(), now);
        }
    }

    /** 锁定对象有效的文件；缺失或对象不可用时扫描保持可重试失败。 */
    FileObject lockActiveFile(long fileId) {
        FileObject row = repository.lockFile(fileId);
        if (row == null || row.objectStatus() != FileObjectStatus.ACTIVE) {
            throw new ServiceUnavailableException();
        }
        return row;
    }

    /** 锁定版本；缺失（例如存量文件未回填 v1）时扫描保持可重试失败。 */
    FileVersion lockVersion(long fileId, int versionNo) {
        FileVersion version = versions.lock(fileId, versionNo);
        if (version == null) {
            throw new ServiceUnavailableException();
        }
        return version;
    }

    /** 文件不存在按不可访问处理。 */
    static FileObject requireFile(FileObject row) {
        if (row == null) {
            throw new FailException(FileErrorCode.FILE_NOT_FOUND);
        }
        return row;
    }

    /** CURRENT 或 SUPERSEDED 且检查 AVAILABLE 的版本可以下载。 */
    static boolean downloadable(FileVersion version) {
        boolean served = version.getVersionStatus() == FileVersionStatus.CURRENT
            || version.getVersionStatus() == FileVersionStatus.SUPERSEDED;
        return served && version.getScanStatus() == FileScanStatus.AVAILABLE;
    }

    /** 新完成的候选版本：等待扫描，冻结上传开始时的策略修订。 */
    static FileVersion candidate(long versionId, long fileId, int versionNo, Upload upload, Verified proof,
        Instant now) {
        FileVersion version = new FileVersion();
        version.setVersionId(versionId);
        version.setFileId(fileId);
        version.setVersionNo(versionNo);
        version.setUploadId(upload.uploadId());
        version.setObjectVersion(proof.objectVersion());
        version.setContentHash(proof.contentHash());
        version.setSizeBytes(proof.sizeBytes());
        version.setMimeType(proof.mimeType());
        version.setStorageEncryptionKeyRef(proof.encryptionKeyRef());
        version.setScanPolicyId(upload.scanPolicyId());
        version.setScanStatus(FileScanStatus.PENDING_SCAN);
        version.setVersionStatus(FileVersionStatus.CANDIDATE);
        version.setVersion(1);
        version.setCreateTime(now);
        version.setUpdateTime(now);
        return version;
    }

    /** 终局版本不会再次启动自动扫描或改写原结果。 */
    private boolean terminal(FileScanStatus status) {
        return status == FileScanStatus.AVAILABLE || status == FileScanStatus.QUARANTINED
            || status == FileScanStatus.REJECTED;
    }

    /** 严格读取接受结果集，未知结构不能变成默认接受。 */
    private Requirement rule(FileScanRequirement row) {
        try {
            var codes = json.readTree(row.acceptedCodes());
            if (!codes.isArray() || codes.isEmpty() || codes.size() > 64 || row.minimumVersion() == null
                || row.minimumVersion().isBlank()) {
                throw new IllegalArgumentException();
            }
            var values = new LinkedHashSet<String>();
            for (var code : codes) {
                if (!code.isTextual() || !code.asText().matches("[A-Z][A-Z0-9_]{0,63}") || !values.add(code.asText())) {
                    throw new IllegalArgumentException();
                }
            }
            return new Requirement(row.id(), row.scannerCode(), row.type(), row.minimumVersion(), List.copyOf(values));
        } catch (RuntimeException invalid) {
            throw new ServiceUnavailableException();
        }
    }

    /** 转为文件能力发布的上传事实；不存在按不可访问处理。 */
    private Upload upload(FileUpload row) {
        if (row == null) {
            throw new FailException(FileErrorCode.FILE_NOT_FOUND);
        }
        return new Upload(row.uploadId(), row.tenantId(), row.uploaderId(), row.sourceSystem(), row.reasonCode(),
            row.displayName(), row.expectedSize(), row.expectedMime(), row.storageProvider(), row.containerRef(),
            row.objectRef(), row.scanPolicyId(), row.expiresAt(), row.status(), row.accessLevel(),
            row.replacesFileId(), row.replacementNo(), row.baseObjectVersion());
    }

    /** 当前版本（镜像）的存储与扫描事实。 */
    private File file(FileObject row) {
        return new File(row.fileId(), row.provider(), row.container(), row.objectRef(),
            new Verified(row.objectVersion(), row.contentHash(), row.sizeBytes(), row.mimeType(),
                row.encryptionKeyRef()),
            row.policyId(), row.scanStatus(), row.objectStatus(), row.currentVersionNo());
    }

    /** 指定版本的存储与扫描事实；对象名与位置各版本共用。 */
    private File file(FileObject row, FileVersion version) {
        return new File(row.fileId(), row.provider(), row.container(), row.objectRef(),
            new Verified(version.getObjectVersion(), version.getContentHash(), version.getSizeBytes(),
                version.getMimeType(), version.getStorageEncryptionKeyRef()),
            version.getScanPolicyId(), version.getScanStatus(), row.objectStatus(), version.getVersionNo());
    }
}
