package com.paytech.client.console.bff.ext.rest;

import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileApi;
import com.paytech.platform.support.api.onboardingfiles.request.StartUploadRequestBody;
import com.paytech.platform.support.api.onboardingfiles.response.DownloadResponseBody;
import com.paytech.platform.support.api.onboardingfiles.response.FileResponseBody;
import com.paytech.platform.support.api.onboardingfiles.response.UploadCompletionResponseBody;
import com.paytech.platform.support.api.onboardingfiles.response.UploadResponseBody;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 文件字节由浏览器按 Platform 授权直接 PUT；BFF 不接收文件正文。 */
@RestController
@lombok.AllArgsConstructor
@RequestMapping("/onboarding-files/v1")
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientOnboardingFileController {

    private final OnboardingFileApi files;

    /** 固定原申请主体，不接受账号、Tenant 或存储目标。 */
    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponseBody start(
        ClientActor actor,
        @Valid @RequestBody StartUploadRequestBody body
    ) {
        return files.start(actor.getAccountId(), actor.getSessionId(), body);
    }

    /** 空 Body 完成原 Upload；同一 Upload 已完成时由 Platform 返回原文件。 */
    @PostMapping("/uploads/{upload}/completion")
    public UploadCompletionResponseBody complete(
        ClientActor actor,
        @PathVariable String upload
    ) {
        return files.complete(actor.getAccountId(), actor.getSessionId(), upload);
    }

    /** GET 不驱动扫描。 */
    @GetMapping("/files/{file}")
    public FileResponseBody read(ClientActor actor, @PathVariable String file) {
        return files.read(actor.getAccountId(), actor.getSessionId(), file);
    }

    /** 每次由 Platform 对准确不可变版本重新授权。 */
    @PostMapping("/files/{file}/download")
    public DownloadResponseBody download(ClientActor actor, @PathVariable String file) {
        return files.download(actor.getAccountId(), actor.getSessionId(), file);
    }
}
