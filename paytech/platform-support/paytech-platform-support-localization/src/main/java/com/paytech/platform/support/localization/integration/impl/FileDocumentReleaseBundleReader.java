package com.paytech.platform.support.localization.integration.impl;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.filedocument.domain.file.StoredFileView;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.localization.domain.Sha256;
import com.paytech.platform.support.localization.integration.ReleaseBundleReader;

import lombok.AllArgsConstructor;

/**
 * 按文件地址读取已存语言包：与导入文件同一条进程内读取方式（file-document 元数据 + 存储注册表按记录坐标读原版本字节），
 * 再与文件服务登记的摘要核对。
 * <li>公开文件与私有文件的读取路径相同：都经 Platform 账号读取记录上的准确版本，不经公开地址，不依赖 CDN。</li>
 * <li>不要求检查已通过：差异与变更摘要也会读取检查中或被拒绝的发布；内容以登记摘要为准。</li>
 * <li>文件不存在、对象不可用或摘要不符属于数据不一致，返回服务端错误；存储不可读返回 503。</li>
 */
@Component
@AllArgsConstructor
public class FileDocumentReleaseBundleReader implements ReleaseBundleReader {

    private final FileLifecycleService files;

    private final FileDocumentUploadedFileReader content;

    /** 核对元数据后读取原版本字节并核对摘要。 */
    @Override
    public byte[] read(FileAddress fileAddress) {
        StoredFileView view = describe(fileAddress);
        if (view.getObjectStatus() != FileObjectStatus.ACTIVE) {
            throw new ErrorException("语言包文件已不可用: " + fileAddress.value() + ", 状态: "
                + view.getObjectStatus().getCode());
        }
        byte[] bytes = content.content(files.file(fileAddress.fileId()), view.getSizeBytes());
        if (!Sha256.hex(bytes).equals(view.getContentHash())) {
            throw new ErrorException("语言包文件内容与登记摘要不一致: " + fileAddress.value());
        }
        return bytes;
    }

    /** 文件元数据；不存在转为数据不一致。 */
    StoredFileView describe(FileAddress fileAddress) {
        try {
            return files.describe(fileAddress.fileId());
        } catch (FailException failure) {
            if (FileErrorCode.FILE_NOT_FOUND.getCode().equals(failure.getCode())) {
                throw new ErrorException("语言包文件不存在: " + fileAddress.value(), failure);
            }
            throw failure;
        }
    }
}
