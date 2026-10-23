package com.paytech.platform.support.localization.integration.impl;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.LocalizationErrorCode;
import com.paytech.platform.support.filedocument.domain.file.FileModels.File;
import com.paytech.platform.support.filedocument.domain.file.StoredFileView;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.filedocument.integration.storage.FileObjectStorage;
import com.paytech.platform.support.filedocument.integration.storage.FileStorageRegistry;
import com.paytech.platform.support.localization.core.configuration.LocalizationProperties;
import com.paytech.platform.support.localization.domain.Sha256;
import com.paytech.platform.support.localization.integration.UploadedFile;
import com.paytech.platform.support.localization.integration.UploadedFileReader;

/** 经 file-document 的 Owner 服务核对元数据、经存储注册表读取原版本字节，并与登记摘要核对。 */
@Component
public class FileDocumentUploadedFileReader implements UploadedFileReader {

    private final FileLifecycleService files;
    private final LocalizationProperties properties;
    private final ObjectProvider<FileStorageRegistry> registry;

    /**
     * 装配依赖；存储注册表只在 files.enabled 时存在。
     *
     * @param files 文件 Owner 服务
     * @param properties 多语言配置
     * @param registry 存储注册表
     */
    public FileDocumentUploadedFileReader(FileLifecycleService files, LocalizationProperties properties,
        ObjectProvider<FileStorageRegistry> registry) {
        this.files = files;
        this.properties = properties;
        this.registry = registry;
    }

    /** 核对元数据后读取并核对摘要。 */
    @Override
    public UploadedFile read(long fileId, String reasonCode) {
        StoredFileView view = describe(fileId);
        requireUsable(view, reasonCode);
        byte[] content = content(files.file(fileId), view.getSizeBytes());
        if (!Sha256.hex(content).equals(view.getContentHash())) {
            throw new ErrorException("导入文件内容与登记摘要不一致: " + fileId);
        }
        return new UploadedFile(fileId, view.getContentHash(), content);
    }

    /** 文件元数据；不存在转为业务拒绝。 */
    StoredFileView describe(long fileId) {
        try {
            return files.describe(fileId);
        } catch (FailException failure) {
            if (FileErrorCode.FILE_NOT_FOUND.getCode().equals(failure.getCode())) {
                throw new BizCheckFailException("导入文件不存在: " + fileId, failure);
            }
            throw failure;
        }
    }

    /** 原因码、检查状态、对象状态与大小。 */
    void requireUsable(StoredFileView view, String reasonCode) {
        if (!reasonCode.equals(view.getReasonCode())) {
            throw new BizCheckFailException("导入文件的上传原因不是 " + reasonCode + ": " + view.getFileId());
        }
        if (view.getScanStatus() == FileScanStatus.PENDING_SCAN || view.getScanStatus() == FileScanStatus.SCANNING) {
            throw new FailException(LocalizationErrorCode.I18N_IMPORT_FILE_NOT_READY, "导入文件仍在检查，当前状态 "
                + view.getScanStatus().getCode() + "，请稍后重试: " + view.getFileId());
        }
        if (view.getScanStatus() != FileScanStatus.AVAILABLE) {
            throw new BizCheckFailException("导入文件未通过检查，当前状态 " + view.getScanStatus().getCode() + ": "
                + view.getFileId());
        }
        if (view.getObjectStatus() != FileObjectStatus.ACTIVE) {
            throw new BizCheckFailException("导入文件已不可用: " + view.getFileId());
        }
        if (view.getSizeBytes() > properties.getImportMaxFileBytes()) {
            throw new BizCheckFailException("导入文件不能超过 " + properties.getImportMaxFileBytes() + " 字节: "
                + view.getFileId());
        }
    }

    /** 读取原版本全部字节；存储不可用时报 503。 */
    byte[] content(File file, long sizeBytes) {
        FileStorageRegistry available = registry.getIfAvailable();
        if (available == null) {
            throw new ServiceUnavailableException();
        }
        try (FileObjectStorage storage = available.openForRead(file.provider(), file.container());
            InputStream stream = storage.open(file)) {
            return stream.readNBytes(Math.toIntExact(sizeBytes) + 1);
        } catch (IOException unavailable) {
            throw new ServiceUnavailableException(unavailable);
        }
    }
}
