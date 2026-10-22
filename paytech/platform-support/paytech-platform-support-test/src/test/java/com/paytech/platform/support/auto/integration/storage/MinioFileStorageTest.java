package com.paytech.platform.support.auto.integration.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.filedocument.domain.file.FileModels.File;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.integration.storage.*;
import com.paytech.platform.support.filedocument.integration.storage.minio.MinioFileStorage;
import com.paytech.platform.support.api.enums.FileStorageCheckResult;
import com.paytech.platform.support.filedocument.integration.storage.ProbeObject;
import com.paytech.platform.support.test.base.FileStorageAdapterTestBase;
import com.paytech.platform.support.test.mocker.local.StorageInstanceMocker;
import com.paytech.platform.support.test.builder.FileBuilder;
import com.paytech.platform.support.test.builder.FileBuilder.Spec;

/**
 * MinIO 适配器经模块发布端口访问本机 HTTPS MinIO：上传授权与条件写入、唯一版本固定、服务端类型识别、版本下载和期限边界。
 *
 * @see FileStorageRegistry#openForWrite(String, String)
 * @see UploadStorage#authorize(Upload)
 * @see CompletedUploadStorage#open(Upload)
 * @see FileDownloadStorage#authorize(File, Instant, com.paytech.platform.support.filedocument.domain.file.DownloadPresentation)
 * @see StoredFileReader#open(File)
 * @see FileObjectStorage#checkBucket(String)
 */
class MinioFileStorageTest extends FileStorageAdapterTestBase {

    @Override
    protected String provider() {
        return StorageInstanceMocker.MINIO_INSTANCE;
    }

    /** 注册表按实例类型构建 MinIO SDK 适配器，实例代码即提供方代码。 */
    @Test
    void test_providerSelected() {
        assertAll(() -> assertEquals(provider(), adapter().providerCode()),
            () -> assertInstanceOf(MinioFileStorage.class, adapter()));
    }

    /** 存储桶检查：测试存储桶全部满足；不存在的桶为 NOT_FOUND，Platform 账号无权访问的桶为 ACCESS_DENIED。 */
    @Test
    void test_checkBucket() {
        assertAll(() -> assertEquals(FileStorageCheckResult.OK, adapter().checkBucket(storage.bucket())),
            () -> assertEquals(FileStorageCheckResult.ACCESS_DENIED, adapter().checkBucket(tempBuckets.unversioned())));
        assertEquals(List.of(), storage.objectsUnder(ProbeObject.PREFIX), "探测对象应已删除");
    }

    /** 浏览器按授权直传后，完成读取固定唯一版本；重复打开得到同一版本与原字节。 */
    @Test
    void test_normalFlow() {
        Spec spec = target().build();

        var grant = uploadThroughGrant(spec);

        assertUploadGrant(grant, spec);
        assertEquals(spec.expiresAt(), grant.expiresAt(), "MinIO 授权按秒取整后恰为上传期限");
        String version = assertOpened(spec, spec.mime());
        assertEquals(List.of(version), versions(spec));
        assertEquals(version, assertOpened(spec, spec.mime()), "重复打开必须固定同一版本");
    }

    /** 对象已存在时第二次条件写入被拒绝，版本与内容保持不变。 */
    @Test
    void test_conditionalPutRejectsSecondWrite() {
        Spec spec = target().build();
        var grant = uploadThroughGrant(spec);
        String version = assertOpened(spec, spec.mime());

        var second = storage.browserPut(grant.url(), grant.headers(), FileBuilder.PNG);

        assertEquals(412, second.statusCode(), "If-None-Match:* 应拒绝覆盖");
        assertEquals(List.of(version), versions(spec));
        assertEquals(version, assertOpened(spec, spec.mime()));
    }

    /** 绕过条件的第二个版本出现后，完成读取不再固定任何版本。 */
    @Test
    void test_overwrittenObjectUnavailable() {
        Spec spec = target().build();
        uploadThroughGrant(spec);
        storage.put(spec.objectRef(), FileBuilder.PNG);

        assertEquals(2, versions(spec).size());
        assertOpenUnavailable(spec);
    }

    /** 浏览器尚未上传时没有可固定的版本。 */
    @Test
    void test_noDataFound() {
        assertOpenUnavailable(target().build());
    }

    /** 媒体类型只按文件头识别，不采信上传声明。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("contentTypes")
    void test_detectContentType(String name, byte[] bytes, String expected) {
        Spec spec = target().bytes(bytes).build();
        storage.put(spec.objectRef(), bytes);

        assertOpened(spec, expected);
    }

    /** 下载与检查读取固定原版本：对象之后被改写，仍返回登记版本的字节。 */
    @Test
    void test_downloadPinnedVersion() {
        Spec uploaded = target().build();
        uploadThroughGrant(uploaded);
        Spec registered = FileBuilder.template(provider(), uploaded.container(), uploaded.objectRef())
            .version(assertOpened(uploaded, uploaded.mime())).build();
        storage.put(uploaded.objectRef(), FileBuilder.PNG);
        Instant requested = now().plusSeconds(60);

        byte[] downloaded = downloadThroughGrant(registered, requested);

        assertArrayEquals(FileBuilder.PDF, downloaded);
        assertArrayEquals(FileBuilder.PDF, readRegistered(registered));
        Instant granted = adapter().authorize(registered.file(), requested, attachment(registered)).expiresAt();
        assertEquals(requested, granted);
        assertEquals(granted.truncatedTo(ChronoUnit.MICROS), granted, "下载授权到期时间为微秒精度");
    }

    /** 下载方式与中英文原文件名签入地址并由存储原样返回；INLINE 不满足条件时降为附件。 */
    @Test
    void test_downloadPresentation() {
        assertDownloadPresentation();
    }

    /** 不足 1 秒的期限拒绝签发；超过 7 天的期限截断为 7 天。 */
    @Test
    void test_edgeCase() {
        Spec expiring = target().expiresAt(now().plusMillis(999)).build();
        Spec distant = target().expiresAt(now().plus(java.time.Duration.ofDays(30))).build();

        assertThrows(ServiceUnavailableException.class, () -> adapter().authorize(expiring.upload()));
        assertThrows(ServiceUnavailableException.class,
            () -> adapter().authorize(distant.file(), now().plusMillis(999), attachment(distant)));
        assertEquals(now().plus(MAX_PRESIGN), adapter().authorize(distant.upload()).expiresAt());
        assertEquals(now().plus(MAX_PRESIGN), adapter().authorize(distant.file(), distant.expiresAt(), attachment(distant)).expiresAt());
    }

    /** PDF、PNG、JPEG、UTF-8 文本（CSV）与无法识别内容。 */
    static Stream<Arguments> contentTypes() {
        return Stream.of(Arguments.of("pdf", FileBuilder.PDF, "application/pdf"),
            Arguments.of("png", FileBuilder.PNG, "image/png"),
            Arguments.of("jpeg", FileBuilder.JPEG, "image/jpeg"),
            Arguments.of("csv", FileBuilder.CSV, StoredContentType.CSV),
            Arguments.of("text", FileBuilder.TEXT, StoredContentType.CSV),
            Arguments.of("unknown", FileBuilder.BINARY, StoredContentType.UNKNOWN));
    }
}
