package com.paytech.platform.support.auto.ext.http.files;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.api.files.InternalFileApi;
import com.paytech.platform.support.api.files.request.StartFileUploadRequestBody;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.core.enums.FileVersionStatus;
import com.paytech.platform.support.test.base.InternalFileApiTestBase;
import com.paytech.platform.support.test.builder.FileBuilder;
import com.paytech.platform.support.test.mocker.local.FileMocker;
import com.paytech.platform.support.test.mocker.local.StorageInstanceMocker;

/**
 * 公开文件上传：调用方声明访问级别，写入路由按 (来源系统, 访问级别) 选择，不跨级别回落；公开文件检查通过后返回长期公开地址，
 * 匿名读取得到原字节；私有文件与不声明访问级别的现有调用行为不变。
 * <li>公开桶为本机 MinIO 的临时桶（版本控制、SSE-S3、匿名只读），经管理员密钥的实例访问；对外基础地址为 MinIO 路径风格地址。</li>
 *
 * @see InternalFileApi#startUpload(StartFileUploadRequestBody)
 * @see InternalFileApi#completeUpload(String)
 * @see InternalFileApi#getFile(String)
 */
class InternalFilePublicApiTest extends InternalFileApiTestBase {

    private static final String SOURCE = StorageInstanceMocker.SOURCE;

    private static final String PEER = StorageInstanceMocker.PEER_SOURCE;

    private static final String PUBLIC = FileAccessLevel.PUBLIC.getCode();

    private static final String PRIVATE = FileAccessLevel.PRIVATE.getCode();

    private String publicBucket;

    private long policyId;

    /** 本类公共：测试扫描策略；公开临时桶与管理员实例；来源系统的公开路由指向公开桶。 */
    @BeforeEach
    void setUpClassMocks() {
        policyId = fileMocker.formatPolicy().policyId();
        publicBucket = tempBuckets.publicReadable();
        storageInstances.rootInstance();
        storageInstances.publicBucket(StorageInstanceMocker.ROOT_INSTANCE, publicBucket,
            tempBuckets.publicBaseUrl(publicBucket), FileStorageBucketStatus.ACTIVE);
        storageInstances.route(SOURCE, FileAccessLevel.PUBLIC, StorageInstanceMocker.ROOT_INSTANCE, publicBucket,
            FileMocker.POLICY_CODE);
        storageInstances.refresh();
    }

    /**
     * 同一来源分别声明公开、私有上传：分别落到公开桶与私有桶；公开文件检查通过后返回公开地址与带版本参数的地址，匿名读取得到原字节；
     * 私有文件公开地址为空。
     */
    @Test
    void test_normalFlow() {
        FileUploadResponseBody publicUpload = start(SOURCE, upload(PUBLIC, FileBuilder.PNG, "image/png"))
            .assertStatus(201).as(FileUploadResponseBody.class);
        FileUploadResponseBody privateUpload = start(SOURCE, upload(PRIVATE, FileBuilder.PDF, "application/pdf"))
            .assertStatus(201).as(FileUploadResponseBody.class);
        storage.track(objectRef(privateUpload.getUploadId()));
        put(publicUpload, FileBuilder.PNG);
        put(privateUpload, FileBuilder.PDF);

        var publicReceipt = complete(SOURCE, publicUpload.getUploadId()).assertOk()
            .as(FileUploadCompletionResponseBody.class);
        var privateReceipt = complete(SOURCE, privateUpload.getUploadId()).assertOk()
            .as(FileUploadCompletionResponseBody.class);
        long publicFile = Long.parseLong(publicReceipt.getFileId());
        long privateFile = Long.parseLong(privateReceipt.getFileId());
        awaitVersion(publicFile, 1, FileVersionStatus.CURRENT);
        awaitVersion(privateFile, 1, FileVersionStatus.CURRENT);

        StoredFileResponseBody published = describe(PEER, publicFile);
        StoredFileResponseBody hidden = describe(PEER, privateFile);
        Map<String, Object> publicRow = fileData.uploadRow(Long.parseLong(publicUpload.getUploadId()));
        String expectedUrl = tempBuckets.publicBaseUrl(publicBucket) + "/" + publicRow.get("storage_object_ref");
        assertAll(() -> assertEquals(StorageInstanceMocker.ROOT_INSTANCE, publicRow.get("storage_provider")),
            () -> assertEquals(publicBucket, publicRow.get("storage_container_ref")),
            () -> assertEquals(PUBLIC, publicRow.get("access_level")),
            () -> assertEquals(PRIVATE, fileData.uploadRow(Long.parseLong(privateUpload.getUploadId())).get(
                "access_level")),
            () -> assertEquals(storage.bucket(), objectBucket(privateUpload)),
            () -> assertEquals(PUBLIC, publicReceipt.getAccessLevel()),
            () -> assertEquals(1, publicReceipt.getVersionNo()),
            () -> assertEquals(PRIVATE, privateReceipt.getAccessLevel()),
            () -> assertEquals(PUBLIC, published.getAccessLevel()),
            () -> assertEquals(1, published.getVersionNo()),
            () -> assertNull(published.getPendingVersionNo()),
            () -> assertEquals(expectedUrl, published.getPublicUrl()),
            () -> assertEquals(expectedUrl + "?v=1", published.getVersionedUrl()),
            () -> assertEquals(PUBLIC, fileData.file(publicFile).get("access_level")),
            () -> assertEquals(PRIVATE, hidden.getAccessLevel()),
            () -> assertNull(hidden.getPublicUrl()),
            () -> assertNull(hidden.getVersionedUrl()));
        var anonymous = storage.browserGet(published.getPublicUrl());
        assertEquals(200, anonymous.statusCode(), "公开地址应可匿名读取");
        assertArrayEquals(FileBuilder.PNG, anonymous.body());
        assertArrayEquals(FileBuilder.PDF, downloadBytes(PEER, privateFile), "私有文件仍走授权下载");
    }

    /** 公开地址只在当前版本 AVAILABLE 时返回：检查中、被拒绝的公开文件为空；存量（回填 v1）的可用公开文件按对象名计算。 */
    @Test
    void test_publicUrlOnlyWhenAvailable() {
        var available = fileMocker.file(publicFile().version("v1").build());
        var pending = fileMocker.file(publicFile().scanStatus(FileScanStatus.PENDING_SCAN).version("v2").build());
        var rejected = fileMocker.file(publicFile().scanStatus(FileScanStatus.REJECTED).version("v3").build());

        StoredFileResponseBody served = describe(PEER, available.fileId());
        StoredFileResponseBody checking = describe(PEER, pending.fileId());
        StoredFileResponseBody refused = describe(PEER, rejected.fileId());

        assertAll(() -> assertEquals(tempBuckets.publicBaseUrl(publicBucket) + "/" + available.objectRef(),
                served.getPublicUrl()),
            () -> assertEquals(1, served.getVersionNo()),
            () -> assertEquals(PUBLIC, checking.getAccessLevel()), () -> assertNull(checking.getPublicUrl()),
            () -> assertNull(checking.getVersionedUrl()), () -> assertNull(refused.getPublicUrl()));
    }

    /** 声明公开但来源系统没有公开路由、默认公开路由也不存在：返回 503，不回落到私有默认路由，不登记上传。 */
    @Test
    void test_noPublicRoute() {
        start(PEER, upload(PUBLIC, FileBuilder.PDF, "application/pdf"))
            .assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE);

        assertTrue(storageData.uploadsFrom(PEER).isEmpty());
        var receipt = start(PEER, upload(null, FileBuilder.PDF, "application/pdf")).assertStatus(201)
            .as(FileUploadResponseBody.class);
        storage.track(objectRef(receipt.getUploadId()));
        List<Map<String, Object>> rows = storageData.uploadsFrom(PEER);
        assertAll(() -> assertEquals(1, rows.size()), () -> assertEquals(PRIVATE, rows.getFirst().get("access_level")),
            () -> assertEquals(StorageInstanceMocker.MINIO_INSTANCE, rows.getFirst().get("storage_provider")));
    }

    /** 默认公开路由 (*, PUBLIC) 存在时，没有精确公开路由的来源使用它；默认私有路由仍只服务私有上传。 */
    @Test
    void test_defaultPublicRoute() {
        storageInstances.route(StorageInstanceMocker.DEFAULT_ROUTE, FileAccessLevel.PUBLIC,
            StorageInstanceMocker.ROOT_INSTANCE, publicBucket, FileMocker.POLICY_CODE);
        storageInstances.refresh();

        start(PEER, upload(PUBLIC, FileBuilder.PDF, "application/pdf")).assertStatus(201);

        Map<String, Object> row = storageData.uploadsFrom(PEER).getFirst();
        assertAll(() -> assertEquals(publicBucket, row.get("storage_container_ref")),
            () -> assertEquals(PUBLIC, row.get("access_level")));
    }

    /** 路由数据被改成指向不同访问级别的桶（数据不一致）时，写入目标不可用，不落到错误级别的桶。 */
    @Test
    void test_mismatchedBucketUnavailable() {
        storageData.deleteRoute(SOURCE);
        storageInstances.route(SOURCE, FileAccessLevel.PUBLIC, StorageInstanceMocker.MINIO_INSTANCE, storage.bucket(),
            FileMocker.POLICY_CODE);
        storageInstances.refresh();

        start(SOURCE, upload(PUBLIC, FileBuilder.PDF, "application/pdf"))
            .assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE);

        assertTrue(storageData.uploadsFrom(SOURCE).isEmpty());
    }

    /** 访问级别只接受 PRIVATE、PUBLIC。 */
    @Test
    void test_invalidParameter() {
        for (String invalid : List.of("public", "INTERNAL", " PUBLIC")) {
            start(SOURCE, upload(invalid, FileBuilder.PDF, "application/pdf"))
                .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("accessLevel");
        }
        assertTrue(storageData.uploadsFrom(SOURCE).isEmpty());
    }

    /** 公开桶中、本类策略下的公开文件规格。 */
    private FileBuilder publicFile() {
        return FileBuilder.template(StorageInstanceMocker.ROOT_INSTANCE, publicBucket, storage.newObjectRef())
            .policyId(policyId).accessLevel(FileAccessLevel.PUBLIC);
    }

    /** 上传记录登记的存储桶。 */
    private String objectBucket(FileUploadResponseBody upload) {
        return (String) fileData.uploadRow(Long.parseLong(upload.getUploadId())).get("storage_container_ref");
    }
}
