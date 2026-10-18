package com.paytech.platform.support.ext.rest.files;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.api.onboardingfiles.OnboardingFileApi;
import com.paytech.platform.support.api.onboardingfiles.request.StartUploadRequestBody;
import com.paytech.platform.support.api.onboardingfiles.response.DownloadResponseBody;
import com.paytech.platform.support.api.onboardingfiles.response.FileResponseBody;
import com.paytech.platform.support.api.onboardingfiles.response.UploadCompletionResponseBody;
import com.paytech.platform.support.api.onboardingfiles.response.UploadResponseBody;
import com.paytech.platform.support.service.processor.DownloadOnboardingFileProcessor;
import com.paytech.platform.support.service.processor.OnboardingFileCompletionProcessor;
import com.paytech.platform.support.service.processor.ReadOnboardingFileProcessor;
import com.paytech.platform.support.service.processor.StartOnboardingUploadProcessor;
import com.paytech.platform.support.service.processor.request.CompleteOnboardingFileRequest;
import com.paytech.platform.support.service.processor.request.OnboardingFileAccessRequest;
import com.paytech.platform.support.service.processor.request.StartOnboardingUploadRequest;
import com.paytech.platform.support.service.processor.response.CompleteOnboardingFileResponse;
import com.paytech.platform.support.service.processor.response.DownloadOnboardingFileResponse;
import com.paytech.platform.support.service.processor.response.ReadOnboardingFileResponse;
import com.paytech.platform.support.service.processor.response.StartOnboardingUploadResponse;

import lombok.AllArgsConstructor;

/**
 * 入驻文件 HTTP 适配：只做协议转换并委托对应 Processor，不接受浏览器自报权限
 * <li>开始上传的字段约束由内部请求经公共 validateBasic 执行，保持 400 INVALID_REQUEST 合同。</li>
 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "files.onboarding.enabled", havingValue = "true")
public class OnboardingFileController implements OnboardingFileApi {

    private final OnboardingFileConverter converter;

    private final ReadOnboardingFileProcessor reads;

    private final StartOnboardingUploadProcessor starts;

    private final DownloadOnboardingFileProcessor downloads;

    private final OnboardingFileCompletionProcessor completions;

    /** 开始上传：解析身份与数值 → Processor → 上传授权；HTTP 201。 */
    @Override
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponseBody start(String accountId, String sessionId, StartUploadRequestBody request) {
        StartOnboardingUploadRequest command = converter.toStartRequest(accountId, sessionId, request);
        StartOnboardingUploadResponse response = starts.process(command);
        return converter.toUploadResponseBody(response);
    }

    /** 完成上传：不接受浏览器自报 Hash 或任何 Body；解析身份 → Processor → 回执。 */
    @Override
    public UploadCompletionResponseBody complete(String accountId, String sessionId, String uploadId) {
        CompleteOnboardingFileRequest command = converter.toCompleteRequest(accountId, sessionId, uploadId);
        CompleteOnboardingFileResponse response = completions.process(command);
        return converter.toCompletionResponseBody(response);
    }

    /** 读取元数据：只解析身份，资源与会话由 Processor 重新核验。 */
    @Override
    public FileResponseBody read(String accountId, String sessionId, String fileId) {
        OnboardingFileAccessRequest command = converter.toAccessRequest(accountId, sessionId, fileId);
        ReadOnboardingFileResponse response = reads.process(command);
        return converter.toFileResponseBody(response);
    }

    /** 下载：每次独立签发；响应不持久化，也不通过通用审计记录临时授权。 */
    @Override
    public DownloadResponseBody download(String accountId, String sessionId, String fileId) {
        OnboardingFileAccessRequest command = converter.toAccessRequest(accountId, sessionId, fileId);
        DownloadOnboardingFileResponse response = downloads.process(command);
        return converter.toDownloadResponseBody(response);
    }
}
