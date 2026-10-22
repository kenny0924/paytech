package com.paytech.client.console.bff.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.integration.PlatformAuthenticationClient;
import com.paytech.client.console.bff.integration.PlatformFileClient;
import com.paytech.client.console.bff.service.processor.request.ClientOnboardingUploadRequest;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.files.request.StartFileUploadRequestBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;

import lombok.AllArgsConstructor;

/**
 * 开始上传入驻材料（F01）：只要求会话有效，不带申请或主体
 * <li>先实时回读 Platform 会话；Platform 内部文件接口不核验客户会话。</li>
 * <li>上传原因固定为 CLIENT_ONBOARDING_MATERIAL，上传人为当前账号；来源系统由调用身份 client-console-bff 决定。</li>
 * <li>每次请求新建上传；HTTP 请求幂等由 Gateway 处理。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientOnboardingUploadProcessor
    extends AbstractProcessor<ClientOnboardingUploadRequest, FileUploadResponseBody> {
    /** 客户入驻材料的上传原因代码。 */
    static final String ONBOARDING_MATERIAL_REASON = "CLIENT_ONBOARDING_MATERIAL";

    private final PlatformFileClient files;
    private final PlatformAuthenticationClient authentication;

    @Override
    protected FileUploadResponseBody processRequest(ClientOnboardingUploadRequest request) {
        authentication.context(request.getActor());
        var body = new StartFileUploadRequestBody();
        body.setReasonCode(ONBOARDING_MATERIAL_REASON);
        body.setFilename(request.getFilename());
        body.setMimeType(request.getMimeType());
        body.setSizeBytes(request.getSizeBytes());
        body.setUploaderAccountId(request.getActor().getAccountId());
        return files.startUpload(body);
    }
}
