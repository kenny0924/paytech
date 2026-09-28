package com.paytech.platform.support.ext.rest.files;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileApi;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileModels.*;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Start;
import com.paytech.platform.support.service.processor.OnboardingUploadProcessor;

/** 上传 HTTP 适配，只解析有界输入，不接受浏览器自报权限。 */
@RestController
@lombok.AllArgsConstructor
@ConditionalOnProperty(name="files.onboarding.enabled",havingValue="true")
public class OnboardingFileController implements OnboardingFileApi {
    private final com.paytech.platform.support.service.processor.OnboardingFileCompletionProcessor completion;
    private final com.paytech.platform.support.service.processor.OnboardingFileReadProcessor reads;
    private final jakarta.validation.Validator validator;
    private final OnboardingUploadProcessor processor;
    /** 先校验协议形状，再交给完整用例。 */
    @Override @ResponseStatus(HttpStatus.CREATED)
    public UploadResponseBody start(String accountId,String sessionId,String key,StartUploadRequestBody request) {
        if (!validator.validate(request).isEmpty()) throw new InvalidParameterException("上传请求字段缺失或格式错误");
        long account; long session;
        try { account=id(accountId); session=id(sessionId); }
        catch (InvalidParameterException invalid) { throw new FailException(ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID); }
        try { if (key==null || !UUID.fromString(key).toString().equals(key)) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException invalid) { throw new InvalidParameterException("Idempotency-Key 必须为规范 UUID"); }
        String name=request.filename();
        if (name.codePointCount(0,name.length())>255) throw new InvalidParameterException("文件名最多 255 个字符");
        name=name.replace('\\','/');
        name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cc}\\p{Cf}]", "").strip();
        if (name.isBlank() || name.equals(".") || name.equals("..")) throw new InvalidParameterException("文件展示名无效");
        if (!request.mimeType().matches("[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*"))
            throw new InvalidParameterException("mimeType 必须为规范媒体类型");
        return processor.start(account,session,key,account + ":" + key,new Start(id(request.applicationId()),id(request.subjectPartyId()),
            name,request.mimeType(),id(request.sizeBytes())));
    }
    /** 完成接口不接受浏览器自报 Hash 或任何 Body。 */
    @Override public FileCompletionReceipt complete(String accountId,String sessionId,String key,String uploadId) {
        long account;long session;
        try { account=id(accountId);session=id(sessionId); }
        catch (InvalidParameterException invalid) { throw new FailException(ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID); }
        try { if (key==null || !UUID.fromString(key).toString().equals(key)) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException invalid) { throw new InvalidParameterException("Idempotency-Key 必须为规范 UUID"); }
        return completion.complete(account,session,key,id(uploadId));
    }
    /** 查询入口只解析身份，资源与会话由用例重新核验。 */
    @Override public FileResponseBody read(String accountId,String sessionId,String fileId) {
        return reads.read(sessionIdentity(accountId),sessionIdentity(sessionId),id(fileId));
    }
    /** 下载响应不持久化，也不通过通用审计记录临时授权。 */
    @Override public DownloadResponseBody download(String accountId,String sessionId,String fileId) {
        return reads.download(sessionIdentity(accountId),sessionIdentity(sessionId),id(fileId));
    }
    /** 非法会话坐标统一返回认证失败。 */
    private long sessionIdentity(String value) {
        try { return id(value); }
        catch (InvalidParameterException invalid) { throw new FailException(ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID); }
    }
    /** ID/字节数拒绝前导零与溢出。 */
    private long id(String value) {
        try {
            if (value==null || !value.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) { throw new InvalidParameterException("标识和字节数必须为规范正整数"); }
    }
}
