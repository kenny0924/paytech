package com.paytech.client.console.bff.integration.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.integration.PlatformFileClient;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.api.files.InternalFileApi;
import com.paytech.platform.support.api.files.request.StartFileUploadRequestBody;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;

import lombok.AllArgsConstructor;

/**
 * 调用 Platform 内部文件接口并校验回执
 * <li>文件地址必须与文件 ID 对应；扫描与对象状态必须是已发布代码，未知状态不开放下载。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class PlatformFileClientImpl implements PlatformFileClient {
    private final InternalFileApi files;

    /** 开始上传并要求返回上传 ID 与 PUT 授权。 */
    @Override
    public FileUploadResponseBody startUpload(StartFileUploadRequestBody request) {
        var value = files.startUpload(request);
        if (value == null || value.getUploadId() == null || value.getAuthorization() == null) {
            throw new ServiceUnavailableException();
        }
        return value;
    }

    /** 完成上传并核对上传 ID 与文件地址。 */
    @Override
    public FileUploadCompletionResponseBody completeUpload(String uploadId) {
        var value = files.completeUpload(uploadId);
        if (value == null || !uploadId.equals(value.getUploadId()) || !addressed(value.getFileId(), value.getFileAddress())) {
            throw new ServiceUnavailableException();
        }
        return value;
    }

    /** 查询元数据并核对文件身份与状态码。 */
    @Override
    public StoredFileResponseBody file(String fileId) {
        var value = files.getFile(fileId);
        if (value == null || !fileId.equals(value.getFileId()) || !addressed(fileId, value.getFileAddress())) {
            throw new ServiceUnavailableException();
        }
        try {
            FileScanStatus.fromCode(value.getScanStatus());
            FileObjectStatus.fromCode(value.getObjectStatus());
        } catch (IllegalArgumentException unknown) {
            throw new ServiceUnavailableException();
        }
        return value;
    }

    /** 签发下载地址并要求地址与到期时间齐全。 */
    @Override
    public FileDownloadResponseBody authorizeDownload(String fileId) {
        var value = files.authorizeDownload(fileId, null, null);
        if (value == null || value.getUrl() == null || value.getExpiresAt() == null) {
            throw new ServiceUnavailableException();
        }
        return value;
    }

    /** 文件地址由文件服务签发，必须恰好对应该文件 ID。 */
    static boolean addressed(String fileId, String fileAddress) {
        try {
            return fileId != null && fileAddress != null
                && Long.toString(FileAddress.parse(fileAddress).fileId()).equals(fileId);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
