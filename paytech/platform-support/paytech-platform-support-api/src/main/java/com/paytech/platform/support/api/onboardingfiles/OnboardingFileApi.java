package com.paytech.platform.support.api.onboardingfiles;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.platform.support.api.onboardingfiles.request.StartUploadRequestBody;
import com.paytech.platform.support.api.onboardingfiles.response.DownloadResponseBody;
import com.paytech.platform.support.api.onboardingfiles.response.FileResponseBody;
import com.paytech.platform.support.api.onboardingfiles.response.UploadCompletionResponseBody;
import com.paytech.platform.support.api.onboardingfiles.response.UploadResponseBody;

/** Client BFF 使用的 Platform 入驻文件合同，部署时只允许可信私网入口。 */
@HttpExchange("/onboarding-files/v1")
public interface OnboardingFileApi {

    /**
     * 查询入驻文件当前元数据
     * <li>不触发扫描；每次核验真实会话与原申请。</li>
     *
     * @param accountId
     *            Gateway/BFF 验证后的账号标识，Platform 仍核验真实会话
     * @param sessionId
     *            当前 CLIENT 会话标识
     * @param fileId
     *            文件 ID
     * @return 当前元数据与允许的动作
     */
    @GetExchange("/files/{fileId}")
    FileResponseBody read(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("fileId") String fileId);

    /**
     * 签发准确不可变版本的短时下载授权
     * <li>每次独立授权；不接受 Body 或幂等回执。</li>
     *
     * @param accountId
     *            Gateway/BFF 验证后的账号标识，Platform 仍核验真实会话
     * @param sessionId
     *            当前 CLIENT 会话标识
     * @param fileId
     *            文件 ID
     * @return 最长 60 秒的下载授权
     */
    @PostExchange("/files/{fileId}/download")
    DownloadResponseBody download(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("fileId") String fileId);

    /**
     * 固定原申请和主体，保存后签发临时上传授权
     * <li>每次请求新建上传；HTTP 请求幂等由 Gateway 处理。</li>
     * <li>Body 约束由 BFF 入口 {@code @Valid} 执行；Platform 以等价的内部请求约束校验，字段错误返回 400 INVALID_REQUEST。</li>
     *
     * @param accountId
     *            Gateway/BFF 验证后的账号标识，Platform 仍核验真实会话
     * @param sessionId
     *            当前 CLIENT 会话标识
     * @param request
     *            声明文件元数据和准确目标
     * @return 稳定上传身份及受控 PUT 授权；HTTP 201
     */
    @PostExchange("/uploads")
    UploadResponseBody start(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @RequestBody StartUploadRequestBody request);

    /**
     * 沿原 Upload 完成登记
     * <li>Body 必须为空；同一 Upload 已完成时返回原文件，回执不包含当前扫描状态。</li>
     *
     * @param accountId
     *            Gateway/BFF 验证后的账号标识，Platform 仍核验真实会话
     * @param sessionId
     *            当前 CLIENT 会话标识
     * @param uploadId
     *            原上传 ID
     * @return 完成回执
     */
    @PostExchange("/uploads/{uploadId}/completion")
    UploadCompletionResponseBody complete(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("uploadId") String uploadId);
}
