package com.paytech.platform.support.test.base;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.api.enums.FileDisposition;
import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.api.enums.FileStorageCheckResult;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.api.enums.FileStorageType;
import com.paytech.platform.support.filedocument.domain.file.DownloadPresentation;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Grant;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.integration.storage.CompletedUploadStorage.Opened;
import com.paytech.platform.support.filedocument.integration.storage.FileDownloadStorage;
import com.paytech.platform.support.filedocument.integration.storage.FileObjectStorage;
import com.paytech.platform.support.filedocument.integration.storage.FileStorageRegistry;
import com.paytech.platform.support.filedocument.integration.storage.LatestVersion;
import com.paytech.platform.support.test.builder.FileBuilder;
import com.paytech.platform.support.test.builder.FileBuilder.Spec;

/**
 * 存储适配器入口测试的公共步骤：经存储注册表按实例代码构建本次适配器，调用其发布的上传、完成读取、下载与检查读取端口，
 * 浏览器请求真实发往本机 MinIO。
 *
 * <p>不含 {@code @Test}；MinIO 与 S3 测试类分别声明场景，断言共用本类的辅助方法。
 */
public abstract class FileStorageAdapterTestBase extends FileTestBase {

    /** 预签名的最长有效期。 */
    protected static final Duration MAX_PRESIGN = Duration.ofDays(7);

    @Autowired
    protected FileStorageRegistry registry;

    private FileObjectStorage adapter;

    /** 被测实例代码。 */
    protected abstract String provider();

    /** 本例使用的适配器：首次使用时按实例与测试存储桶构建，本例结束关闭。 */
    protected FileObjectStorage adapter() {
        if (adapter == null) {
            adapter = registry.openForWrite(provider(), storage.bucket());
        }
        return adapter;
    }

    /** 关闭本例构建的适配器。 */
    @AfterEach
    void closeAdapter() {
        if (adapter != null) {
            adapter.close();
            adapter = null;
        }
    }

    /** 本例对象上的上传目标，默认 15 分钟后到期。 */
    protected FileBuilder target() {
        return material(provider()).expiresAt(now().plusSeconds(900));
    }

    /** 签发上传授权并以浏览器身份原样 PUT，要求存储接受。 */
    protected Grant uploadThroughGrant(Spec spec) {
        Grant grant = adapter().authorize(spec.upload());
        assertEquals(200, storage.browserPut(grant.url(), grant.headers(), spec.bytes()).statusCode(),
            "预签名 PUT 应被接受");
        return grant;
    }

    /** 授权为 HTTPS、签入类型与仅不存在时写入条件，且到期不晚于上传期限。 */
    protected void assertUploadGrant(Grant grant, Spec spec) {
        Map<String, String> headers = grant.headers().entrySet().stream()
            .collect(Collectors.toMap(entry -> entry.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue));
        assertAll(() -> assertEquals("https", URI.create(grant.url()).getScheme()),
            () -> assertEquals(Map.of("content-type", spec.mime(), "if-none-match", "*"), headers),
            () -> assertFalse(grant.expiresAt().isAfter(spec.expiresAt()), "授权不得晚于上传期限"),
            () -> assertTrue(grant.expiresAt().isAfter(now()), "授权应尚未过期"));
    }

    /** 打开固定版本并检查类型、加密引用与精确字节。 */
    protected String assertOpened(Spec spec, String expectedMime) {
        try (Opened opened = adapter().open(spec.upload())) {
            assertAll(() -> assertNotNull(opened.version()), () -> assertFalse(opened.version().isBlank()),
                () -> assertEquals(expectedMime, opened.detectedMime()),
                () -> assertEquals(FileBuilder.SSE_S3, opened.encryptionKeyRef()));
            assertArrayEquals(spec.bytes(), opened.bytes().readAllBytes(), "应读取完整原字节");
            return opened.version();
        } catch (IOException failure) {
            throw new AssertionError("读取固定版本失败", failure);
        }
    }

    /** 完成读取不可用：多版本、缺失对象或删除标记都不能固定版本。 */
    protected void assertOpenUnavailable(Spec spec) {
        assertThrows(ServiceUnavailableException.class, () -> {
            try (Opened ignored = adapter().open(spec.upload())) {
                fail("不应打开对象");
            }
        });
    }

    /** 读取已登记版本的全部字节。 */
    protected byte[] readRegistered(Spec spec) {
        try (InputStream bytes = adapter().open(spec.file())) {
            return bytes.readAllBytes();
        } catch (IOException failure) {
            throw new AssertionError("读取已登记版本失败", failure);
        }
    }

    /** 签发下载授权并检查期限、协议与附件下载后返回字节。 */
    protected byte[] downloadThroughGrant(Spec spec, Instant requested) {
        FileDownloadStorage.Download grant = adapter().authorize(spec.file(), requested, attachment(spec));
        assertAll(() -> assertEquals("https", URI.create(grant.url()).getScheme()),
            () -> assertFalse(grant.expiresAt().isAfter(requested), "下载授权不得晚于请求期限"),
            () -> assertTrue(grant.expiresAt().isAfter(now()), "下载授权应尚未过期"));
        var response = storage.browserGet(grant.url());
        assertEquals(200, response.statusCode(), "预签名 GET 应成功");
        assertTrue(response.headers().firstValue("Content-Disposition").orElse("").startsWith("attachment"),
            "应以附件方式返回");
        return response.body();
    }

    /** 按附件方式下载规格中的文件。 */
    protected static DownloadPresentation attachment(Spec spec) {
        return DownloadPresentation.of(FileDisposition.ATTACHMENT, spec.displayName(), spec.fileId(), spec.mime(),
            spec.scanStatus());
    }

    /**
     * 下载方式与原文件名：预签名地址的 Query 参数与浏览器实际收到的响应头一致
     * <li>中文名 INLINE 的 PDF：内联并签入登记的 Content-Type；英文名含引号的附件：ASCII 回退把引号替换为 _；</li>
     * <li>登记 MIME 不在内联名单内时 INLINE 降为附件，地址不带 response-content-type。</li>
     */
    protected void assertDownloadPresentation() {
        Spec uploaded = target().build();
        uploadThroughGrant(uploaded);
        String version = assertOpened(uploaded, uploaded.mime());
        Instant requested = now().plusSeconds(60);

        Spec chinese = registered(uploaded, version, "营业执照 2026.pdf", uploaded.mime());
        assertPresented(chinese, FileDisposition.INLINE, requested, FileDisposition.INLINE,
            "inline; filename=\"____ 2026.pdf\"; filename*=UTF-8''%E8%90%A5%E4%B8%9A%E6%89%A7%E7%85%A7%202026.pdf",
            "application/pdf");

        Spec english = registered(uploaded, version, "Certificate \"A\".pdf", uploaded.mime());
        assertPresented(english, FileDisposition.ATTACHMENT, requested, FileDisposition.ATTACHMENT,
            "attachment; filename=\"Certificate _A_.pdf\"; filename*=UTF-8''Certificate%20%22A%22.pdf", null);

        Spec archive = registered(uploaded, version, "bundle.zip", "application/zip");
        assertPresented(archive, FileDisposition.INLINE, requested, FileDisposition.ATTACHMENT,
            "attachment; filename=\"bundle.zip\"; filename*=UTF-8''bundle.zip", null);
    }

    /** 同一已登记版本，改用指定展示名与登记 MIME。 */
    private Spec registered(Spec uploaded, String version, String displayName, String mime) {
        return FileBuilder.template(provider(), uploaded.container(), uploaded.objectRef()).displayName(displayName)
            .mime(mime).version(version).build();
    }

    /** 签发并断言生效方式、地址 Query 参数与实际响应头。 */
    private void assertPresented(Spec spec, FileDisposition requested, Instant deadline, FileDisposition expected,
        String contentDisposition, String contentType) {
        DownloadPresentation presentation = DownloadPresentation.of(requested, spec.displayName(), spec.fileId(),
            spec.mime(), spec.scanStatus());
        FileDownloadStorage.Download grant = adapter().authorize(spec.file(), deadline, presentation);
        Map<String, String> query = query(grant.url());
        HttpResponse<byte[]> response = storage.browserGet(grant.url());
        assertAll(() -> assertEquals(expected, presentation.disposition()),
            () -> assertEquals(contentDisposition, query.get("response-content-disposition")),
            () -> assertEquals(contentType, query.get("response-content-type")),
            () -> assertEquals(200, response.statusCode(), "预签名 GET 应成功"),
            () -> assertEquals(contentDisposition, response.headers().firstValue("Content-Disposition").orElse(null)),
            () -> assertArrayEquals(FileBuilder.PDF, response.body()));
        if (contentType != null) {
            assertEquals(contentType, response.headers().firstValue("Content-Type").orElse(null));
        }
    }

    /** 解码预签名地址的 Query 参数。 */
    private static Map<String, String> query(String url) {
        return java.util.Arrays.stream(URI.create(url).getRawQuery().split("&")).map(pair -> pair.split("=", 2))
            .collect(Collectors.toMap(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                pair -> pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : ""));
    }

    /**
     * 替换上传：授权只签 Content-Type（版本控制保留旧版本）；浏览器 PUT 后比基准新的版本恰为一个，按该版本打开得到服务端识别类型、
     * 加密引用与原字节；之后再写入一次，两个新版本最新在前；基准版本不在对象版本中时不可用。
     */
    protected void assertReplacementVersions() {
        Spec original = target().build();
        uploadThroughGrant(original);
        String base = assertOpened(original, original.mime());
        Upload replacement = original.replacementUpload(1, base, FileBuilder.PNG, "image/png");

        Grant grant = adapter().authorize(replacement);
        assertEquals(Map.of("content-type", "image/png"), lowerCased(grant.headers()), "替换授权不带 If-None-Match");
        assertEquals(200, storage.browserPut(grant.url(), grant.headers(), FileBuilder.PNG).statusCode());
        List<String> newer = adapter().newerVersions(replacement);

        assertEquals(1, newer.size());
        try (Opened opened = adapter().open(replacement, newer.getFirst())) {
            assertAll(() -> assertEquals(newer.getFirst(), opened.version()),
                () -> assertEquals("image/png", opened.detectedMime()),
                () -> assertEquals(FileBuilder.SSE_S3, opened.encryptionKeyRef()));
            assertArrayEquals(FileBuilder.PNG, opened.bytes().readAllBytes());
        } catch (IOException failure) {
            throw new AssertionError("读取替换版本失败", failure);
        }
        String third = storage.put(original.objectRef(), FileBuilder.JPEG);
        assertEquals(List.of(third, newer.getFirst()), adapter().newerVersions(replacement), "最新在前");
        assertTrue(adapter().newerVersions(original.replacementUpload(2, third, FileBuilder.PNG, "image/png"))
            .isEmpty(), "基准即最新版本时没有新版本");
        Upload unknownBase = original.replacementUpload(3, "unknown-version", FileBuilder.PNG, "image/png");
        assertThrows(ServiceUnavailableException.class, () -> adapter().newerVersions(unknownBase));
    }

    /**
     * 公开桶（本机 MinIO 管理员密钥的实例与匿名只读临时桶）
     * <li>存储桶检查：公开级别经对外基础地址匿名读到探测对象为 OK；按私有级别检查为 PUBLIC_ACCESS_ALLOWED；对外基础地址读不到为
     * PUBLIC_READ_FAILED。</li>
     * <li>最新版本与恢复：授权外写入后最新版本是该写入且无源版本；把登记版本复制为最新版本后最新版本记录源版本并对外提供登记版本，
     * 匿名读取得到登记版本的字节与 Content-Type。</li>
     *
     * @param instanceCode 管理员实例代码
     * @param type 适配器类型
     */
    protected void assertPublicBucket(String instanceCode, FileStorageType type) {
        String bucket = tempBuckets.publicReadable();
        String baseUrl = tempBuckets.publicBaseUrl(bucket);
        storageInstances.rootInstance(instanceCode, type);
        storageInstances.publicBucket(instanceCode, bucket, baseUrl, FileStorageBucketStatus.ACTIVE);
        storageInstances.refresh();
        try (FileObjectStorage root = registry.openForWrite(instanceCode, bucket)) {
            assertAll(() -> assertEquals(FileStorageCheckResult.OK,
                root.checkBucket(bucket, FileAccessLevel.PUBLIC, baseUrl)),
                () -> assertEquals(FileStorageCheckResult.PUBLIC_ACCESS_ALLOWED,
                    root.checkBucket(bucket, FileAccessLevel.PRIVATE, null)),
                () -> assertEquals(FileStorageCheckResult.PUBLIC_READ_FAILED,
                    root.checkBucket(bucket, FileAccessLevel.PUBLIC, tempBuckets.publicBaseUrl(tempBuckets.missing()))));

            String objectRef = storage.newObjectRef();
            String registered = tempBuckets.put(bucket, objectRef, FileBuilder.PDF);
            String overwritten = tempBuckets.put(bucket, objectRef, FileBuilder.JPEG);
            Spec file = FileBuilder.template(instanceCode, bucket, objectRef).accessLevel(FileAccessLevel.PUBLIC)
                .version(registered).build();
            assertEquals(new LatestVersion(overwritten, null), root.latest(bucket, objectRef));

            root.restore(file.file());

            LatestVersion restored = root.latest(bucket, objectRef);
            HttpResponse<byte[]> anonymous = storage.browserGet(baseUrl + "/" + objectRef);
            assertAll(() -> assertEquals(registered, restored.restoredFrom()),
                () -> assertTrue(restored.serves(registered)), () -> assertFalse(restored.serves(overwritten)),
                () -> assertEquals(3, tempBuckets.versions(bucket, objectRef).size()),
                () -> assertEquals(200, anonymous.statusCode()), () -> assertArrayEquals(FileBuilder.PDF, anonymous.body()),
                () -> assertEquals("application/pdf", anonymous.headers().firstValue("Content-Type").orElse(null)));
        }
    }

    /** 头名按小写归一。 */
    private static Map<String, String> lowerCased(Map<String, String> headers) {
        return headers.entrySet().stream()
            .collect(Collectors.toMap(entry -> entry.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue));
    }

    /** 当前对象的全部非删除标记版本。 */
    protected List<String> versions(Spec spec) {
        return storage.versions(spec.objectRef());
    }
}
