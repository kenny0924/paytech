package com.paytech.tax.test.mock.external;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.files.InternalFileApi;
import com.paytech.platform.support.api.files.request.StartFileReplacementRequestBody;
import com.paytech.platform.support.api.files.request.StartFileUploadRequestBody;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadAuthorization;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadConstraintsResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;
import com.paytech.tax.test.mocker.external.PlatformFileMocker;

/**
 * 仅存在于独立测试 HTTP 服务的 Platform 内部文件接口（Platform Context Path /platform-support）
 * <li>开始上传签发指向同一 Mock 服务 {@link MockObjectStorageController} 的 PUT 地址；tax 用真实直传客户端写入。</li>
 * <li>tax 只开始与完成上传；其余操作一律记为意外调用。</li>
 */
@RestController
@TestComponent
public class MockPlatformInternalFileApi implements InternalFileApi, WebMvcConfigurer {
    private final PlatformFileMocker platform;

    /** 注入 Mocker。 */
    public MockPlatformInternalFileApi(PlatformFileMocker platform) {
        this.platform = platform;
    }

    /** Platform 的 Context Path。 */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/platform-support", InternalFileApi.class::isAssignableFrom);
    }

    /** 开始上传：签发指向 Mock 对象存储的 PUT 授权。 */
    @Override
    public FileUploadResponseBody startUpload(StartFileUploadRequestBody requestBody) {
        markRequestId();
        String uploadId = platform.startUpload(requestBody, header("X-Caller-Service"));
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path(MockObjectStorageController.PATH_PREFIX + uploadId).build().toUriString();
        Instant expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES);
        return new FileUploadResponseBody(uploadId,
            new FileUploadAuthorization("PUT", url, Map.of("Content-Type", requestBody.getMimeType()), expiresAt), expiresAt);
    }

    /** tax 不替换文件。 */
    @Override
    public FileUploadResponseBody startReplacement(String fileId, StartFileReplacementRequestBody requestBody) {
        throw platform.unexpected("startReplacement");
    }

    /** 完成上传：返回 /files/v1/{fileId}。 */
    @Override
    public FileUploadCompletionResponseBody completeUpload(String uploadId) {
        markRequestId();
        String fileId = platform.complete(uploadId);
        return new FileUploadCompletionResponseBody(uploadId, fileId, "/files/v1/" + fileId, Instant.now(), 1, "PRIVATE");
    }

    /** tax 不查询文件。 */
    @Override
    public StoredFileResponseBody getFile(String fileId) {
        throw platform.unexpected("getFile");
    }

    /** tax 不查询写入限制。 */
    @Override
    public FileUploadConstraintsResponseBody getUploadConstraints(String accessLevel) {
        throw platform.unexpected("getUploadConstraints");
    }

    /** tax 不分页查询文件。 */
    @Override
    public PagingResponseBody<StoredFileResponseBody> listFiles(Instant createdFrom, Instant createdTo, String reasonCode,
        String accessLevel, String uploaderAccountId, Integer page, Integer size) {
        throw platform.unexpected("listFiles");
    }

    /** tax 不签发下载。 */
    @Override
    public FileDownloadResponseBody authorizeDownload(String fileId, String operatorAccountId, String disposition) {
        throw platform.unexpected("authorizeDownload");
    }

    /** 响应 X-Request-Id 与 Problem 体一致。 */
    static void markRequestId() {
        ((ServletRequestAttributes)RequestContextHolder.currentRequestAttributes()).getResponse()
            .setHeader("X-Request-Id", PlatformFileMocker.REQUEST_ID);
    }

    /** 本次请求的 Header。 */
    static String header(String name) {
        return ((ServletRequestAttributes)RequestContextHolder.currentRequestAttributes()).getRequest().getHeader(name);
    }
}
