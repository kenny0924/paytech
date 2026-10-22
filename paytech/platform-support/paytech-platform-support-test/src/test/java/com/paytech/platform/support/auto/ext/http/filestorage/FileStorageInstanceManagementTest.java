package com.paytech.platform.support.auto.ext.http.filestorage;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.api.enums.FileStorageCheckResult;
import com.paytech.platform.support.api.enums.FileStorageCredentialMode;
import com.paytech.platform.support.api.enums.FileStorageInstanceStatus;
import com.paytech.platform.support.api.enums.FileStorageType;
import com.paytech.platform.support.api.filestorage.FileStorageManagementApi;
import com.paytech.platform.support.api.filestorage.request.ChangeStorageStatusRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateStorageInstanceRequestBody;
import com.paytech.platform.support.api.filestorage.request.RegisterStorageBucketRequestBody;
import com.paytech.platform.support.api.filestorage.request.ReplaceStorageCredentialRequestBody;
import com.paytech.platform.support.api.filestorage.request.UpdateStorageInstanceRequestBody;
import com.paytech.platform.support.api.filestorage.response.StorageBucketCheckResponseBody;
import com.paytech.platform.support.api.filestorage.response.StorageInstanceListResponseBody;
import com.paytech.platform.support.api.filestorage.response.StorageInstanceResponseBody;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.test.base.FileStorageManagementTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.builder.StorageInstanceRequestBuilder;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.external.StorageVaultMocker;

/**
 * 存储实例与存储桶维护：新建（密钥只进 Vault）、编辑、更换密钥、桶登记与检查、状态流转、版本冲突、Vault 不可用与审计。
 *
 * @see FileStorageManagementApi#listInstances(String, String)
 * @see FileStorageManagementApi#createInstance(String, String, CreateStorageInstanceRequestBody)
 * @see FileStorageManagementApi#updateInstance(String, String, String, UpdateStorageInstanceRequestBody)
 * @see FileStorageManagementApi#replaceCredential(String, String, String, ReplaceStorageCredentialRequestBody)
 * @see FileStorageManagementApi#changeInstanceStatus(String, String, String, ChangeStorageStatusRequestBody)
 * @see FileStorageManagementApi#registerBucket(String, String, String, RegisterStorageBucketRequestBody)
 * @see FileStorageManagementApi#checkBucket(String, String, String, String)
 * @see FileStorageManagementApi#changeBucketStatus(String, String, String, String, ChangeStorageStatusRequestBody)
 */
@ExtendWith(OutputCaptureExtension.class)
class FileStorageInstanceManagementTest extends FileStorageManagementTestBase {

    private static final String MINIO = "UT_ADMIN_MINIO";

    private static final String S3 = "UT_ADMIN_S3";

    private static final String S3_DEFAULT_ENDPOINT = "UT_ADMIN_S3_REGIONAL";

    private static final String S3_ROOT = "UT_ADMIN_S3_ROOT";

    private static final String MISSING = "UT_MISSING";

    private static final String REPLACE = "OPERATIONS_FILE_STORAGE_CREDENTIAL_REPLACE";

    private static final String MINIO_TOKEN = StorageVaultMocker.token("admin-minio");

    private static final String S3_TOKEN = StorageVaultMocker.token("admin-s3");

    private static final String CREATE = "OPERATIONS_FILE_STORAGE_INSTANCE_CREATE";

    @Autowired
    private Clock clock;

    private Operator admin;

    /** 本类公共：一名持有全部目录权限的平台管理员。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = operationsMocker.operator(OperatorBuilder.administrator());
    }

    /**
     * 新建 MinIO 与 S3 实例、登记并检查桶、启用：两个实例同时可用；密钥只出现在 Vault 请求中，表、审计、响应与日志都没有原值。
     */
    @Test
    void test_normalFlow(CapturedOutput output) {
        CreateStorageInstanceRequestBody minio = request(MINIO).build();
        CreateStorageInstanceRequestBody s3 = request(S3).type(FileStorageType.S3.getCode()).build();
        storageVault.expectProtect(MINIO_TOKEN);
        storageVault.expectProtect(S3_TOKEN);

        assertMutation(post(BASE + "/instances", admin, minio), FileStorageChange.INSTANCE, MINIO,
            FileStorageInstanceStatus.DRAFT.getCode(), 1);
        var event = auditChecker.assertEvent(requestId, CREATE, AuditModels.Result.SUCCEEDED.getCode(), admin.actor(),
            FileStorageChange.INSTANCE, MINIO);
        assertMutation(post(BASE + "/instances", admin, s3), FileStorageChange.INSTANCE, S3,
            FileStorageInstanceStatus.DRAFT.getCode(), 1);

        activate(MINIO, MINIO_TOKEN);
        activate(S3, S3_TOKEN);

        var instances = get(BASE + "/instances", admin).assertOk();
        var items = instances.as(StorageInstanceListResponseBody.class).getItems();
        assertAll(() -> assertEquals(List.of(MINIO, S3), items.stream().map(StorageInstanceResponseBody::getInstanceCode).toList()),
            () -> assertTrue(items.stream().allMatch(StorageInstanceResponseBody::isCredentialConfigured)),
            () -> assertTrue(items.stream().allMatch(i -> FileStorageInstanceStatus.ACTIVE.getCode().equals(i.getStatus()))),
            () -> assertEquals(FileStorageBucketStatus.ACTIVE.getCode(), items.getFirst().getBuckets().getFirst().getStatus()),
            () -> assertFalse(instances.text().contains(MINIO_TOKEN), "列表不得返回 Vault Token"));
        assertEquals(MINIO_TOKEN, storageData.instance(MINIO).get("credential_vault_token"));
        assertEquals(List.of(credentialJson(minio), credentialJson(s3)), storageVault.protectedValues());
        assertTrue(storageVault.protectKeys().getFirst().startsWith("file-storage-credential."));
        assertSecretAbsent(minio.getSecretKey(), output);
        assertSecretAbsent(s3.getSecretKey(), output);
        auditChecker.assertNoSecret(event, minio.getSecretKey());
        assertEquals("SET", attributes(event).get("after.CREDENTIAL"));
    }

    /** 字段与凭据组合非法时在 Vault 之前拒绝，不写库。 */
    @Test
    void test_invalidParameter() {
        post(BASE + "/instances", admin, request("lower").build()).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("instanceCode");
        post(BASE + "/instances", admin, request(MINIO).type("GCS").build())
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("storageType");
        post(BASE + "/instances", admin, request(MINIO).endpoint("http://127.0.0.1:19400").build())
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        for (String endpoint : List.of("https://127.0.0.1:19400/path", "https://user@127.0.0.1:19400",
            "https://127.0.0.1:19400?region=x", "https://127.0.0.1:19400#bucket", "https:///")) {
            post(BASE + "/instances", admin, request(MINIO).endpoint(endpoint).build())
                .assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        }
        post(BASE + "/instances", admin, request(MINIO).certificate("-----BEGIN CERTIFICATE-----\nnot-a-cert\n").build())
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        post(BASE + "/instances", admin, request(MINIO).credential(FileStorageCredentialMode.STATIC.getCode(), null, null)
            .build()).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        post(BASE + "/instances", admin, request(MINIO).credential(FileStorageCredentialMode.STATIC.getCode(), "AK", " ")
            .build()).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        post(BASE + "/instances", admin, request(MINIO).type(FileStorageType.S3.getCode())
            .credential(FileStorageCredentialMode.IAM_ROLE.getCode(), "AK", "SK").build())
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);

        assertTrue(storageVault.protectedValues().isEmpty());
        assertNull(storageData.instance(MINIO));
    }

    /** MINIO 只允许静态密钥且必须填写地址；实例代码已存在时在 Vault 之前拒绝。 */
    @Test
    void test_businessRuleViolation() {
        post(BASE + "/instances", admin, request(MINIO)
            .credential(FileStorageCredentialMode.IAM_ROLE.getCode(), null, null).build())
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        storageVault.expectProtect(MINIO_TOKEN);
        post(BASE + "/instances", admin, request(MINIO).endpoint(null).build())
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        assertNull(storageData.instance(MINIO), "Vault 成功但写库拒绝时不登记实例");

        storageInstances.staticInstance(MINIO, FileStorageType.MINIO, MINIO_TOKEN, false);
        post(BASE + "/instances", admin, request(MINIO).build()).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        assertEquals(1, storageVault.protectedValues().size(), "已存在的代码不再写入 Vault");
    }

    /**
     * IAM_ROLE 的 S3 实例不访问 Vault：可不填证书，地址可为根路径或留空（使用区域默认地址）；Vault 不可用时 STATIC 新建返回 503 且不写库。
     */
    @Test
    void test_vaultUnavailableOnCreate() {
        assertMutation(post(BASE + "/instances", admin, request(S3).type(FileStorageType.S3.getCode())
            .endpoint("https://s3.me-central-1.amazonaws.com/").certificate(null)
            .credential(FileStorageCredentialMode.IAM_ROLE.getCode(), null, null).build()), FileStorageChange.INSTANCE,
            S3, FileStorageInstanceStatus.DRAFT.getCode(), 1);
        assertMutation(post(BASE + "/instances", admin, request(S3_DEFAULT_ENDPOINT).type(FileStorageType.S3.getCode())
            .endpoint(" ").certificate(" ").credential(FileStorageCredentialMode.IAM_ROLE.getCode(), null, null).build()),
            FileStorageChange.INSTANCE, S3_DEFAULT_ENDPOINT, FileStorageInstanceStatus.DRAFT.getCode(), 1);
        assertAll(() -> assertNull(storageData.instance(S3).get("credential_vault_token")),
            () -> assertNull(storageData.instance(S3).get("trusted_certificate_pem")),
            () -> assertNull(storageData.instance(S3_DEFAULT_ENDPOINT).get("endpoint")),
            () -> assertNull(storageData.instance(S3_DEFAULT_ENDPOINT).get("trusted_certificate_pem")));

        storageVault.protectFails(503);
        post(BASE + "/instances", admin, request(MINIO).build()).assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE);
        assertNull(storageData.instance(MINIO));
    }

    /**
     * Vault 不可用时 IAM_ROLE 实例不受影响：检查不读取密钥，使用运行环境默认凭据链（测试以 JVM 系统属性提供本机 MinIO 账号）；
     * 同时 STATIC 实例的检查返回 503。
     */
    @Test
    void test_iamRoleUnaffectedByVault() {
        storageInstances.instance(S3, FileStorageType.S3, FileStorageCredentialMode.IAM_ROLE, null, true,
            FileStorageInstanceStatus.DRAFT);
        storageInstances.bucket(S3, storage.bucket(), FileStorageBucketStatus.DRAFT);
        storageInstances.staticInstance(MINIO, FileStorageType.MINIO, MINIO_TOKEN, false);
        storageInstances.bucket(MINIO, storage.bucket(), FileStorageBucketStatus.DRAFT);
        storageVault.revealUnavailable();
        System.setProperty("aws.accessKeyId", storage.accessKey());
        System.setProperty("aws.secretAccessKey", storage.secretKey());
        try {
            var result = check(S3, storage.bucket()).assertOk().as(StorageBucketCheckResponseBody.class);
            assertEquals(FileStorageCheckResult.OK.getCode(), result.getResult());
        } finally {
            System.clearProperty("aws.accessKeyId");
            System.clearProperty("aws.secretAccessKey");
        }
        check(MINIO, storage.bucket()).assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE);
        assertAll(() -> assertEquals(0, storageVault.revealCount(S3_TOKEN)),
            () -> assertEquals(1, storageVault.revealCount(MINIO_TOKEN)));
    }

    /** 编辑连接设置递增版本并只记录证书摘要；无变化不递增版本；旧版本返回 409。 */
    @Test
    void test_updateInstance() {
        storageInstances.staticInstance(MINIO, FileStorageType.MINIO, MINIO_TOKEN, false);
        var body = new UpdateStorageInstanceRequestBody(storage.endpoint(), "me-central-1", true,
            storage.certificatePem(), 3000, 20000, 1, REASON);

        assertMutation(put(BASE + "/instances/" + MINIO, admin, body), FileStorageChange.INSTANCE, MINIO,
            FileStorageInstanceStatus.ACTIVE.getCode(), 2);
        var attributes = attributes(auditChecker.assertEvent(requestId, "OPERATIONS_FILE_STORAGE_INSTANCE_UPDATE",
            AuditModels.Result.SUCCEEDED.getCode(), admin.actor(), FileStorageChange.INSTANCE, MINIO));
        var row = storageData.instance(MINIO);
        assertAll(() -> assertEquals("me-central-1", row.get("region")),
            () -> assertEquals(1, intValue(row.get("path_style_access"))),
            () -> assertEquals(3000, intValue(row.get("connect_timeout_ms"))),
            () -> assertEquals("me-central-1", attributes.get("after.REGION")),
            () -> assertNull(attributes.get("after.TRUSTED_CERTIFICATE_DIGEST"), "证书未变不记录"));

        body.setExpectedVersion(2);
        assertMutation(put(BASE + "/instances/" + MINIO, admin, body), FileStorageChange.INSTANCE, MINIO,
            FileStorageInstanceStatus.ACTIVE.getCode(), 2);
        body.setExpectedVersion(1);
        put(BASE + "/instances/" + MINIO, admin, body).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        put(BASE + "/instances/UT_MISSING", admin, body).assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    /** 更换密钥：新 Token 生效，之后的检查只读取新 Token；旧版本 409 且不写入 Vault。 */
    @Test
    void test_replaceCredential() {
        storageInstances.staticInstance(MINIO, FileStorageType.MINIO, MINIO_TOKEN, false);
        storageInstances.bucket(MINIO, storage.bucket(), FileStorageBucketStatus.DRAFT);
        String rotated = StorageVaultMocker.token("admin-minio-rotated");
        storageVault.expectProtect(rotated);
        storageVault.credential(rotated, storage.accessKey(), storage.secretKey());
        var body = new ReplaceStorageCredentialRequestBody(FileStorageCredentialMode.STATIC.getCode(), "UT-NEW-AK",
            "ut-new-secret-value", 1, REASON);

        assertMutation(put(BASE + "/instances/" + MINIO + "/credential", admin, body), FileStorageChange.INSTANCE,
            MINIO, FileStorageInstanceStatus.ACTIVE.getCode(), 2);
        var event = auditChecker.assertEvent(requestId, "OPERATIONS_FILE_STORAGE_CREDENTIAL_REPLACE",
            AuditModels.Result.SUCCEEDED.getCode(), admin.actor(), FileStorageChange.INSTANCE, MINIO);
        check(MINIO, storage.bucket()).assertOk();

        assertAll(() -> assertEquals(rotated, storageData.instance(MINIO).get("credential_vault_token")),
            () -> assertNotNull(storageData.instance(MINIO).get("credential_update_time")),
            () -> assertEquals(1, storageVault.revealCount(rotated)),
            () -> assertEquals(0, storageVault.revealCount(MINIO_TOKEN)),
            () -> assertEquals("REPLACED", attributes(event).get("after.CREDENTIAL")));
        auditChecker.assertNoSecret(event, "ut-new-secret-value");
        put(BASE + "/instances/" + MINIO + "/credential", admin, body)
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        assertEquals(1, storageVault.protectedValues().size());
    }

    /** 检查结果分别为不存在、未开版本控制、未开加密与无权限，均回写且拒绝启用。 */
    @Test
    void test_bucketCheckFailures() {
        String rootToken = StorageVaultMocker.token("admin-root");
        storageVault.credential(rootToken, tempBuckets.rootUser(), tempBuckets.rootPassword());
        storageInstances.instance(MINIO, FileStorageType.MINIO, FileStorageCredentialMode.STATIC, rootToken, false,
            FileStorageInstanceStatus.DRAFT);
        storageInstances.staticInstance(S3, FileStorageType.S3, S3_TOKEN, true);
        String missing = tempBuckets.missing();
        String unversioned = tempBuckets.unversioned();
        String unencrypted = tempBuckets.unencrypted();

        assertCheck(MINIO, missing, FileStorageCheckResult.NOT_FOUND);
        assertCheck(MINIO, unversioned, FileStorageCheckResult.VERSIONING_DISABLED);
        assertCheck(MINIO, unencrypted, FileStorageCheckResult.ENCRYPTION_DISABLED);
        assertCheck(S3, unversioned, FileStorageCheckResult.ACCESS_DENIED);
        storageInstances.instance(S3_ROOT, FileStorageType.S3, FileStorageCredentialMode.STATIC, rootToken, true,
            FileStorageInstanceStatus.DRAFT);
        assertCheck(S3_ROOT, missing, FileStorageCheckResult.NOT_FOUND);
        assertCheck(S3_ROOT, unversioned, FileStorageCheckResult.VERSIONING_DISABLED);
        assertCheck(S3_ROOT, unencrypted, FileStorageCheckResult.ENCRYPTION_DISABLED);

        patch(BASE + "/instances/" + MINIO + "/buckets/" + unencrypted + "/status", admin,
            status(FileStorageBucketStatus.ACTIVE.getCode(), 2)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        assertEquals(List.of(), storage.objectsUnder(".platform-readiness/"));
    }

    /** 启用桶要求 10 分钟内 OK；桶状态只能按允许路径转换；实例启用要求有 ACTIVE 桶。 */
    @Test
    void test_statusTransitions() {
        storageInstances.instance(MINIO, FileStorageType.MINIO, FileStorageCredentialMode.STATIC, MINIO_TOKEN, false,
            FileStorageInstanceStatus.DRAFT);
        long bucketId = storageInstances.bucket(MINIO, storage.bucket(), FileStorageBucketStatus.DRAFT);
        String bucketPath = BASE + "/instances/" + MINIO + "/buckets/" + storage.bucket() + "/status";

        patch(BASE + "/instances/" + MINIO + "/status", admin, status(FileStorageInstanceStatus.ACTIVE.getCode(), 1))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        patch(bucketPath, admin, status(FileStorageBucketStatus.ACTIVE.getCode(), 1))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        storageVault.credential(MINIO_TOKEN, storage.accessKey(), storage.secretKey());
        check(MINIO, storage.bucket()).assertOk();
        storageData.bucketCheckTime(bucketId, clock.instant().minus(11, ChronoUnit.MINUTES));
        patch(bucketPath, admin, status(FileStorageBucketStatus.ACTIVE.getCode(), 2))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        check(MINIO, storage.bucket()).assertOk();

        assertMutation(patch(bucketPath, admin, status(FileStorageBucketStatus.ACTIVE.getCode(), 3)),
            FileStorageChange.BUCKET, MINIO + "/" + storage.bucket(), FileStorageBucketStatus.ACTIVE.getCode(), 4);
        assertMutation(patch(bucketPath, admin, status(FileStorageBucketStatus.READ_ONLY.getCode(), 4)),
            FileStorageChange.BUCKET, MINIO + "/" + storage.bucket(), FileStorageBucketStatus.READ_ONLY.getCode(), 5);
        assertMutation(patch(bucketPath, admin, status(FileStorageBucketStatus.DISABLED.getCode(), 5)),
            FileStorageChange.BUCKET, MINIO + "/" + storage.bucket(), FileStorageBucketStatus.DISABLED.getCode(), 6);
        patch(bucketPath, admin, status(FileStorageBucketStatus.ACTIVE.getCode(), 6))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        patch(BASE + "/instances/" + MINIO + "/status", admin, status(FileStorageBucketStatus.READ_ONLY.getCode(), 1))
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        String untimed = "ut-untimed-check";
        assertEquals(1, storageData.insertBucket(OperatorBuilder.nextId(), instanceId(MINIO), untimed,
            FileStorageBucketStatus.DRAFT.getCode(), null, FileStorageCheckResult.OK.getCode(), clock.instant()));
        patch(BASE + "/instances/" + MINIO + "/buckets/" + untimed + "/status", admin,
            status(FileStorageBucketStatus.ACTIVE.getCode(), 1)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        patch(BASE + "/instances/" + MINIO + "/status", admin, status(FileStorageInstanceStatus.DISABLED.getCode(), 1))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
    }

    /**
     * 检查不可用：Vault 返回的密钥格式不合法、实例的凭据登记不完整（MINIO 配 IAM_ROLE、STATIC 无 Token，属数据不一致）或 Vault 不可用时返回
     * 503 且不回写；不存在的实例或桶 404；重复登记 400。
     */
    @Test
    void test_checkUnavailable() {
        storageInstances.staticInstance(MINIO, FileStorageType.MINIO, MINIO_TOKEN, false);
        register(MINIO, storage.bucket());
        post(BASE + "/instances/" + MINIO + "/buckets", admin,
            new RegisterStorageBucketRequestBody(storage.bucket(), REASON)).assertProblem(400,
                CommonErrorCode.BUSINESS_CHECK_FAILED);
        check(MINIO, "ut-unregistered-bucket").assertProblem(404, CommonErrorCode.NOT_FOUND);
        check(MISSING, storage.bucket()).assertProblem(404, CommonErrorCode.NOT_FOUND);

        for (String malformed : List.of("", "not-json", "{}", "{\"accessKeyId\":1,\"secretAccessKey\":\"x\"}",
            "{\"accessKeyId\":\" \",\"secretAccessKey\":\"x\"}")) {
            storageVault.rawCredential(MINIO_TOKEN, malformed);
            check(MINIO, storage.bucket()).assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE);
        }
        String iamMinio = "UT_ADMIN_MINIO_IAM";
        storageInstances.instance(iamMinio, FileStorageType.MINIO, FileStorageCredentialMode.IAM_ROLE, null, false,
            FileStorageInstanceStatus.DRAFT);
        storageInstances.bucket(iamMinio, storage.bucket(), FileStorageBucketStatus.DRAFT);
        check(iamMinio, storage.bucket()).assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE);
        String tokenless = "UT_ADMIN_NO_TOKEN";
        storageInstances.instance(tokenless, FileStorageType.S3, FileStorageCredentialMode.STATIC, null, true,
            FileStorageInstanceStatus.DRAFT);
        storageInstances.bucket(tokenless, storage.bucket(), FileStorageBucketStatus.DRAFT);
        check(tokenless, storage.bucket()).assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE);

        storageVault.revealUnavailable();
        check(MINIO, storage.bucket()).assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE);

        assertNull(storageData.bucket(MINIO, storage.bucket()).get("last_check_result"));
    }

    /** 没有存储管理权限的人员不能查看或维护；只读权限可以查看不能维护。 */
    @Test
    void test_permissionDenied() {
        Operator viewer = operationsMocker.operator(OperatorBuilder.viewer());
        long role = operationsMocker.customRole("FILE_STORAGE_READER", "FILE_STORAGE_READ");
        Operator reader = operationsMocker.operator(OperatorBuilder.viewer().roles(role));

        get(BASE + "/instances", viewer).assertProblem(403, "OPERATIONS_PERMISSION_DENIED");
        get(BASE + "/instances", reader).assertOk();
        post(BASE + "/instances", reader, request(MINIO).build()).assertProblem(403, "OPERATIONS_PERMISSION_DENIED");
        assertTrue(storageVault.protectedValues().isEmpty());
    }

    /**
     * 凭据方式切换：S3 实例 STATIC→IAM_ROLE 清除 Token 且不访问 Vault，IAM_ROLE→STATIC 写入新 Token；MINIO 不能切到 IAM_ROLE；
     * 密钥不完整或 IAM_ROLE 带密钥在 Vault 之前拒绝；Vault 不可用时 503 且不改库；实例不存在 404。列表显示未配置密钥与未检查的桶。
     */
    @Test
    void test_credentialModeSwitch() {
        storageInstances.staticInstance(S3, FileStorageType.S3, S3_TOKEN, true);
        storageInstances.bucket(S3, storage.bucket(), FileStorageBucketStatus.DRAFT);
        storageInstances.staticInstance(MINIO, FileStorageType.MINIO, MINIO_TOKEN, false);
        String path = BASE + "/instances/" + S3 + "/credential";

        assertMutation(put(path, admin, credential(FileStorageCredentialMode.IAM_ROLE, null, null, 1)),
            FileStorageChange.INSTANCE, S3, FileStorageInstanceStatus.ACTIVE.getCode(), 2);
        var cleared = attributes(auditChecker.assertEvent(requestId, REPLACE, AuditModels.Result.SUCCEEDED.getCode(),
            admin.actor(), FileStorageChange.INSTANCE, S3));
        var listed = get(BASE + "/instances", admin).assertOk().as(StorageInstanceListResponseBody.class).getItems()
            .stream().filter(item -> S3.equals(item.getInstanceCode())).findFirst().orElseThrow();
        assertAll(() -> assertNull(storageData.instance(S3).get("credential_vault_token")),
            () -> assertEquals("CLEARED", cleared.get("after.CREDENTIAL")),
            () -> assertEquals(FileStorageCredentialMode.IAM_ROLE.getCode(), cleared.get("after.CREDENTIAL_MODE")),
            () -> assertEquals(FileStorageCredentialMode.IAM_ROLE.getCode(), listed.getCredentialMode()),
            () -> assertFalse(listed.isCredentialConfigured()),
            () -> assertNull(listed.getBuckets().getFirst().getLastCheckResult()),
            () -> assertTrue(storageVault.protectedValues().isEmpty()));

        assertMutation(put(path, admin, credential(FileStorageCredentialMode.IAM_ROLE, null, null, 2)),
            FileStorageChange.INSTANCE, S3, FileStorageInstanceStatus.ACTIVE.getCode(), 3);
        var unchanged = attributes(auditChecker.assertEvent(requestId, REPLACE, AuditModels.Result.SUCCEEDED.getCode(),
            admin.actor(), FileStorageChange.INSTANCE, S3));
        assertTrue(unchanged.getOrDefault("after.CREDENTIAL", "").isEmpty(), "IAM_ROLE 之间切换没有密钥");

        String rotated = StorageVaultMocker.token("admin-s3-static-again");
        storageVault.expectProtect(rotated);
        assertMutation(put(path, admin, credential(FileStorageCredentialMode.STATIC, "UT-S3-AK", "ut-s3-secret", 3)),
            FileStorageChange.INSTANCE, S3, FileStorageInstanceStatus.ACTIVE.getCode(), 4);
        var set = attributes(auditChecker.assertEvent(requestId, REPLACE, AuditModels.Result.SUCCEEDED.getCode(),
            admin.actor(), FileStorageChange.INSTANCE, S3));
        assertAll(() -> assertEquals(rotated, storageData.instance(S3).get("credential_vault_token")),
            () -> assertEquals("SET", set.get("after.CREDENTIAL")));

        put(BASE + "/instances/" + MINIO + "/credential", admin,
            credential(FileStorageCredentialMode.IAM_ROLE, null, null, 1))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        put(path, admin, credential(FileStorageCredentialMode.STATIC, "UT-S3-AK", " ", 4))
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        put(path, admin, credential(FileStorageCredentialMode.IAM_ROLE, "UT-S3-AK", "ut-s3-secret", 4))
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        put(path, admin, credential(FileStorageCredentialMode.IAM_ROLE, "UT-S3-AK", null, 4))
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        put(path, admin, credential(FileStorageCredentialMode.IAM_ROLE, null, "ut-s3-secret", 4))
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        put(BASE + "/instances/" + MISSING + "/credential", admin,
            credential(FileStorageCredentialMode.IAM_ROLE, null, null, 1)).assertProblem(404, CommonErrorCode.NOT_FOUND);
        storageVault.protectFails(503);
        put(path, admin, credential(FileStorageCredentialMode.STATIC, "UT-S3-AK", "ut-s3-secret", 4))
            .assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE);

        assertAll(() -> assertEquals(4, intValue(storageData.instance(S3).get("version"))),
            () -> assertEquals(rotated, storageData.instance(S3).get("credential_vault_token")),
            () -> assertEquals(MINIO_TOKEN, storageData.instance(MINIO).get("credential_vault_token")));
    }

    /**
     * 不允许的实例与存储桶状态转换、旧版本、不存在的实例与桶均拒绝且不改库；只读桶可直接恢复启用，停用的实例可重新启用。
     */
    @Test
    void test_statusTransitionsRejected() {
        storageInstances.instance(MINIO, FileStorageType.MINIO, FileStorageCredentialMode.STATIC, MINIO_TOKEN, false,
            FileStorageInstanceStatus.DRAFT);
        storageInstances.bucket(MINIO, storage.bucket(), FileStorageBucketStatus.DRAFT);
        String instancePath = BASE + "/instances/" + MINIO + "/status";
        String bucketPath = BASE + "/instances/" + MINIO + "/buckets/" + storage.bucket() + "/status";
        String bucketCode = MINIO + "/" + storage.bucket();

        patch(instancePath, admin, status(FileStorageInstanceStatus.DISABLED.getCode(), 1))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        patch(bucketPath, admin, status(FileStorageBucketStatus.DISABLED.getCode(), 1))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        storageInstances.bucketStatus(MINIO, FileStorageBucketStatus.ACTIVE);
        storageInstances.instanceStatus(MINIO, FileStorageInstanceStatus.ACTIVE);
        patch(instancePath, admin, status(FileStorageInstanceStatus.ACTIVE.getCode(), 1))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        patch(bucketPath, admin, status(FileStorageBucketStatus.ACTIVE.getCode(), 1))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        assertMutation(patch(bucketPath, admin, status(FileStorageBucketStatus.READ_ONLY.getCode(), 1)),
            FileStorageChange.BUCKET, bucketCode, FileStorageBucketStatus.READ_ONLY.getCode(), 2);
        patch(bucketPath, admin, status(FileStorageBucketStatus.READ_ONLY.getCode(), 2))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        patch(bucketPath, admin, status(FileStorageBucketStatus.ACTIVE.getCode(), 1))
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        assertMutation(patch(bucketPath, admin, status(FileStorageBucketStatus.ACTIVE.getCode(), 2)),
            FileStorageChange.BUCKET, bucketCode, FileStorageBucketStatus.ACTIVE.getCode(), 3);

        patch(instancePath, admin, status(FileStorageInstanceStatus.DISABLED.getCode(), 2))
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        assertMutation(patch(instancePath, admin, status(FileStorageInstanceStatus.DISABLED.getCode(), 1)),
            FileStorageChange.INSTANCE, MINIO, FileStorageInstanceStatus.DISABLED.getCode(), 2);
        patch(instancePath, admin, status(FileStorageInstanceStatus.DISABLED.getCode(), 2))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        assertMutation(patch(instancePath, admin, status(FileStorageInstanceStatus.ACTIVE.getCode(), 2)),
            FileStorageChange.INSTANCE, MINIO, FileStorageInstanceStatus.ACTIVE.getCode(), 3);

        patch(BASE + "/instances/" + MINIO + "/buckets/ut-unregistered-bucket/status", admin,
            status(FileStorageBucketStatus.ACTIVE.getCode(), 1)).assertProblem(404, CommonErrorCode.NOT_FOUND);
        patch(BASE + "/instances/" + MISSING + "/status", admin, status(FileStorageInstanceStatus.ACTIVE.getCode(), 1))
            .assertProblem(404, CommonErrorCode.NOT_FOUND);
        assertAll(() -> assertEquals(3, intValue(storageData.instance(MINIO).get("version"))),
            () -> assertEquals(3, intValue(storageData.bucket(MINIO, storage.bucket()).get("version"))));
    }

    /** 登记、检查、启用存储桶后启用实例。 */
    private void activate(String code, String token) {
        storageVault.credential(token, storage.accessKey(), storage.secretKey());
        register(code, storage.bucket());
        var result = check(code, storage.bucket()).assertOk().as(StorageBucketCheckResponseBody.class);
        assertEquals(FileStorageCheckResult.OK.getCode(), result.getResult());
        patch(BASE + "/instances/" + code + "/buckets/" + storage.bucket() + "/status", admin,
            status(FileStorageBucketStatus.ACTIVE.getCode(), result.getVersion())).assertOk();
        patch(BASE + "/instances/" + code + "/status", admin, status(FileStorageInstanceStatus.ACTIVE.getCode(), 1))
            .assertOk();
    }

    /** 登记 DRAFT 存储桶。 */
    private void register(String code, String bucket) {
        assertMutation(post(BASE + "/instances/" + code + "/buckets", admin,
            new RegisterStorageBucketRequestBody(bucket, REASON)), FileStorageChange.BUCKET, code + "/" + bucket,
            FileStorageBucketStatus.DRAFT.getCode(), 1);
    }

    /** 检查结果与回写一致。 */
    private void assertCheck(String code, String bucket, FileStorageCheckResult expected) {
        storageInstances.bucket(code, bucket, FileStorageBucketStatus.DRAFT);
        var result = check(code, bucket).assertOk().as(StorageBucketCheckResponseBody.class);
        assertAll(() -> assertEquals(expected.getCode(), result.getResult(), bucket),
            () -> assertEquals(expected.getCode(), storageData.bucket(code, bucket).get("last_check_result")));
    }

    /** 调用检查接口。 */
    private PlatformResponseChecker check(String code, String bucket) {
        return post(BASE + "/instances/" + code + "/buckets/" + bucket + "/check", admin, null);
    }

    /** 保存结果的身份、状态与版本。 */
    private static ConfigurationMutationResponseBody assertMutation(PlatformResponseChecker checker, String type,
        String code, String status, int version) {
        var mutation = checker.assertOk().as(ConfigurationMutationResponseBody.class);
        assertAll(() -> assertEquals(type, mutation.getObjectType()), () -> assertEquals(code, mutation.getObjectCode()),
            () -> assertEquals(status, mutation.getStatus()), () -> assertEquals(version, mutation.getVersion()));
        return mutation;
    }

    /** 实例主键。 */
    private long instanceId(String code) {
        return ((Number) storageData.instance(code).get("instance_id")).longValue();
    }

    /** 更换密钥请求。 */
    private static ReplaceStorageCredentialRequestBody credential(FileStorageCredentialMode mode, String accessKey,
        String secretKey, int expectedVersion) {
        return new ReplaceStorageCredentialRequestBody(mode.getCode(), accessKey, secretKey, expectedVersion, REASON);
    }

    /** 指向本机 MinIO 的默认新建请求。 */
    private StorageInstanceRequestBuilder request(String code) {
        return StorageInstanceRequestBuilder.template(code, storage.endpoint(), storage.certificatePem());
    }

    /** 密钥只以 FILE_STORAGE_CREDENTIAL_V1 JSON 进入 Vault。 */
    private static String credentialJson(CreateStorageInstanceRequestBody body) {
        return "{\"accessKeyId\":\"" + body.getAccessKey() + "\",\"secretAccessKey\":\"" + body.getSecretKey() + "\"}";
    }

    /** 表与日志中没有密钥原值。 */
    private void assertSecretAbsent(String secret, CapturedOutput output) {
        assertAll(() -> assertTrue(storageData.storageTexts().stream().noneMatch(text -> text.contains(secret)),
            "存储表含密钥原值"),
            () -> assertTrue(storageData.auditAttributeTexts().stream().noneMatch(text -> text.contains(secret)),
                "审计属性含密钥原值"),
            () -> assertFalse(output.getAll().contains(secret), "日志含密钥原值"));
    }
}
