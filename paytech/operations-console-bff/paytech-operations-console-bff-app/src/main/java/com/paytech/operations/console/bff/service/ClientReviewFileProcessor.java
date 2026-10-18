package com.paytech.operations.console.bff.service;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.operations.console.bff.integration.ClientReviewFileClient;
import com.paytech.operations.console.bff.integration.ClientReviewRiskClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.api.reviewfiles.response.ReviewFileDownloadResponseBody;

import lombok.AllArgsConstructor;

/**
 * O10 审核材料下载：先读 Risk 任务确认文件属于该任务的证据，再由 Platform 重检运营权限并签发下载地址。
 * <li>任务中不存在的文件按文件不存在处理，不调用 Platform</li>
 */
@Component
@AllArgsConstructor
public class ClientReviewFileProcessor {
    private final ClientReviewRiskClient risk;
    private final ClientReviewFileClient files;

    /**
     * 签发审核材料下载地址。
     *
     * @param operator 当前运营身份
     * @param caseId 审核任务
     * @param fileId 任务证据中的文件
     * @return Platform 下载授权
     */
    public ReviewFileDownloadResponseBody download(OperatorContext operator, String caseId, String fileId) {
        if (!risk.evidenceFileIds(operator, caseId).contains(fileId)) {
            throw new FailException(OnboardingFileErrorCode.FILE_NOT_FOUND);
        }
        return files.download(operator, fileId);
    }
}
