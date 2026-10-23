package com.paytech.platform.support.service.processor.filestorage;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.enums.FileDisposition;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.service.processor.files.AuthorizeFileDownloadProcessor;
import com.paytech.platform.support.service.processor.files.request.AuthorizeFileDownloadRequest;
import com.paytech.platform.support.service.processor.files.response.AuthorizeFileDownloadResponse;
import com.paytech.platform.support.service.processor.filestorage.request.FileRecordRequest;

import lombok.AllArgsConstructor;

/**
 * 运营下载文件记录的某一版本：重检 FILE_RECORD_DOWNLOAD → 调用文件下载入口签发附件地址并写下载审计
 * <li>缺省下载当前版本；指定版本须为 CURRENT 或 SUPERSEDED 且检查 AVAILABLE，否则 409。</li>
 * <li>审计 FILE_DOWNLOAD_AUTHORIZE 的主体为 Gateway 操作人账号，属性 version_no 记录实际下载的版本；审计写入失败不返回地址。</li>
 * <li>文件能力未开启返回 503。本入口不写文件表、不开事务。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class DownloadFileRecordProcessor extends AbstractProcessor<FileRecordRequest, AuthorizeFileDownloadResponse> {

    private final FileLifecycleService files;
    private final FileStorageManagementSupport support;
    private final ObjectProvider<AuthorizeFileDownloadProcessor> downloads;

    @Override
    protected AuthorizeFileDownloadResponse processRequest(FileRecordRequest request) {
        // 权限重检
        long operator = support.requireAccount(request.getActor(), request.getSession(),
            FileStorageManagementSupport.RECORD_DOWNLOAD_PERMISSION);

        // 确定版本后调用文件下载入口
        AuthorizeFileDownloadProcessor processor = downloads.getIfAvailable();
        if (processor == null) {
            throw new ServiceUnavailableException();
        }
        int versionNo = request.getVersionNo() != null ? request.getVersionNo()
            : files.file(request.getFileId()).versionNo();
        return processor.process(new AuthorizeFileDownloadRequest(null, request.getFileId(), FileDisposition.ATTACHMENT,
            operator, request.getRequestId(), versionNo));
    }
}
