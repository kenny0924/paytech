package com.paytech.platform.support.localization.integration.impl;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.filedocument.domain.file.StoredFileView;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.localization.core.enums.BundleFileState;
import com.paytech.platform.support.localization.integration.ReleaseBundleFileChecker;

import lombok.AllArgsConstructor;

/** 按文件地址读取 file-document 元数据，把扫描与对象状态归为上线判断所需的三种结论。 */
@Component
@AllArgsConstructor
public class FileDocumentReleaseBundleFileChecker implements ReleaseBundleFileChecker {

    private final FileLifecycleService files;

    /** AVAILABLE 且对象 ACTIVE 为可用；待检查与检查中为等待；其余为拒绝。 */
    @Override
    public BundleFileState state(FileAddress fileAddress) {
        StoredFileView view;
        try {
            view = files.describe(fileAddress.fileId());
        } catch (FailException failure) {
            if (FileErrorCode.FILE_NOT_FOUND.getCode().equals(failure.getCode())) {
                return BundleFileState.REJECTED;
            }
            throw failure;
        }
        if (view.getObjectStatus() != FileObjectStatus.ACTIVE) {
            return BundleFileState.REJECTED;
        }
        return switch (view.getScanStatus()) {
            case AVAILABLE -> BundleFileState.AVAILABLE;
            case PENDING_SCAN, SCANNING -> BundleFileState.PENDING;
            case QUARANTINED, REJECTED -> BundleFileState.REJECTED;
        };
    }
}
