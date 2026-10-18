package com.paytech.client.console.bff.integration.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.integration.PlatformOnboardingFileClient;
import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.OnboardingFileAction;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileApi;
import com.paytech.platform.support.api.onboardingfiles.response.FileResponseBody;

import lombok.AllArgsConstructor;

/**
 * 调用 Platform 入驻文件查询并校验回执
 * <li>扫描与对象状态必须是已发布代码；未知状态不默认开放下载。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class PlatformOnboardingFileClientImpl implements PlatformOnboardingFileClient {
    private final OnboardingFileApi files;

    /** 读取文件元数据并校验身份、状态码与允许动作。 */
    @Override
    public FileResponseBody file(ClientActor actor, String fileId) {
        var value = files.read(actor.getAccountId(), actor.getSessionId(), fileId);
        if (value == null || !fileId.equals(value.getFileId()) || value.getAllowedActions() == null) {
            throw new ServiceUnavailableException();
        }
        FileScanStatus.fromCode(value.getScanStatus());
        FileObjectStatus.fromCode(value.getObjectStatus());
        if (value.getAllowedActions().stream().anyMatch(action -> !OnboardingFileAction.DOWNLOAD.getCode().equals(action))) {
            throw new ServiceUnavailableException();
        }
        return value;
    }
}
