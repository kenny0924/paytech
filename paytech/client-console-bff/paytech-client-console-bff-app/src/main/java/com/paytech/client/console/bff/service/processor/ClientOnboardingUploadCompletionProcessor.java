package com.paytech.client.console.bff.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.integration.PlatformAuthenticationClient;
import com.paytech.client.console.bff.integration.PlatformFileClient;
import com.paytech.client.console.bff.service.model.response.OnboardingUploadCompletionResponseBody;
import com.paytech.client.console.bff.service.processor.request.ClientOnboardingUploadCompletionRequest;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.enums.FileErrorCode;

import lombok.AllArgsConstructor;

/**
 * 完成上传（F02）：沿原上传登记文件，再核对上传人是当前账号
 * <li>Platform 只限定同一来源系统可完成，不区分客户账号；完成后查询文件，上传人不是当前账号时按文件不存在拒绝，不返回文件地址。</li>
 * <li>同一上传重复完成时 Platform 返回原结果；回执不含当前扫描状态，页面另行轮询。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientOnboardingUploadCompletionProcessor
    extends AbstractProcessor<ClientOnboardingUploadCompletionRequest, OnboardingUploadCompletionResponseBody> {
    private final PlatformFileClient files;
    private final PlatformAuthenticationClient authentication;

    @Override
    protected OnboardingUploadCompletionResponseBody processRequest(ClientOnboardingUploadCompletionRequest request) {
        var actor = request.getActor();
        authentication.context(actor);
        var receipt = files.completeUpload(request.getUploadId());
        if (!ClientOnboardingFileAccess.uploadedBy(files.file(receipt.getFileId()), actor)) {
            throw new FailException(FileErrorCode.FILE_NOT_FOUND);
        }
        return new OnboardingUploadCompletionResponseBody(receipt.getUploadId(), receipt.getUploadId(),
            receipt.getFileId(), receipt.getFileAddress(), receipt.getCompletedAt());
    }
}
