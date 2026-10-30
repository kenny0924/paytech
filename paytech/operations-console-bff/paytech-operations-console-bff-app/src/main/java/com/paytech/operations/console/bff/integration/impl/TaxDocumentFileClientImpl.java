package com.paytech.operations.console.bff.integration.impl;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.operations.console.bff.integration.TaxDocumentFileClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.platform.support.api.files.InternalFileApi;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;

import lombok.AllArgsConstructor;

/** 税票下载授权：带操作人单次签发，按 Platform 默认附件方式下载；授权缺少地址或到期时间按上游响应无效处理。 */
@Component
@AllArgsConstructor
public class TaxDocumentFileClientImpl implements TaxDocumentFileClient {
    private final InternalFileApi files;

    /** 单次签发并带上操作人。 */
    @Override
    public FileDownloadResponseBody authorizeDownload(OperatorContext operator, String fileId) {
        var response = files.authorizeDownload(fileId, operator.accountId(), null);
        if (response == null || response.getUrl() == null || response.getExpiresAt() == null) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        return response;
    }
}
