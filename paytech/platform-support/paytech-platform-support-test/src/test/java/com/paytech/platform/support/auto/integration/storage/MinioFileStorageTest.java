package com.paytech.platform.support.auto.integration.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
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
import com.paytech.platform.support.test.base.FileStorageAdapterTestBase;
import com.paytech.platform.support.test.builder.OnboardingFileBuilder;
import com.paytech.platform.support.test.builder.OnboardingFileBuilder.Spec;

/**
 * MinIO 适配器经模块发布端口访问本机 HTTPS MinIO：上传授权与条件写入、唯一版本固定、服务端类型识别、版本下载和期限边界。
 *
 * @see UploadStorage#authorize(Upload)
 * @see CompletedUploadStorage#open(Upload)
 * @see FileDownloadStorage#authorize(File, Instant)
 * @see StoredFileReader#open(File)
 */
class MinioFileStorageTest extends FileStorageAdapterTestBase {

    @Override
    protected String provider() {
        return MinioFileStorage.PROVIDER;
    }

    /** 当前上下文的四个端口均由 MinIO 适配器提供。 */
    @Test
    void test_providerSelected() {
        assertAll(() -> assertEquals(MinioFileStorage.PROVIDER, uploads.providerCode()),
            () -> assertEquals(MinioFileStorage.PROVIDER, completed.providerCode()),
            () -> assertEquals(MinioFileStorage.PROVIDER, downloads.providerCode()),
            () -> assertEquals(MinioFileStorage.PROVIDER, reader.providerCode()));
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

        var second = storage.browserPut(grant.url(), grant.headers(), OnboardingFileBuilder.PNG);

        assertEquals(412, second.statusCode(), "If-None-Match:* 应拒绝覆盖");
        assertEquals(List.of(version), versions(spec));
        assertEquals(version, assertOpened(spec, spec.mime()));
    }

    /** 绕过条件的第二个版本出现后，完成读取不再固定任何版本。 */
    @Test
    void test_overwrittenObjectUnavailable() {
        Spec spec = target().build();
        uploadThroughGrant(spec);
        storage.put(spec.objectRef(), OnboardingFileBuilder.PNG);

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
        Spec registered = OnboardingFileBuilder.template(provider(), uploaded.container(), uploaded.objectRef())
            .version(assertOpened(uploaded, uploaded.mime())).build();
        storage.put(uploaded.objectRef(), OnboardingFileBuilder.PNG);
        Instant requested = now().plusSeconds(60);

        byte[] downloaded = downloadThroughGrant(registered, requested);

        assertArrayEquals(OnboardingFileBuilder.PDF, downloaded);
        assertArrayEquals(OnboardingFileBuilder.PDF, readRegistered(registered));
        assertEquals(requested, downloads.authorize(registered.file(), requested).expiresAt());
    }

    /** 不足 1 秒的期限拒绝签发；超过 7 天的期限截断为 7 天。 */
    @Test
    void test_edgeCase() {
        Spec expiring = target().expiresAt(now().plusMillis(999)).build();
        Spec distant = target().expiresAt(now().plus(java.time.Duration.ofDays(30))).build();

        assertThrows(ServiceUnavailableException.class, () -> uploads.authorize(expiring.upload()));
        assertThrows(ServiceUnavailableException.class,
            () -> downloads.authorize(distant.file(), now().plusMillis(999)));
        assertEquals(now().plus(MAX_PRESIGN), uploads.authorize(distant.upload()).expiresAt());
        assertEquals(now().plus(MAX_PRESIGN), downloads.authorize(distant.file(), distant.expiresAt()).expiresAt());
    }

    /** PDF、PNG、JPEG 与无法识别内容。 */
    static Stream<Arguments> contentTypes() {
        return Stream.of(Arguments.of("pdf", OnboardingFileBuilder.PDF, "application/pdf"),
            Arguments.of("png", OnboardingFileBuilder.PNG, "image/png"),
            Arguments.of("jpeg", OnboardingFileBuilder.JPEG, "image/jpeg"),
            Arguments.of("unknown", OnboardingFileBuilder.TEXT, StoredContentType.UNKNOWN));
    }
}
