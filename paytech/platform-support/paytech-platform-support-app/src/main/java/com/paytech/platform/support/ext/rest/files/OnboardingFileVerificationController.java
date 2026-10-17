package com.paytech.platform.support.ext.rest.files;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;
import com.paytech.foundation.common.error.*;
import com.paytech.platform.support.api.enums.*;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileVerificationApi;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileModels.VerificationResponseBody;
import com.paytech.platform.support.service.processor.OnboardingFileReadProcessor;

/** CCT 专用私网入口；Caller Header 仅防误用，部署必须限制私网调用，不能当作强身份认证。 */
@RestController @lombok.AllArgsConstructor
@ConditionalOnProperty(name="files.onboarding.enabled",havingValue="true")
public class OnboardingFileVerificationController implements OnboardingFileVerificationApi {
    private final OnboardingFileReadProcessor processor;
    /** 不接受 Client Header 冒充运营审核用途；不递归查询 CCT。 */
    @Override public VerificationResponseBody verify(String caller,String fileId,String applicationId,String subjectPartyId,
        String clientId,String accountId,String purpose) {
        if (!"client-master-service".equals(caller)) throw new FailException(OnboardingFileErrorCode.FILE_NOT_FOUND);
        try { OnboardingFilePurpose.fromCode(purpose); }
        catch (IllegalArgumentException invalid) { throw new InvalidParameterException("文件核验用途无效"); }
        return processor.verify(id(accountId),id(fileId),id(applicationId),id(subjectPartyId),id(clientId));
    }
    /** ID 必须为规范正整数；无效输入不进入日志或错误正文。 */
    private long id(String value) {
        try {
            if (value==null || !value.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) { throw new InvalidParameterException("文件核验标识必须为规范正整数"); }
    }
}
