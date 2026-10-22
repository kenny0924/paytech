package com.paytech.client.console.bff.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.service.model.response.OnboardingFileResponseBody;
import com.paytech.client.console.bff.service.processor.request.ClientOnboardingFileRequest;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 查询入驻文件（F03）：按 {@link ClientOnboardingFileAccess} 授权后裁剪页面字段
 * <li>GET 不驱动扫描；扫描可用且对象有效时才给出 DOWNLOAD 动作。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientOnboardingFileReadProcessor
    extends AbstractProcessor<ClientOnboardingFileRequest, OnboardingFileResponseBody> {
    private final ClientOnboardingFileAccess access;

    @Override
    protected OnboardingFileResponseBody processRequest(ClientOnboardingFileRequest request) {
        return OnboardingFileResponseBody.of(access.readableFile(request.getActor(), request.getFileId()));
    }
}
