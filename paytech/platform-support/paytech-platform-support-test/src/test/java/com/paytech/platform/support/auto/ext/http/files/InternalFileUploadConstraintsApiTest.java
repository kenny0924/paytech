package com.paytech.platform.support.auto.ext.http.files;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.api.files.InternalFileApi;
import com.paytech.platform.support.api.files.request.StartFileUploadRequestBody;
import com.paytech.platform.support.api.files.response.FileUploadConstraintsResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.test.base.InternalFileApiTestBase;
import com.paytech.platform.support.test.builder.FileBuilder;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.FileMocker;
import com.paytech.platform.support.test.mocker.local.StorageInstanceMocker;

/**
 * 写入限制：按 (来源系统, 访问级别) → (*, 访问级别) 解析写入路由，再取扫描策略唯一的有效修订；与开始上传同一份解析，提示通过则开始上传
 * 通过，提示未配置则开始上传返回 503。
 * <li>标准环境：默认私有路由 (*, PRIVATE) 指向 MINIO 实例的测试桶，冻结测试策略代码；策略修订由各用例按需插入。</li>
 *
 * @see InternalFileApi#getUploadConstraints(String)
 * @see InternalFileApi#startUpload(StartFileUploadRequestBody)
 */
class InternalFileUploadConstraintsApiTest extends InternalFileApiTestBase {

    private static final String SOURCE = StorageInstanceMocker.SOURCE;

    private static final String PEER = StorageInstanceMocker.PEER_SOURCE;

    private static final String PUBLIC = FileAccessLevel.PUBLIC.getCode();

    private static final String PRIVATE = FileAccessLevel.PRIVATE.getCode();

    /** 测试策略登记的允许类型（与 FileMocker 的规则同序）。 */
    private static final List<String> ALLOWED =
        List.of("application/pdf", "image/png", "image/jpeg", "text/csv", "application/json");

    /** 测试策略的大小上限。 */
    private static final long MAX_SIZE = 10_485_760L;

    /** 来源系统有精确公开路由：返回该路由的公开桶、对外基础地址、策略修订、允许类型、大小上限与上传期限。 */
    @Test
    void test_exactRoute() {
        fileMocker.formatPolicy();
        String publicBucket = tempBuckets.publicReadable();
        storageInstances.rootInstance();
        storageInstances.publicBucket(StorageInstanceMocker.ROOT_INSTANCE, publicBucket,
            tempBuckets.publicBaseUrl(publicBucket), FileStorageBucketStatus.ACTIVE);
        storageInstances.route(SOURCE, FileAccessLevel.PUBLIC, StorageInstanceMocker.ROOT_INSTANCE, publicBucket,
            FileMocker.POLICY_CODE);
        storageInstances.refresh();

        FileUploadConstraintsResponseBody constraints = constraints(SOURCE, PUBLIC);

        assertAll(() -> assertTrue(constraints.isConfigured()), () -> assertEquals(PUBLIC, constraints.getAccessLevel()),
            () -> assertEquals(SOURCE, constraints.getRouteSourceSystem()),
            () -> assertEquals(StorageInstanceMocker.ROOT_INSTANCE, constraints.getInstanceCode()),
            () -> assertEquals(publicBucket, constraints.getBucketName()),
            () -> assertEquals(tempBuckets.publicBaseUrl(publicBucket), constraints.getPublicBaseUrl()),
            () -> assertEquals(FileMocker.POLICY_CODE, constraints.getScanPolicyCode()),
            () -> assertEquals(1, constraints.getScanPolicyRevisionNo()),
            () -> assertEquals(ALLOWED, constraints.getAllowedMimeTypes()),
            () -> assertEquals(Long.toString(MAX_SIZE), constraints.getMaxSizeBytes()),
            () -> assertEquals(StorageInstanceMocker.UPLOAD_SECONDS, constraints.getMaxUploadSeconds()));
        assertTrue(storageData.uploadsFrom(SOURCE).isEmpty(), "查询限制不登记上传");
    }

    /** 没有精确路由时使用同级别默认路由 (*, PRIVATE)；不声明访问级别按 PRIVATE；私有桶没有对外基础地址。 */
    @Test
    void test_defaultRoute() {
        fileMocker.formatPolicy();

        FileUploadConstraintsResponseBody constraints = constraints(PEER, null);

        assertAll(() -> assertTrue(constraints.isConfigured()), () -> assertEquals(PRIVATE, constraints.getAccessLevel()),
            () -> assertEquals(StorageInstanceMocker.DEFAULT_ROUTE, constraints.getRouteSourceSystem()),
            () -> assertEquals(StorageInstanceMocker.MINIO_INSTANCE, constraints.getInstanceCode()),
            () -> assertEquals(storage.bucket(), constraints.getBucketName()),
            () -> assertNull(constraints.getPublicBaseUrl()),
            () -> assertEquals(FileMocker.POLICY_CODE, constraints.getScanPolicyCode()));
    }

    /** 没有 (来源, PUBLIC) 也没有 (*, PUBLIC) 路由：200 且未配置，只回显访问级别；开始上传同样 503，不回落到私有路由。 */
    @Test
    void test_noRouteUnconfigured() {
        fileMocker.formatPolicy();

        assertUnconfigured(constraints(PEER, PUBLIC), PUBLIC);
        start(PEER, upload(PUBLIC, FileBuilder.PDF, "application/pdf"))
            .assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    /** 默认路由的桶停用：写入目标不可用，未配置；开始上传 503。 */
    @Test
    void test_bucketDisabledUnconfigured() {
        fileMocker.formatPolicy();
        storageInstances.bucketStatus(StorageInstanceMocker.MINIO_INSTANCE, FileStorageBucketStatus.DISABLED);

        assertUnconfigured(constraints(PEER, PRIVATE), PRIVATE);
        start(PEER, upload(PRIVATE, FileBuilder.PDF, "application/pdf"))
            .assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    /** 路由冻结的策略代码没有有效修订：未配置；开始上传 503，不登记上传。 */
    @Test
    void test_noActiveRevisionUnconfigured() {
        assertUnconfigured(constraints(PEER, PRIVATE), PRIVATE);
        start(PEER, upload(PRIVATE, FileBuilder.PDF, "application/pdf"))
            .assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE);

        assertTrue(storageData.uploadsFrom(PEER).isEmpty());
    }

    /** 同一份数据下限制与开始上传的实际校验一致：允许类型且不超上限通过；不在允许类型或超上限返回 422。 */
    @Test
    void test_matchesUploadAdmission() {
        fileMocker.formatPolicy();
        FileUploadConstraintsResponseBody constraints = constraints(PEER, PRIVATE);
        long maxSize = Long.parseLong(constraints.getMaxSizeBytes());

        for (String mime : constraints.getAllowedMimeTypes()) {
            var receipt = start(PEER, new StartFileUploadRequestBody(REASON, "material.bin", mime, Long.toString(maxSize),
                null, PRIVATE)).assertStatus(201).as(FileUploadResponseBody.class);
            storage.track(objectRef(receipt.getUploadId()));
        }
        assertFalse(constraints.getAllowedMimeTypes().contains("text/plain"));
        start(PEER, new StartFileUploadRequestBody(REASON, "note.txt", "text/plain", "10", null, PRIVATE))
            .assertProblem(422, FileErrorCode.FILE_POLICY_REJECTED);
        start(PEER, new StartFileUploadRequestBody(REASON, "large.pdf", "application/pdf", Long.toString(maxSize + 1),
            null, PRIVATE)).assertProblem(422, FileErrorCode.FILE_POLICY_REJECTED);

        assertEquals(constraints.getAllowedMimeTypes().size(), storageData.uploadsFrom(PEER).size());
    }

    /** 访问级别只接受 PRIVATE、PUBLIC。 */
    @Test
    void test_invalidParameter() {
        for (String invalid : List.of("public", "INTERNAL", "PRIVATE,PUBLIC")) {
            query(PEER, "?accessLevel=" + invalid).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        }
    }

    /** 查询写入限制并解析响应。 */
    private FileUploadConstraintsResponseBody constraints(String caller, String accessLevel) {
        return query(caller, accessLevel == null ? "" : "?accessLevel=" + accessLevel).assertOk()
            .as(FileUploadConstraintsResponseBody.class);
    }

    /** 以调用方身份查询写入限制。 */
    private PlatformResponseChecker query(String caller, String query) {
        return call(HttpMethod.GET, BASE + "/upload-constraints" + query, caller, null);
    }

    /** 未配置：只有访问级别，其余字段为空。 */
    private static void assertUnconfigured(FileUploadConstraintsResponseBody constraints, String accessLevel) {
        assertAll(() -> assertFalse(constraints.isConfigured()), () -> assertEquals(accessLevel, constraints.getAccessLevel()),
            () -> assertNull(constraints.getRouteSourceSystem()), () -> assertNull(constraints.getInstanceCode()),
            () -> assertNull(constraints.getBucketName()), () -> assertNull(constraints.getPublicBaseUrl()),
            () -> assertNull(constraints.getScanPolicyCode()), () -> assertNull(constraints.getScanPolicyRevisionNo()),
            () -> assertNull(constraints.getAllowedMimeTypes()), () -> assertNull(constraints.getMaxSizeBytes()),
            () -> assertNull(constraints.getMaxUploadSeconds()));
    }
}
