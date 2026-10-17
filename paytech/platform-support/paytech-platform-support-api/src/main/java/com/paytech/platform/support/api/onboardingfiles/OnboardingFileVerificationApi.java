package com.paytech.platform.support.api.onboardingfiles;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.service.annotation.*;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileModels.VerificationResponseBody;
/** CCT 专用私网查询；CCT 自行核验申请与主体，不回调 CCT 形成循环。 */
@HttpExchange("/internal/onboarding-files")
public interface OnboardingFileVerificationApi {
    /** 返回当前最小文件证据，不签发下载 URL。 */
    @GetExchange("/files/{fileId}/verification")
    VerificationResponseBody verify(@RequestHeader("X-Caller-Service") String caller,
        @PathVariable("fileId") String fileId,@RequestParam("expectedApplicationId") String expectedApplicationId,
        @RequestParam("expectedSubjectPartyId") String expectedSubjectPartyId,@RequestParam("expectedClientId") String expectedClientId,
        @RequestParam("requestingAccountId") String requestingAccountId,@RequestParam("purpose") String purpose);
}
