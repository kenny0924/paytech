package com.paytech.platform.support.ext.rest.files;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileVerificationApi;
import com.paytech.platform.support.api.onboardingfiles.response.FileVerificationResponseBody;
import com.paytech.platform.support.service.processor.VerifyOnboardingFileProcessor;
import com.paytech.platform.support.service.processor.request.VerifyOnboardingFileRequest;
import com.paytech.platform.support.service.processor.response.VerifyOnboardingFileResponse;

import lombok.AllArgsConstructor;

/** CCT 专用私网入口；Caller Header 仅防误用，部署必须限制私网调用，不能当作强身份认证。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "files.onboarding.enabled", havingValue = "true")
public class OnboardingFileVerificationController implements OnboardingFileVerificationApi {

    /** 唯一允许的调用方服务名。 */
    private static final String CCT_CALLER = "client-master-service";

    private final OnboardingFileConverter converter;

    private final VerifyOnboardingFileProcessor verifications;

    /** 非 CCT 调用方按文件不存在拒绝，不接受 Client Header 冒充审核用途；不递归查询 CCT。 */
    @Override
    public FileVerificationResponseBody verify(String caller, String fileId, String applicationId,
        String subjectPartyId, String clientId, String accountId, String purpose) {
        if (!CCT_CALLER.equals(caller)) {
            throw new FailException(OnboardingFileErrorCode.FILE_NOT_FOUND);
        }
        VerifyOnboardingFileRequest command =
            converter.toVerifyRequest(fileId, applicationId, subjectPartyId, clientId, accountId, purpose);
        VerifyOnboardingFileResponse response = verifications.process(command);
        return converter.toVerificationResponseBody(response);
    }
}
