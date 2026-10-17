package com.paytech.operations.console.bff.integration.impl;

import org.springframework.stereotype.Component;

import com.paytech.operations.console.bff.integration.ClientReviewFileClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.platform.support.api.reviewfiles.ReviewFileApi;
import com.paytech.platform.support.api.reviewfiles.response.ReviewFileDownloadResponseBody;

import lombok.AllArgsConstructor;

/** 转发运营身份；响应必须对应请求的文件且带下载地址，否则按上游响应无效处理。 */
@Component
@AllArgsConstructor
public class ClientReviewFileClientImpl implements ClientReviewFileClient {
    private final ReviewFileApi files;

    /** 单次转发并核对文件身份。 */
    @Override
    public ReviewFileDownloadResponseBody download(OperatorContext operator, String fileId) {
        var response = files.download(operator.accountId(), operator.sessionId(), fileId);
        ClientReviewRiskClientImpl.require(response != null && fileId.equals(response.getFileId())
            && response.getUrl() != null && response.getExpiresAt() != null);
        return response;
    }
}
