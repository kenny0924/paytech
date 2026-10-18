package com.paytech.platform.support.ext.rest.files;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.api.reviewfiles.ReviewFileApi;
import com.paytech.platform.support.api.reviewfiles.response.ReviewFileDownloadResponseBody;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.service.processor.ReviewFileDownloadProcessor;
import com.paytech.platform.support.service.processor.request.ReviewFileDownloadRequest;
import com.paytech.platform.support.service.processor.response.ReviewFileDownloadResponse;

import lombok.AllArgsConstructor;

/** 运营审核材料下载的协议适配；权限与文件状态由 Processor 判断。 */
@RestController
@AllArgsConstructor
@ConditionalOnExpression("${files.onboarding.enabled:false} && ${operations.authentication.enabled:false}")
public class ReviewFileController implements ReviewFileApi {
    private final ReviewFileDownloadProcessor processor;

    /** 解析十进制文件标识后委托；每次下载授权都记录审计。 */
    @Audit(action = "OPERATIONS_REVIEW_FILE_DOWNLOAD", resourceType = "FILE", resourceIdParameter = "fileId",
        fallbackResourceId = "INVALID_FILE")
    @Override
    public ReviewFileDownloadResponseBody download(String actorAccountId, String sessionId, String fileId) {
        if (fileId == null || !fileId.matches("[1-9][0-9]{0,18}")) {
            throw new InvalidParameterException("fileId 必须为正整数");
        }
        long id;
        try {
            id = Long.parseLong(fileId);
        } catch (NumberFormatException overflow) {
            throw new InvalidParameterException("fileId 超出范围");
        }
        return body(processor.process(new ReviewFileDownloadRequest(actorAccountId, sessionId, id)));
    }

    /** 文件标识与字节数以十进制字符串返回。 */
    ReviewFileDownloadResponseBody body(ReviewFileDownloadResponse response) {
        return new ReviewFileDownloadResponseBody(Long.toString(response.getFileId()), response.getDisplayName(),
            response.getMimeType(), Long.toString(response.getSizeBytes()), response.getUrl(), response.getExpiresAt());
    }
}
