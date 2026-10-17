package com.paytech.platform.support.api.onboardingfiles;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.platform.support.api.onboardingfiles.OnboardingFileModels.*;

/** Client BFF 使用的 Platform 上传合同，部署时只允许可信私网入口。 */
@HttpExchange("/onboarding-files/v1")
public interface OnboardingFileApi {
    /** 当前元数据查询不触发扫描，必须核验真实会话与原申请。 */
    @GetExchange("/files/{fileId}")
    FileResponseBody read(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,@PathVariable("fileId") String fileId);
    /** 每次独立授权准确不可变版本，不接受 Body 或幂等回执。 */
    @PostExchange("/files/{fileId}/download")
    DownloadResponseBody download(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,@PathVariable("fileId") String fileId);

    /**
     * 固定原申请和主体，保存后签发临时上传授权；每次请求新建上传，HTTP 请求幂等由 Gateway 处理。
     * @param accountId Gateway/BFF 验证后的账号标识，Platform 仍核验真实会话
     * @param sessionId 当前 CLIENT 会话标识
     * @param request 声明文件元数据和准确目标
     * @return 稳定上传身份及受控 PUT 授权；HTTP 201
     */
    @PostExchange("/uploads")
    UploadResponseBody start(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @RequestBody StartUploadRequestBody request);
    /** 沿原 Upload 完成登记；Body 必须为空；同一 Upload 已完成时返回原文件，回执不包含当前扫描状态。 */
    @PostExchange("/uploads/{uploadId}/completion")
    FileCompletionReceipt complete(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,@PathVariable("uploadId") String uploadId);
}
