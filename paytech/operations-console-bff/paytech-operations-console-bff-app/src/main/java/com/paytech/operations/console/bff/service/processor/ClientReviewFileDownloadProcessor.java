package com.paytech.operations.console.bff.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.ClientReviewFileClient;
import com.paytech.operations.console.bff.integration.ClientReviewRiskClient;
import com.paytech.operations.console.bff.service.model.response.ClientReviewFileDownloadResponseBody;
import com.paytech.operations.console.bff.service.processor.request.ClientReviewFileDownloadRequest;
import com.paytech.platform.support.api.enums.FileErrorCode;

import lombok.AllArgsConstructor;

/**
 * O10 审核材料下载：先读 Risk 任务确认文件属于该任务的证据，再经 Platform 内部文件接口取元数据并签发下载地址
 * <li>任务中不存在的文件按文件不存在处理，不调用 Platform。</li>
 * <li>Platform 内部文件接口不做运营权限判断；任务读取由 Risk 按操作人重检，下载请求带操作人，由 Platform 记审计。</li>
 * <li>先查元数据再签发：文件不存在或元数据失败时不签发地址；文件归属与下载结果以 Risk、Platform 的当次结果为准，BFF 不缓存。</li>
 */
@Component
@AllArgsConstructor
public class ClientReviewFileDownloadProcessor
    extends AbstractProcessor<ClientReviewFileDownloadRequest, ClientReviewFileDownloadResponseBody> {
    private final ClientReviewFileClient files;
    private final ClientReviewRiskClient risk;

    @Override
    protected ClientReviewFileDownloadResponseBody processRequest(ClientReviewFileDownloadRequest request) {
        if (!risk.evidenceFileIds(request.getOperator(), request.getCaseId()).contains(request.getFileId())) {
            throw new FailException(FileErrorCode.FILE_NOT_FOUND);
        }
        var file = files.file(request.getFileId());
        var download = files.download(request.getOperator(), request.getFileId());
        return new ClientReviewFileDownloadResponseBody(file.getFileId(), file.getDisplayName(), file.getMimeType(),
            file.getSizeBytes(), download.getUrl(), download.getExpiresAt());
    }
}
