package com.paytech.platform.support.ext.rest.files;

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.internalauth.api.CallerIdentity;
import com.paytech.foundation.internalauth.api.CallerIdentityHolder;
import com.paytech.foundation.internalauth.api.InternalTokenInvalidException;
import com.paytech.platform.support.api.enums.FileDisposition;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.api.files.request.StartFileReplacementRequestBody;
import com.paytech.platform.support.api.files.request.StartFileUploadRequestBody;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadAuthorization;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.file.StoredFileView;
import com.paytech.platform.support.service.processor.files.request.AuthorizeFileDownloadRequest;
import com.paytech.platform.support.service.processor.files.request.CompleteFileUploadRequest;
import com.paytech.platform.support.service.processor.files.request.StartFileReplacementRequest;
import com.paytech.platform.support.service.processor.files.request.StartFileUploadRequest;
import com.paytech.platform.support.service.processor.files.response.AuthorizeFileDownloadResponse;
import com.paytech.platform.support.service.processor.files.response.CompleteFileUploadResponse;
import com.paytech.platform.support.service.processor.files.response.StartFileUploadResponse;

/**
 * 内部文件接口的协议转换：来源系统取 Foundation internal-auth 已识别的调用身份，Path 与 Body 中的 ID 按规范正整数解析
 * <li>没有调用身份（internal-auth.mode=OFF，或 DUAL 下既无 Token 也无合法 X-Caller-Service）返回 401 INTERNAL_TOKEN_INVALID。</li>
 */
@Component
@ConditionalOnProperty(name = "files.enabled", havingValue = "true")
public class InternalFileConverter {

    private static final String CANONICAL_ID = "[1-9][0-9]{0,18}";

    private static final String UPLOAD_METHOD = "PUT";

    /** 无请求 ID 时写入审计的占位值（正常经 RequestIdFilter 总是存在）。 */
    private static final String UNKNOWN_REQUEST = "UNKNOWN";

    /** 当前请求的调用方服务名；没有调用身份时拒绝。 */
    String callerService() {
        CallerIdentity identity = CallerIdentityHolder.current();
        if (identity == null) {
            throw new InternalTokenInvalidException("caller identity is required");
        }
        return identity.caller();
    }

    /** 开始上传：来源系统取调用身份，数值字段与访问级别已由 Body 注解约束，访问级别缺省为 PRIVATE。 */
    StartFileUploadRequest start(StartFileUploadRequestBody body) {
        Long uploader = body.getUploaderAccountId() == null ? null : Long.parseLong(body.getUploaderAccountId());
        return new StartFileUploadRequest(callerService(), body.getReasonCode(), body.getFilename(),
            body.getMimeType(), Long.parseLong(body.getSizeBytes()), uploader,
            FileAccessLevel.fromNullableCode(body.getAccessLevel()));
    }

    /** 开始替换：来源系统取调用身份，文件 ID 按规范正整数解析。 */
    StartFileReplacementRequest replacement(String fileId, StartFileReplacementRequestBody body) {
        String caller = callerService();
        Long uploader = body.getUploaderAccountId() == null ? null : Long.parseLong(body.getUploaderAccountId());
        return new StartFileReplacementRequest(caller, id(fileId, "文件 ID 必须为规范正整数"), body.getFilename(),
            body.getMimeType(), Long.parseLong(body.getSizeBytes()), uploader);
    }

    /** 完成上传：来源系统取调用身份。 */
    CompleteFileUploadRequest complete(String uploadId) {
        return new CompleteFileUploadRequest(callerService(), id(uploadId, "上传 ID 必须为规范正整数"));
    }

    /** 下载授权：要求存在调用身份；可选人工操作人按规范正整数解析，下载方式缺省为附件，请求 ID 取 MDC 供审计。 */
    AuthorizeFileDownloadRequest downloadRequest(String fileId, String operatorAccountId, String disposition) {
        String caller = callerService();
        Long operator = operatorAccountId == null ? null : id(operatorAccountId, "操作人账号 ID 必须为规范正整数");
        String requestId = MDC.get(ApiProblemProfile.REQUEST_ID_MDC_KEY);
        return new AuthorizeFileDownloadRequest(caller, id(fileId, "文件 ID 必须为规范正整数"), disposition(disposition),
            operator, requestId == null ? UNKNOWN_REQUEST : requestId, null);
    }

    /** 下载方式：缺省或为空按附件，未知 Code 拒绝。 */
    FileDisposition disposition(String value) {
        if (value == null || value.isEmpty()) {
            return FileDisposition.ATTACHMENT;
        }
        try {
            return FileDisposition.fromCode(value);
        } catch (IllegalArgumentException unknown) {
            throw new InvalidParameterException("下载方式只能为 INLINE 或 ATTACHMENT");
        }
    }

    /** 解析规范正整数 ID。 */
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

    /** 上传结果：ID 输出十进制字符串，授权固定为 PUT。 */
    FileUploadResponseBody upload(StartFileUploadResponse response) {
        var grant = response.getGrant();
        return new FileUploadResponseBody(Long.toString(response.getUploadId()),
            new FileUploadAuthorization(UPLOAD_METHOD, grant.url(), grant.headers(), grant.expiresAt()),
            response.getExpiresAt());
    }

    /** 完成回执：文件 ID 同时输出文件地址，附本次版本号与访问级别。 */
    FileUploadCompletionResponseBody completion(CompleteFileUploadResponse response) {
        return new FileUploadCompletionResponseBody(Long.toString(response.getUploadId()),
            Long.toString(response.getFileId()), FileAddress.of(response.getFileId()).value(),
            response.getCompletedAt(), response.getVersionNo(), response.getAccessLevel().getCode());
    }

    /** 文件元数据：ID 与大小输出十进制字符串并附文件地址，状态与访问级别输出稳定 Code。 */
    StoredFileResponseBody file(StoredFileView view) {
        return new StoredFileResponseBody(view.getFileId().toString(), FileAddress.of(view.getFileId()).value(),
            view.getSourceSystem(), view.getReasonCode(),
            view.getDisplayName(), view.getMimeType(), view.getSizeBytes().toString(), view.getContentHash(),
            view.getScanStatus().getCode(), view.getObjectStatus().getCode(),
            view.getUploaderId() == null ? null : view.getUploaderId().toString(), view.getCreateTime(),
            view.getAccessLevel().getCode(), view.getVersionNo(), view.getPublicUrl(), view.getVersionedUrl(),
            view.getPendingVersionNo());
    }

    /** 下载授权与实际生效的下载方式。 */
    FileDownloadResponseBody downloadResult(AuthorizeFileDownloadResponse response) {
        return new FileDownloadResponseBody(response.getUrl(), response.getExpiresAt(),
            response.getDisposition().getCode());
    }
}
