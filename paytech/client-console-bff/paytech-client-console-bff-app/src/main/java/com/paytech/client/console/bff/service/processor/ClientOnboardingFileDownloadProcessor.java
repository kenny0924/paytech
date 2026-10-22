package com.paytech.client.console.bff.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.integration.PlatformFileClient;
import com.paytech.client.console.bff.service.processor.request.ClientOnboardingFileRequest;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;

import lombok.AllArgsConstructor;

/**
 * 下载入驻文件（F04）：按查文件的同一授权，再由 Platform 签发原版本的短时下载地址
 * <li>授权不通过时不调用下载授权接口；文件状态不允许下载由 Platform 返回 FILE_STATE_CONFLICT。</li>
 * <li>每次独立授权，地址最长 60 秒，不进入日志。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientOnboardingFileDownloadProcessor
    extends AbstractProcessor<ClientOnboardingFileRequest, FileDownloadResponseBody> {
    private final PlatformFileClient files;
    private final ClientOnboardingFileAccess access;

    @Override
    protected FileDownloadResponseBody processRequest(ClientOnboardingFileRequest request) {
        access.readableFile(request.getActor(), request.getFileId());
        return files.authorizeDownload(request.getFileId());
    }
}
