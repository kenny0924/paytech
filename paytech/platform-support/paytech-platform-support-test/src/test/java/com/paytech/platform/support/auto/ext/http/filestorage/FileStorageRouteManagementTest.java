package com.paytech.platform.support.auto.ext.http.filestorage;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.api.enums.FileStorageInstanceStatus;
import com.paytech.platform.support.api.enums.FileStorageType;
import com.paytech.platform.support.api.filestorage.FileStorageManagementApi;
import com.paytech.platform.support.api.filestorage.request.CreateStorageRouteRequestBody;
import com.paytech.platform.support.api.filestorage.request.DeleteStorageRouteRequestBody;
import com.paytech.platform.support.api.filestorage.request.UpdateStorageRouteRequestBody;
import com.paytech.platform.support.api.filestorage.response.StorageInstanceListResponseBody;
import com.paytech.platform.support.api.filestorage.response.StorageRouteListResponseBody;
import com.paytech.platform.support.api.filestorage.response.StorageRouteResponseBody;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.domain.storage.StorageWriteTarget;
import com.paytech.platform.support.filedocument.integration.storage.FileStorageRegistry;
import com.paytech.platform.support.test.base.FileStorageManagementTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.mocker.local.OnboardingFileMocker;
import com.paytech.platform.support.test.mocker.local.StorageInstanceMocker;

/**
 * 写入路由维护：目标资格（ACTIVE 桶与实例、有效扫描策略）、默认路由不可删、修改后新上传按新路由选择、引用保护与版本冲突。
 *
 * @see FileStorageManagementApi#listRoutes(String, String)
 * @see FileStorageManagementApi#createRoute(String, String, CreateStorageRouteRequestBody)
 * @see FileStorageManagementApi#updateRoute(String, String, String, UpdateStorageRouteRequestBody)
 * @see FileStorageManagementApi#deleteRoute(String, String, String, DeleteStorageRouteRequestBody)
 */
class FileStorageRouteManagementTest extends FileStorageManagementTestBase {

    private static final String MINIO = StorageInstanceMocker.MINIO_INSTANCE;

    private static final String S3 = StorageInstanceMocker.S3_INSTANCE;

    private static final String SOURCE = StorageInstanceMocker.SOURCE;

    @Autowired
    private FileStorageRegistry registry;

    @Autowired
    private Clock clock;

    private Operator admin;

    /** 本类公共：两个 ACTIVE 实例各有 ACTIVE 测试存储桶，测试扫描策略，平台管理员。 */
    @BeforeEach
    void setUpClassMocks() {
        fileMocker.formatPolicy();
        storageInstances.staticInstance(MINIO, FileStorageType.MINIO, StorageInstanceMocker.MINIO_TOKEN, false);
        storageInstances.staticInstance(S3, FileStorageType.S3, StorageInstanceMocker.S3_TOKEN, true);
        storageInstances.bucket(MINIO, storage.bucket(), FileStorageBucketStatus.ACTIVE);
        storageInstances.bucket(S3, storage.bucket(), FileStorageBucketStatus.ACTIVE);
        admin = operationsMocker.operator(OperatorBuilder.administrator());
    }

    /** 默认路由与来源路由：新建后本进程立即按来源选择桶，其他来源走默认路由；列表含目标实例与桶。 */
    @Test
    void test_normalFlow() {
        assertMutation(post(BASE + "/routes", admin, create(StorageInstanceMocker.DEFAULT_ROUTE, MINIO)),
            StorageInstanceMocker.DEFAULT_ROUTE, 1);
        assertMutation(post(BASE + "/routes", admin, create(SOURCE, S3)), SOURCE, 1);
        var event = auditChecker.assertEvent(requestId, "OPERATIONS_FILE_STORAGE_ROUTE_CREATE",
            AuditModels.Result.SUCCEEDED.getCode(), admin.actor(), FileStorageChange.ROUTE, SOURCE);

        assertTarget(SOURCE, S3);
        assertTarget(StorageInstanceMocker.PEER_SOURCE, MINIO);
        var routes = get(BASE + "/routes", admin).assertOk().as(StorageRouteListResponseBody.class).getItems();
        assertAll(() -> assertEquals(List.of(StorageInstanceMocker.DEFAULT_ROUTE, SOURCE),
            routes.stream().map(StorageRouteResponseBody::getSourceSystem).toList()),
            () -> assertEquals(S3, routes.getLast().getInstanceCode()),
            () -> assertEquals(storage.bucket(), routes.getLast().getBucketName()),
            () -> assertEquals(OnboardingFileMocker.POLICY_CODE, routes.getLast().getScanPolicyCode()),
            () -> assertEquals(S3, attributes(event).get("after.STORAGE_INSTANCE")));
        var instances = get(BASE + "/instances", admin).assertOk().as(StorageInstanceListResponseBody.class).getItems();
        assertEquals(List.of(1, 1), instances.stream().map(i -> i.getRouteCount()).toList());
    }

    /** 修改路由只影响之后的选择；删除来源路由后回到默认路由；默认路由不能删除；旧版本 409。 */
    @Test
    void test_updateAndDelete() {
        storageInstances.route(StorageInstanceMocker.DEFAULT_ROUTE, MINIO, OnboardingFileMocker.POLICY_CODE);
        storageInstances.route(SOURCE, MINIO, OnboardingFileMocker.POLICY_CODE);

        assertMutation(put(BASE + "/routes/" + SOURCE, admin, update(S3, 600, 1)), SOURCE, 2);
        assertTarget(SOURCE, S3);
        assertEquals(600, intValue(storageData.route(SOURCE).get("max_upload_seconds")));
        put(BASE + "/routes/" + SOURCE, admin, update(S3, 600, 1)).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        assertMutation(put(BASE + "/routes/" + SOURCE, admin, update(S3, 600, 2)), SOURCE, 2);

        assertMutation(delete(BASE + "/routes/" + SOURCE, admin, new DeleteStorageRouteRequestBody(2, REASON)), SOURCE,
            2);
        assertNull(storageData.route(SOURCE));
        assertTarget(SOURCE, MINIO);
        delete(BASE + "/routes/*", admin, new DeleteStorageRouteRequestBody(1, REASON))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        delete(BASE + "/routes/ut-missing", admin, new DeleteStorageRouteRequestBody(1, REASON))
            .assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    /** 目标桶或实例未启用、扫描策略无有效修订、来源格式与期限非法时拒绝，不写库。 */
    @Test
    void test_businessRuleViolation() {
        storageInstances.bucketStatus(S3, FileStorageBucketStatus.READ_ONLY);
        post(BASE + "/routes", admin, create(SOURCE, S3)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        storageInstances.instanceStatus(MINIO, FileStorageInstanceStatus.DISABLED);
        post(BASE + "/routes", admin, create(SOURCE, MINIO)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        storageInstances.instanceStatus(MINIO, FileStorageInstanceStatus.ACTIVE);
        post(BASE + "/routes", admin, create(SOURCE, "UT_MISSING"))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        post(BASE + "/routes", admin, new CreateStorageRouteRequestBody(SOURCE, MINIO, "ut-unregistered-bucket",
            OnboardingFileMocker.POLICY_CODE, 300, REASON)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        post(BASE + "/routes", admin, new CreateStorageRouteRequestBody(SOURCE, MINIO, storage.bucket(), "UT_NO_POLICY",
            300, REASON)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        post(BASE + "/routes", admin, new CreateStorageRouteRequestBody("File_Test", MINIO, storage.bucket(),
            OnboardingFileMocker.POLICY_CODE, 300, REASON)).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("sourceSystem");
        post(BASE + "/routes", admin, new CreateStorageRouteRequestBody(SOURCE, MINIO, storage.bucket(),
            OnboardingFileMocker.POLICY_CODE, 59, REASON)).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("maxUploadSeconds");

        assertNull(storageData.route(SOURCE));
        storageInstances.route(SOURCE, MINIO, OnboardingFileMocker.POLICY_CODE);
        post(BASE + "/routes", admin, create(SOURCE, MINIO)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
    }

    /** 有路由指向时，存储桶不能设为只读或停用，实例不能停用。 */
    @Test
    void test_referencedTargetsProtected() {
        storageInstances.route(SOURCE, MINIO, OnboardingFileMocker.POLICY_CODE);
        String bucketPath = BASE + "/instances/" + MINIO + "/buckets/" + storage.bucket() + "/status";

        patch(bucketPath, admin, status(FileStorageBucketStatus.READ_ONLY.getCode(), 1))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        patch(bucketPath, admin, status(FileStorageBucketStatus.DISABLED.getCode(), 1))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        patch(BASE + "/instances/" + MINIO + "/status", admin, status(FileStorageInstanceStatus.DISABLED.getCode(), 1))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        assertMutation(patch(BASE + "/instances/" + S3 + "/status", admin,
            status(FileStorageInstanceStatus.DISABLED.getCode(), 1)), S3, 2);
    }

    /** 路由指向的存储桶已不存在（数据不一致）：列表按空目标返回以便运营发现，其余路由正常列出。 */
    @Test
    void test_danglingRoute() {
        storageInstances.route(StorageInstanceMocker.DEFAULT_ROUTE, MINIO, OnboardingFileMocker.POLICY_CODE);
        assertEquals(1, storageData.insertRoute(OperatorBuilder.nextId(), SOURCE, OperatorBuilder.nextId(),
            OnboardingFileMocker.POLICY_CODE, StorageInstanceMocker.UPLOAD_SECONDS,
            clock.instant().truncatedTo(ChronoUnit.MICROS)));

        var routes = get(BASE + "/routes", admin).assertOk().as(StorageRouteListResponseBody.class).getItems();

        assertAll(() -> assertEquals(List.of(StorageInstanceMocker.DEFAULT_ROUTE, SOURCE),
            routes.stream().map(StorageRouteResponseBody::getSourceSystem).toList()),
            () -> assertEquals(MINIO, routes.getFirst().getInstanceCode()),
            () -> assertNull(routes.getLast().getInstanceCode()),
            () -> assertNull(routes.getLast().getBucketName()));
    }

    /** 本进程快照选择的写入目标。 */
    private void assertTarget(String source, String instanceCode) {
        StorageWriteTarget target = registry.forWrite(source);
        assertAll(() -> assertEquals(instanceCode, target.getProvider()),
            () -> assertEquals(storage.bucket(), target.getContainer()));
    }

    /** 新建路由请求，指向实例的测试存储桶与测试策略。 */
    private CreateStorageRouteRequestBody create(String source, String instanceCode) {
        return new CreateStorageRouteRequestBody(source, instanceCode, storage.bucket(), OnboardingFileMocker.POLICY_CODE,
            StorageInstanceMocker.UPLOAD_SECONDS, REASON);
    }

    /** 修改路由请求。 */
    private UpdateStorageRouteRequestBody update(String instanceCode, int seconds, int expectedVersion) {
        return new UpdateStorageRouteRequestBody(instanceCode, storage.bucket(), OnboardingFileMocker.POLICY_CODE, seconds,
            expectedVersion, REASON);
    }

    /** 保存结果的编码与版本。 */
    private static ConfigurationMutationResponseBody assertMutation(
        com.paytech.platform.support.test.checker.PlatformResponseChecker checker, String code, int version) {
        var mutation = checker.assertOk().as(ConfigurationMutationResponseBody.class);
        assertAll(() -> assertEquals(code, mutation.getObjectCode()), () -> assertEquals(version, mutation.getVersion()));
        return mutation;
    }
}
