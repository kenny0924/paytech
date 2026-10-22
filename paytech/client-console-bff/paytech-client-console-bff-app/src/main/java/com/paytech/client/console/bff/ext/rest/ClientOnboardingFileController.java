package com.paytech.client.console.bff.ext.rest;

import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.client.console.bff.service.model.request.OnboardingUploadRequestBody;
import com.paytech.client.console.bff.service.model.response.OnboardingFileResponseBody;
import com.paytech.client.console.bff.service.model.response.OnboardingUploadCompletionResponseBody;
import com.paytech.client.console.bff.service.processor.ClientOnboardingFileDownloadProcessor;
import com.paytech.client.console.bff.service.processor.ClientOnboardingFileReadProcessor;
import com.paytech.client.console.bff.service.processor.ClientOnboardingUploadCompletionProcessor;
import com.paytech.client.console.bff.service.processor.ClientOnboardingUploadProcessor;
import com.paytech.client.console.bff.service.processor.request.ClientOnboardingFileRequest;
import com.paytech.client.console.bff.service.processor.request.ClientOnboardingUploadCompletionRequest;
import com.paytech.client.console.bff.service.processor.request.ClientOnboardingUploadRequest;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 文件字节由浏览器按 Platform 授权直接 PUT；BFF 不接收文件正文，经 Platform 内部文件接口完成上传、查询与下载。 */
@RestController
@lombok.AllArgsConstructor
@RequestMapping("/onboarding-files/v1")
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientOnboardingFileController {

    private final ClientOnboardingFileReadProcessor readFile;
    private final ClientOnboardingUploadProcessor startUpload;
    private final ClientOnboardingFileDownloadProcessor downloadFile;
    private final ClientOnboardingUploadCompletionProcessor completeUpload;

    /** 只声明文件本身，不接受申请、主体、账号或存储目标。 */
    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public FileUploadResponseBody start(
        ClientActor actor,
        @Valid @RequestBody OnboardingUploadRequestBody body
    ) {
        return startUpload.process(
            new ClientOnboardingUploadRequest(actor, body.getFilename(), body.getMimeType(), body.getSizeBytes())
        );
    }

    /** 空 Body 完成原 Upload；同一 Upload 已完成时由 Platform 返回原文件。 */
    @PostMapping("/uploads/{upload}/completion")
    public OnboardingUploadCompletionResponseBody complete(
        ClientActor actor,
        @PathVariable String upload
    ) {
        return completeUpload.process(new ClientOnboardingUploadCompletionRequest(actor, upload));
    }

    /** GET 不驱动扫描。 */
    @GetMapping("/files/{file}")
    public OnboardingFileResponseBody read(ClientActor actor, @PathVariable String file) {
        return readFile.process(new ClientOnboardingFileRequest(actor, file));
    }

    /** 每次按同一授权重新签发准确原版本的短时地址。 */
    @PostMapping("/files/{file}/download")
    public FileDownloadResponseBody download(ClientActor actor, @PathVariable String file) {
        return downloadFile.process(new ClientOnboardingFileRequest(actor, file));
    }
}
