package com.paytech.platform.support.ext.rest.files;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import com.paytech.platform.support.api.enums.OnboardingFilePurpose;
import com.paytech.platform.support.api.onboardingfiles.request.StartUploadRequestBody;
import com.paytech.platform.support.api.onboardingfiles.response.DownloadResponseBody;
import com.paytech.platform.support.api.onboardingfiles.response.FileResponseBody;
import com.paytech.platform.support.api.onboardingfiles.response.FileVerificationResponseBody;
import com.paytech.platform.support.api.onboardingfiles.response.UploadAuthorization;
import com.paytech.platform.support.api.onboardingfiles.response.UploadCompletionResponseBody;
import com.paytech.platform.support.api.onboardingfiles.response.UploadResponseBody;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Grant;
import com.paytech.platform.support.service.processor.request.CompleteOnboardingFileRequest;
import com.paytech.platform.support.service.processor.request.OnboardingFileAccessRequest;
import com.paytech.platform.support.service.processor.request.StartOnboardingUploadRequest;
import com.paytech.platform.support.service.processor.request.VerifyOnboardingFileRequest;
import com.paytech.platform.support.service.processor.response.CompleteOnboardingFileResponse;
import com.paytech.platform.support.service.processor.response.DownloadOnboardingFileResponse;
import com.paytech.platform.support.service.processor.response.ReadOnboardingFileResponse;
import com.paytech.platform.support.service.processor.response.StartOnboardingUploadResponse;
import com.paytech.platform.support.service.processor.response.VerifyOnboardingFileResponse;

/**
 * 入驻文件 HTTP 协议与内部 Request/Response 的转换
 * <li>账号与会话 Header 非规范正整数时统一返回 CLIENT_SESSION_INVALID；其他标识与字节数返回参数错误。</li>
 * <li>只做字符串 ID、Code 与时间等协议转换，不查询数据或判断业务资格。</li>
 */
@Component
@ConditionalOnProperty(name = "files.onboarding.enabled", havingValue = "true")
public class OnboardingFileConverter {

    /** 规范正整数：无符号、无前导零，最多 19 位。 */
    private static final String CANONICAL_ID = "[1-9][0-9]{0,18}";

    /** 上传入口的标识与字节数错误提示。 */
    private static final String INVALID_UPLOAD_ID = "标识和字节数必须为规范正整数";

    /** 核验入口的标识错误提示。 */
    private static final String INVALID_VERIFICATION_ID = "文件核验标识必须为规范正整数";

    /** 上传授权固定使用 PUT。 */
    private static final String UPLOAD_METHOD = "PUT";

    /** 开始上传：先解析会话身份，再解析申请、主体与字节数；文件名与 MIME 原样交给 Processor 校验。 */
    public StartOnboardingUploadRequest toStartRequest(String accountId, String sessionId,
        StartUploadRequestBody body) {
        long account = sessionIdentity(accountId);
        long session = sessionIdentity(sessionId);
        return new StartOnboardingUploadRequest(account, session, id(body.getApplicationId(), INVALID_UPLOAD_ID),
            id(body.getSubjectPartyId(), INVALID_UPLOAD_ID), body.getFilename(), body.getMimeType(),
            id(body.getSizeBytes(), INVALID_UPLOAD_ID));
    }

    /** 完成上传：解析会话身份与原上传 ID。 */
    public CompleteOnboardingFileRequest toCompleteRequest(String accountId, String sessionId, String uploadId) {
        return new CompleteOnboardingFileRequest(sessionIdentity(accountId), sessionIdentity(sessionId),
            id(uploadId, INVALID_UPLOAD_ID));
    }

    /** 读取或下载：解析会话身份与文件 ID。 */
    public OnboardingFileAccessRequest toAccessRequest(String accountId, String sessionId, String fileId) {
        return new OnboardingFileAccessRequest(sessionIdentity(accountId), sessionIdentity(sessionId),
            id(fileId, INVALID_UPLOAD_ID));
    }

    /**
     * 核验：先校验用途代码，再解析各期望标识
     * <li>用途代码目前只校验合法性，不改变核验范围。</li>
     */
    public VerifyOnboardingFileRequest toVerifyRequest(String fileId, String applicationId, String subjectPartyId,
        String clientId, String accountId, String purpose) {
        requirePurpose(purpose);
        return new VerifyOnboardingFileRequest(id(accountId, INVALID_VERIFICATION_ID),
            id(fileId, INVALID_VERIFICATION_ID), id(applicationId, INVALID_VERIFICATION_ID),
            id(subjectPartyId, INVALID_VERIFICATION_ID), id(clientId, INVALID_VERIFICATION_ID));
    }

    /** 开始上传响应：稳定上传 ID 与固定 PUT 的临时授权。 */
    public UploadResponseBody toUploadResponseBody(StartOnboardingUploadResponse response) {
        Grant grant = response.getAuthorization();
        UploadAuthorization authorization =
            new UploadAuthorization(UPLOAD_METHOD, grant.url(), grant.headers(), grant.expiresAt());
        return new UploadResponseBody(Long.toString(response.getUploadId()), authorization, response.getExpiresAt());
    }

    /** 完成回执中的 commandId 与 uploadId 相同：完成操作的身份就是上传本身。 */
    public UploadCompletionResponseBody toCompletionResponseBody(CompleteOnboardingFileResponse response) {
        String uploadId = Long.toString(response.getUploadId());
        return new UploadCompletionResponseBody(uploadId, uploadId, Long.toString(response.getFileId()),
            response.getCompletedAt());
    }

    /** 文件元数据：数值与状态转为稳定字符串。 */
    public FileResponseBody toFileResponseBody(ReadOnboardingFileResponse response) {
        List<String> actions = response.getAllowedActions().stream().map(CodeEnum::getCode).toList();
        return new FileResponseBody(Long.toString(response.getFileId()), response.getDisplayName(),
            Long.toString(response.getSizeBytes()), response.getMimeType(), response.getScanStatus().getCode(),
            response.getObjectStatus().getCode(), actions);
    }

    /** 下载授权原样回传 URL 与实际到期时间。 */
    public DownloadResponseBody toDownloadResponseBody(DownloadOnboardingFileResponse response) {
        return new DownloadResponseBody(response.getUrl(), response.getExpiresAt());
    }

    /** 核验证据：数值与状态转为稳定字符串。 */
    public FileVerificationResponseBody toVerificationResponseBody(VerifyOnboardingFileResponse response) {
        return new FileVerificationResponseBody(Long.toString(response.getFileId()),
            Long.toString(response.getUploadId()), response.getContentHash(), Long.toString(response.getSizeBytes()),
            response.getMimeType(), response.getScanStatus().getCode(), response.getObjectStatus().getCode(),
            response.getCheckedAt());
    }

    /** 未知用途代码返回参数错误，不映射成默认许可。 */
    void requirePurpose(String purpose) {
        try {
            OnboardingFilePurpose.fromCode(purpose);
        } catch (IllegalArgumentException invalid) {
            throw new InvalidParameterException("文件核验用途无效");
        }
    }

    /** 非法会话坐标统一返回认证失败。 */
    long sessionIdentity(String value) {
        try {
            return id(value, INVALID_UPLOAD_ID);
        } catch (InvalidParameterException invalid) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        }
    }

    /** 标识与字节数拒绝空值、前导零、符号与溢出；无效输入不进入错误正文。 */
    long id(String value, String message) {
        if (value == null || !value.matches(CANONICAL_ID)) {
            throw new InvalidParameterException(message);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException overflow) {
            throw new InvalidParameterException(message);
        }
    }
}
