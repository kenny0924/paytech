package com.paytech.platform.support.test.base;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.files.request.StartFileReplacementRequestBody;
import com.paytech.platform.support.api.files.request.StartFileUploadRequestBody;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;
import com.paytech.platform.support.filedocument.core.enums.FileVersionStatus;
import com.paytech.platform.support.service.handler.FileScanHandler;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.dal.FileStorageTestMapper;

/**
 * 内部文件接口（公开桶与同地址替换）测试的公共调用：以 X-Caller-Service 身份调用、浏览器按授权直传、有界等待版本检查结论
 * <li>不含 {@code @Test}；internal-auth.mode=DUAL，存储与 Vault 设施沿用 {@link FileTestBase}。</li>
 * <li>只有写入仓库测试存储桶的对象需要登记回收；临时公开桶由 TempBucketMocker 整桶回收。</li>
 */
@TestPropertySource(properties = "internal-auth.mode=DUAL")
public abstract class InternalFileApiTestBase extends FileTestBase {

    /** 内部文件接口前缀。 */
    protected static final String BASE = "/internal/files/v1";

    /** 测试上传原因。 */
    protected static final String REASON = "WORKFLOW_ATTACHMENT";

    @Autowired
    protected FileStorageTestMapper storageData;

    /** 开始上传请求：PDF 声明与大小、上传人 42。 */
    protected static StartFileUploadRequestBody upload(String accessLevel, byte[] bytes, String mime) {
        return new StartFileUploadRequestBody(REASON, "material.pdf", mime, Integer.toString(bytes.length), "42",
            accessLevel);
    }

    /** 开始替换请求：新内容的类型与大小，上传人 43。 */
    protected static StartFileReplacementRequestBody replacement(byte[] bytes, String mime) {
        return new StartFileReplacementRequestBody("replacement.png", mime, Integer.toString(bytes.length), "43", null,
            null);
    }

    /** 开始上传。 */
    protected PlatformResponseChecker start(String caller, StartFileUploadRequestBody body) {
        return call(HttpMethod.POST, BASE + "/uploads", caller, body);
    }

    /** 开始替换。 */
    protected PlatformResponseChecker replace(String caller, long fileId, Object body) {
        return call(HttpMethod.POST, BASE + "/files/" + fileId + "/replacements", caller, body);
    }

    /** 完成上传（首次或替换）。 */
    protected PlatformResponseChecker complete(String caller, String uploadId) {
        return call(HttpMethod.POST, BASE + "/uploads/" + uploadId + "/completion", caller, null);
    }

    /** 查询文件元数据。 */
    protected StoredFileResponseBody describe(String caller, long fileId) {
        return call(HttpMethod.GET, BASE + "/files/" + fileId, caller, null).assertOk().as(StoredFileResponseBody.class);
    }

    /** 签发当前版本下载并以浏览器身份读取字节。 */
    protected byte[] downloadBytes(String caller, long fileId) {
        var download = call(HttpMethod.POST, BASE + "/files/" + fileId + "/download", caller, null).assertOk()
            .as(FileDownloadResponseBody.class);
        return storage.browserGet(download.getUrl()).body();
    }

    /**
     * 按授权以浏览器身份 PUT 字节，要求存储接受
     *
     * @param upload 开始上传或替换的结果
     * @param bytes 本次写入的内容
     */
    protected void put(FileUploadResponseBody upload, byte[] bytes) {
        var authorization = upload.getAuthorization();
        assertEquals(200, storage.browserPut(authorization.getUrl(), authorization.getHeaders(), bytes).statusCode(),
            "预签名 PUT 应被接受");
    }

    /** 上传登记的对象名（用于回收测试存储桶中的对象）。 */
    protected String objectRef(String uploadId) {
        return (String) fileData.uploadRow(Long.parseLong(uploadId)).get("storage_object_ref");
    }

    /** 有界等待文件的指定版本得到检查结论，并等待扫描任务全部结束。 */
    protected void awaitVersion(long fileId, int versionNo, FileVersionStatus status) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50)).until(() -> fileData.versions(fileId)
            .stream().anyMatch(row -> ((Number) row.get("version_no")).intValue() == versionNo
                && status.getCode().equals(row.get("version_status"))));
        await().atMost(Duration.ofSeconds(15)).until(() -> fileData.activeTasks(FileScanHandler.EVENT_TYPE) == 0);
    }

    /** 文件的指定版本行。 */
    protected Map<String, Object> version(long fileId, int versionNo) {
        return fileData.versions(fileId).stream()
            .filter(row -> ((Number) row.get("version_no")).intValue() == versionNo).findFirst()
            .orElseThrow(() -> new AssertionError("版本不存在: " + fileId + ":v" + versionNo));
    }

    /** 文件当前扫描状态。 */
    protected String scanStatus(long fileId) {
        return (String) fileData.file(fileId).get("scan_status");
    }

    /** 扫描可用状态 Code。 */
    protected static String available() {
        return FileScanStatus.AVAILABLE.getCode();
    }

    /** 以 X-Caller-Service 身份调用内部接口。 */
    protected PlatformResponseChecker call(HttpMethod method, String path, String caller, Object body) {
        String id = "plt-file-" + UUID.randomUUID();
        HttpHeaders headers = TestRequestHeaders.internal(id);
        headers.set("X-Caller-Service", caller);
        return new PlatformResponseChecker(httpTestInvoker.exchange(method, path, headers, body), objectMapper, id);
    }
}
