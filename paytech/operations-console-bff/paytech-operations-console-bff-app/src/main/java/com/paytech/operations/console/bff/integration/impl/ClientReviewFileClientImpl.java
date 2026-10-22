package com.paytech.operations.console.bff.integration.impl;

import org.springframework.stereotype.Component;

import com.paytech.operations.console.bff.integration.ClientReviewFileClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.platform.support.api.files.InternalFileApi;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;

import lombok.AllArgsConstructor;

/** 元数据必须对应请求的文件，下载授权必须带地址与到期时间，否则按上游响应无效处理。 */
@Component
@AllArgsConstructor
public class ClientReviewFileClientImpl implements ClientReviewFileClient {
    private final InternalFileApi files;

    /** 单次查询并核对文件身份。 */
    @Override
    public StoredFileResponseBody file(String fileId) {
        var response = files.getFile(fileId);
        ClientReviewRiskClientImpl.require(response != null && fileId.equals(response.getFileId()));
        return response;
    }

    /** 单次签发并带上操作人。 */
    @Override
    public FileDownloadResponseBody download(OperatorContext operator, String fileId) {
        var response = files.authorizeDownload(fileId, operator.accountId(), null);
        ClientReviewRiskClientImpl.require(response != null && response.getUrl() != null && response.getExpiresAt() != null);
        return response;
    }
}
