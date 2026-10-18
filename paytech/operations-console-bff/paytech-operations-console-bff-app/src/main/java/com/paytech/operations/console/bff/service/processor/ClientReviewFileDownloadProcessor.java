package com.paytech.operations.console.bff.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.ClientReviewFileClient;
import com.paytech.operations.console.bff.integration.ClientReviewRiskClient;
import com.paytech.operations.console.bff.service.processor.request.ClientReviewFileDownloadRequest;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.api.reviewfiles.response.ReviewFileDownloadResponseBody;

import lombok.AllArgsConstructor;

/**
 * O10 审核材料下载：先读 Risk 任务确认文件属于该任务的证据，再由 Platform 重检运营权限并签发下载地址
 * <li>任务中不存在的文件按文件不存在处理，不调用 Platform。</li>
 * <li>文件归属与下载权限分别以 Risk、Platform 的当次结果为准，BFF 不缓存。</li>
 */
@Component
@AllArgsConstructor
public class ClientReviewFileDownloadProcessor
    extends AbstractProcessor<ClientReviewFileDownloadRequest, ReviewFileDownloadResponseBody> {
    private final ClientReviewFileClient files;
    private final ClientReviewRiskClient risk;

    @Override
    protected ReviewFileDownloadResponseBody processRequest(ClientReviewFileDownloadRequest request) {
        if (!risk.evidenceFileIds(request.getOperator(), request.getCaseId()).contains(request.getFileId())) {
            throw new FailException(OnboardingFileErrorCode.FILE_NOT_FOUND);
        }
        return files.download(request.getOperator(), request.getFileId());
    }
}
