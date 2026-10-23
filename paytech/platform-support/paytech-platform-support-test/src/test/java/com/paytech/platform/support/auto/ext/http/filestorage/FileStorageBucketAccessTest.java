package com.paytech.platform.support.auto.ext.http.filestorage;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.api.enums.FileStorageCheckResult;
import com.paytech.platform.support.api.filestorage.FileStorageManagementApi;
import com.paytech.platform.support.api.filestorage.request.RegisterStorageBucketRequestBody;
import com.paytech.platform.support.api.filestorage.request.UpdateStorageBucketRequestBody;
import com.paytech.platform.support.api.filestorage.response.StorageBucket;
import com.paytech.platform.support.api.filestorage.response.StorageBucketCheckResponseBody;
import com.paytech.platform.support.api.filestorage.response.StorageInstanceListResponseBody;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.integration.storage.ProbeObject;
import com.paytech.platform.support.test.base.FileStorageManagementTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.StorageInstanceMocker;

/**
 * 存储桶访问级别：登记时校验公开桶的对外基础地址；检查按级别核对匿名访问——私有桶必须拒绝匿名读取，公开桶必须经基础地址匿名读到探测
 * 对象；DRAFT 公开桶可修改基础地址并清除检查结果。
 * <li>公开桶为本机 MinIO 的临时匿名只读桶，经管理员密钥的实例访问；对外基础地址为 MinIO 路径风格地址。</li>
 *
 * @see FileStorageManagementApi#registerBucket(String, String, String, RegisterStorageBucketRequestBody)
 * @see FileStorageManagementApi#updateBucket(String, String, String, String, UpdateStorageBucketRequestBody)
 * @see FileStorageManagementApi#checkBucket(String, String, String, String)
 * @see FileStorageManagementApi#listInstances(String, String)
 */
class FileStorageBucketAccessTest extends FileStorageManagementTestBase {

    private static final String ROOT = StorageInstanceMocker.ROOT_INSTANCE;

    private static final String PUBLIC = FileAccessLevel.PUBLIC.getCode();

    private static final String PRIVATE = FileAccessLevel.PRIVATE.getCode();

    private Operator admin;

    /** 本类公共：管理员密钥的 ACTIVE 实例与平台管理员。 */
    @BeforeEach
    void setUpClassMocks() {
        storageInstances.rootInstance();
        storageInstances.refresh();
        admin = operationsMocker.operator(OperatorBuilder.administrator());
    }

    /** 公开桶：检查依次通过且匿名读到探测对象后为 OK，可启用；列表返回访问级别与对外基础地址；审计记录两项。 */
    @Test
    void test_normalFlow() {
        String bucket = tempBuckets.publicReadable();
        String baseUrl = tempBuckets.publicBaseUrl(bucket);

        assertMutation(register(bucket, PUBLIC, baseUrl), ROOT + "/" + bucket, 1);
        var event = auditChecker.assertEvent(requestId, "OPERATIONS_FILE_STORAGE_BUCKET_REGISTER",
            AuditModels.Result.SUCCEEDED.getCode(), admin.actor(), FileStorageChange.BUCKET, ROOT + "/" + bucket);
        assertEquals(FileStorageCheckResult.OK.getCode(), check(bucket).getResult());
        patch(bucketPath(bucket) + "/status", admin, status(FileStorageBucketStatus.ACTIVE.getCode(), 2)).assertOk();

        StorageBucket listed = listed(bucket);
        Map<String, String> attributes = attributes(event);
        assertAll(() -> assertEquals(PUBLIC, listed.getAccessLevel()),
            () -> assertEquals(baseUrl, listed.getPublicBaseUrl()),
            () -> assertEquals(FileStorageBucketStatus.ACTIVE.getCode(), listed.getStatus()),
            () -> assertEquals(PUBLIC, attributes.get("after.ACCESS_LEVEL")),
            () -> assertEquals(baseUrl, attributes.get("after.PUBLIC_BASE_URL")),
            () -> assertEquals(PRIVATE, listedOther()));
    }

    /** 私有桶实际允许匿名读取：检查结果 PUBLIC_ACCESS_ALLOWED（桶允许匿名访问），不能启用。 */
    @Test
    void test_privateBucketAllowsAnonymous() {
        String bucket = tempBuckets.publicReadable();
        assertMutation(register(bucket, PRIVATE, null), ROOT + "/" + bucket, 1);

        assertEquals(FileStorageCheckResult.PUBLIC_ACCESS_ALLOWED.getCode(), check(bucket).getResult());

        patch(bucketPath(bucket) + "/status", admin, status(FileStorageBucketStatus.ACTIVE.getCode(), 2))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        assertEquals(FileStorageCheckResult.PUBLIC_ACCESS_ALLOWED.getCode(),
            storageData.bucket(ROOT, bucket).get("last_check_result"));
    }

    /**
     * 公开桶的探测对象经基础地址匿名读不到（桶拒绝匿名访问，或基础地址不可连接）：检查结果 PUBLIC_READ_FAILED（公开地址不可访问），
     * 不能启用；私有的仓库测试桶按私有登记时检查通过。
     */
    @Test
    void test_publicReadFailed() {
        String privateBucket = storage.bucket();
        assertMutation(register(privateBucket, PUBLIC, tempBuckets.publicBaseUrl(privateBucket)),
            ROOT + "/" + privateBucket, 1);
        assertEquals(FileStorageCheckResult.PUBLIC_READ_FAILED.getCode(), check(privateBucket).getResult());
        patch(bucketPath(privateBucket) + "/status", admin, status(FileStorageBucketStatus.ACTIVE.getCode(), 2))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        String unreachable = tempBuckets.publicReadable();
        assertMutation(register(unreachable, PUBLIC, "https://127.0.0.1:1/" + unreachable), ROOT + "/" + unreachable, 1);
        assertEquals(FileStorageCheckResult.PUBLIC_READ_FAILED.getCode(), check(unreachable).getResult());
        assertEquals(List.of(), storage.objectsUnder(ProbeObject.PREFIX), "探测对象应已删除");
    }

    /**
     * 登记校验：公开桶缺少基础地址、非 HTTPS、带查询串或片段、以 / 结尾或含用户信息拒绝；私有桶带基础地址拒绝；访问级别缺失或非法为
     * 字段错误；均不写库。
     */
    @Test
    void test_invalidParameter() {
        String bucket = "ut-access-invalid";
        for (String invalid : new String[] {null, "", "http://cdn.example.com/files", "https://cdn.example.com/files/",
            "https://cdn.example.com/files?x=1", "https://cdn.example.com/files#a", "https://user@cdn.example.com",
            "ftp://cdn.example.com", "https:///files"}) {
            register(bucket, PUBLIC, invalid).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        }
        register(bucket, PRIVATE, "https://cdn.example.com/files")
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        register(bucket, null, null).assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("accessLevel");
        register(bucket, "public", null).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("accessLevel");
        register(bucket, PUBLIC, "https://cdn.example.com/" + "a".repeat(250))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("publicBaseUrl");

        assertNull(storageData.bucket(ROOT, bucket));
    }

    /**
     * 修改基础地址：DRAFT 公开桶可改，地址变化时清除最近检查结果并递增版本，记审计；无变化不递增；旧版本 409；私有桶、非 DRAFT 桶拒绝；
     * 不存在 404。
     */
    @Test
    void test_updateBucket() {
        String bucket = tempBuckets.publicReadable();
        String baseUrl = tempBuckets.publicBaseUrl(bucket);
        register(bucket, PUBLIC, "https://cdn.example.com/" + bucket).assertOk();
        assertEquals(FileStorageCheckResult.PUBLIC_READ_FAILED.getCode(), check(bucket).getResult());

        assertMutation(update(bucket, baseUrl, 2), ROOT + "/" + bucket, 3);
        var event = auditChecker.assertEvent(requestId, "OPERATIONS_FILE_STORAGE_BUCKET_UPDATE",
            AuditModels.Result.SUCCEEDED.getCode(), admin.actor(), FileStorageChange.BUCKET, ROOT + "/" + bucket);
        Map<String, Object> row = storageData.bucket(ROOT, bucket);
        assertAll(() -> assertEquals(baseUrl, row.get("public_base_url")),
            () -> assertNull(row.get("last_check_result"), "修改地址后需重新检查"),
            () -> assertNull(row.get("last_check_time")),
            () -> assertEquals(baseUrl, attributes(event).get("after.PUBLIC_BASE_URL")),
            () -> assertEquals(FileStorageCheckResult.PUBLIC_READ_FAILED.getCode(),
                attributes(event).get("before.CHECK_RESULT")));
        assertMutation(update(bucket, baseUrl, 3), ROOT + "/" + bucket, 3);
        update(bucket, baseUrl + "x", 2).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        update(bucket, "http://cdn.example.com", 3).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        assertEquals(FileStorageCheckResult.OK.getCode(), check(bucket).getResult());
        patch(bucketPath(bucket) + "/status", admin, status(FileStorageBucketStatus.ACTIVE.getCode(), 4)).assertOk();
        update(bucket, baseUrl + "x", 5).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        String privateBucket = "ut-access-private";
        register(privateBucket, PRIVATE, null).assertOk();
        update(privateBucket, baseUrl, 1).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        update("ut-access-missing", baseUrl, 1).assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    /** 登记存储桶。 */
    private PlatformResponseChecker register(String bucket, String accessLevel, String publicBaseUrl) {
        return post(BASE + "/instances/" + ROOT + "/buckets", admin,
            new RegisterStorageBucketRequestBody(bucket, accessLevel, publicBaseUrl, REASON));
    }

    /** 修改对外基础地址。 */
    private PlatformResponseChecker update(String bucket, String publicBaseUrl, int expectedVersion) {
        return put(bucketPath(bucket), admin, new UpdateStorageBucketRequestBody(publicBaseUrl, expectedVersion, REASON));
    }

    /** 检查存储桶。 */
    private StorageBucketCheckResponseBody check(String bucket) {
        return post(bucketPath(bucket) + "/check", admin, null).assertOk().as(StorageBucketCheckResponseBody.class);
    }

    /** 存储桶路径。 */
    private static String bucketPath(String bucket) {
        return BASE + "/instances/" + ROOT + "/buckets/" + bucket;
    }

    /** 实例列表中的存储桶。 */
    private StorageBucket listed(String bucket) {
        return get(BASE + "/instances", admin).assertOk().as(StorageInstanceListResponseBody.class).getItems().stream()
            .flatMap(instance -> instance.getBuckets().stream()).filter(item -> item.getBucketName().equals(bucket))
            .findFirst().orElseThrow();
    }

    /** 私有登记的另一个桶在列表中的访问级别。 */
    private String listedOther() {
        String other = "ut-access-listed";
        register(other, PRIVATE, null).assertOk();
        StorageBucket bucket = listed(other);
        assertNull(bucket.getPublicBaseUrl());
        return bucket.getAccessLevel();
    }

    /** 保存结果的编码与版本。 */
    private static void assertMutation(PlatformResponseChecker checker, String code, int version) {
        var mutation = checker.assertOk().as(ConfigurationMutationResponseBody.class);
        assertAll(() -> assertEquals(code, mutation.getObjectCode()), () -> assertEquals(version, mutation.getVersion()));
    }
}
