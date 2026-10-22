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
import com.paytech.platform.support.filedocument.domain.file.DownloadPresentation;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Grant;
import com.paytech.platform.support.filedocument.integration.storage.CompletedUploadStorage.Opened;
import com.paytech.platform.support.filedocument.integration.storage.FileDownloadStorage;
import com.paytech.platform.support.filedocument.integration.storage.FileObjectStorage;
import com.paytech.platform.support.filedocument.integration.storage.FileStorageRegistry;
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

    /** 当前对象的全部非删除标记版本。 */
    protected List<String> versions(Spec spec) {
        return storage.versions(spec.objectRef());
    }
}
