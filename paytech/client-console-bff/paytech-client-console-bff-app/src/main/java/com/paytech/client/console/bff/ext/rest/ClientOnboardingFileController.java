package com.paytech.client.console.bff.ext.rest;

import com.paytech.client.console.bff.service.processor.ClientSessionProcessor;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileApi;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileModels.*;
import jakarta.servlet.http.HttpServletRequest;
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
    private final ClientSessionProcessor sessions;

    /** 固定原申请主体，不接受账号、Tenant 或存储目标。 */
    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponseBody start(
        HttpServletRequest request,
        @Valid @RequestBody StartUploadRequestBody body
    ) {
        var actor = sessions.identity(request);
        return files.start(actor[0], actor[1], body);
    }

    /** 空 Body 完成原 Upload；同一 Upload 已完成时由 Platform 返回原文件。 */
    @PostMapping("/uploads/{upload}/completion")
    public FileCompletionReceipt complete(
        HttpServletRequest request,
        @PathVariable String upload
    ) {
        var actor = sessions.identity(request);
        return files.complete(actor[0], actor[1], upload);
    }

    /** GET 不驱动扫描。 */
    @GetMapping("/files/{file}")
    public FileResponseBody read(HttpServletRequest request, @PathVariable String file) {
        var actor = sessions.identity(request);
        return files.read(actor[0], actor[1], file);
    }

    /** 每次由 Platform 对准确不可变版本重新授权。 */
    @PostMapping("/files/{file}/download")
    public DownloadResponseBody download(HttpServletRequest request, @PathVariable String file) {
        var actor = sessions.identity(request);
        return files.download(actor[0], actor[1], file);
    }
}
