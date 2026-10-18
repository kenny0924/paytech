package com.paytech.platform.support.api.onboardingfiles;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.platform.support.api.onboardingfiles.response.FileVerificationResponseBody;

/** CCT 专用私网查询；CCT 自行核验申请与主体，不回调 CCT 形成循环。 */
@HttpExchange("/internal/onboarding-files")
public interface OnboardingFileVerificationApi {

    /**
     * 返回当前最小文件证据，不签发下载 URL
     *
     * @param caller 固定 client-master-service；不能替代私网隔离
     * @param fileId 文件 ID
     * @param expectedApplicationId CCT 已核验的原申请 ID
     * @param expectedSubjectPartyId CCT 已核验的材料主体 Party ID
     * @param expectedClientId CCT 已核验的申请所属 Client ID
     * @param requestingAccountId 原上传账号 ID
     * @param purpose OnboardingFilePurpose 稳定代码
     * @return 文件证据；归属不符统一返回 404
     */
    @GetExchange("/files/{fileId}/verification")
    FileVerificationResponseBody verify(@RequestHeader("X-Caller-Service") String caller,
        @PathVariable("fileId") String fileId, @RequestParam("expectedApplicationId") String expectedApplicationId,
        @RequestParam("expectedSubjectPartyId") String expectedSubjectPartyId,
        @RequestParam("expectedClientId") String expectedClientId,
        @RequestParam("requestingAccountId") String requestingAccountId, @RequestParam("purpose") String purpose);
}
