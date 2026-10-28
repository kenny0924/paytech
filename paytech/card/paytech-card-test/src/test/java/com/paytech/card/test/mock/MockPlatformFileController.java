package com.paytech.card.test.mock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.test.mocker.PlatformMocker;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.enums.FileDisposition;
import com.paytech.platform.support.api.files.InternalFileApi;
import com.paytech.platform.support.api.files.request.StartFileReplacementRequestBody;
import com.paytech.platform.support.api.files.request.StartFileUploadRequestBody;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadConstraintsResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;

import lombok.AllArgsConstructor;

/**
 * 仅存在于独立测试 HTTP 服务的 Platform 内部文件接口（Platform Context Path /platform-support）
 * <li>下载授权返回指向同一 Mock 服务 {@link MockObjectStorageController} 的短时地址，card 用真实下载客户端读取。</li>
 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockPlatformFileController implements InternalFileApi, WebMvcConfigurer {
    private final PlatformMocker platform;

    /** Platform 的 Context Path：同一前缀只能登记一次，文件接口与国家区域映射共用。 */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/platform-support", InternalFileApi.class::isAssignableFrom);
    }

    /** card 不上传文件。 */
    @Override
    public FileUploadResponseBody startUpload(StartFileUploadRequestBody requestBody) {
        throw platform.unexpectedUpload();
    }

    /** card 不替换文件内容。 */
    @Override
    public FileUploadResponseBody startReplacement(String fileId, StartFileReplacementRequestBody requestBody) {
        throw platform.unexpectedUpload();
    }

    /** card 不完成上传。 */
    @Override
    public FileUploadCompletionResponseBody completeUpload(String uploadId) {
        throw platform.unexpectedUpload();
    }

    /** card 不查询写入限制。 */
    @Override
    public FileUploadConstraintsResponseBody getUploadConstraints(String accessLevel) {
        throw platform.unexpectedUpload();
    }

    /** card 不分页查询自己来源的文件。 */
    @Override
    public PagingResponseBody<StoredFileResponseBody> listFiles(Instant createdFrom, Instant createdTo, String reasonCode,
        String accessLevel, String uploaderAccountId, Integer page, Integer size) {
        throw platform.unexpectedUpload();
    }

    /** 文件元数据；错误响应的 X-Request-Id 与 Problem 体一致。 */
    @Override
    public StoredFileResponseBody getFile(String fileId) {
        ((ServletRequestAttributes)RequestContextHolder.currentRequestAttributes()).getResponse()
            .setHeader("X-Request-Id", PlatformMocker.REQUEST_ID);
        return platform.getFile(fileId, caller());
    }

    /** 签发指向测试对象存储的下载地址，60 秒有效。 */
    @Override
    public FileDownloadResponseBody authorizeDownload(String fileId, String operatorAccountId, String disposition) {
        platform.authorizeDownload(fileId, caller(), operatorAccountId, disposition);
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path(MockObjectStorageController.PATH_PREFIX + fileId).build().toUriString();
        return new FileDownloadResponseBody(url, Instant.now().plus(60, ChronoUnit.SECONDS),
            FileDisposition.ATTACHMENT.getCode());
    }

    /** 本次请求的调用方服务名 Header。 */
    static String caller() {
        return ((ServletRequestAttributes)RequestContextHolder.currentRequestAttributes()).getRequest()
            .getHeader(CardApiConstants.CALLER_HEADER);
    }
}
