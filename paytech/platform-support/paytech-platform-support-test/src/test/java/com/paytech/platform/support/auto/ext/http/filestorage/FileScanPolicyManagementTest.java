package com.paytech.platform.support.auto.ext.http.filestorage;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.api.enums.FileStorageType;
import com.paytech.platform.support.api.filestorage.FileStorageManagementApi;
import com.paytech.platform.support.api.filestorage.request.ActivateScanPolicyRevisionRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateScanPolicyRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateScanPolicyRevisionRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateStorageRouteRequestBody;
import com.paytech.platform.support.api.filestorage.request.DeleteStorageRouteRequestBody;
import com.paytech.platform.support.api.filestorage.request.DiscardScanPolicyRevisionRequestBody;
import com.paytech.platform.support.api.filestorage.request.RetireScanPolicyRequestBody;
import com.paytech.platform.support.api.filestorage.request.UpdateScanPolicyRevisionRequestBody;
import com.paytech.platform.support.api.filestorage.response.ScanPolicyDetailResponseBody;
import com.paytech.platform.support.api.filestorage.response.ScanPolicyListResponseBody;
import com.paytech.platform.support.api.filestorage.response.ScanPolicySummary;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.enums.FileScanType;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.integration.scanning.FormatFileScanner;
import com.paytech.platform.support.test.base.FileStorageManagementTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.dal.FileTestMapper;
import com.paytech.platform.support.test.mocker.local.FileMocker;
import com.paytech.platform.support.test.mocker.local.StorageInstanceMocker;

/**
 * 检查策略维护：代码 + 修订（DRAFT / ACTIVE / RETIRED），同一代码最多一个 ACTIVE 与一个 DRAFT；新修订自动带格式检查要求；激活时原
 * ACTIVE 改为 RETIRED；被写入路由引用的策略不能停用；草稿可废弃；允许类型只能取格式检查器能识别的类型。
 *
 * @see FileStorageManagementApi#listScanPolicies(String, String)
 * @see FileStorageManagementApi#getScanPolicy(String, String, String)
 * @see FileStorageManagementApi#createScanPolicy(String, String, CreateScanPolicyRequestBody)
 * @see FileStorageManagementApi#createScanPolicyRevision(String, String, String, CreateScanPolicyRevisionRequestBody)
 * @see FileStorageManagementApi#updateScanPolicyRevision(String, String, String, int, UpdateScanPolicyRevisionRequestBody)
 * @see FileStorageManagementApi#discardScanPolicyRevision(String, String, String, int, DiscardScanPolicyRevisionRequestBody)
 * @see FileStorageManagementApi#activateScanPolicyRevision(String, String, String, int, ActivateScanPolicyRevisionRequestBody)
 * @see FileStorageManagementApi#retireScanPolicy(String, String, String, RetireScanPolicyRequestBody)
 */
class FileScanPolicyManagementTest extends FileStorageManagementTestBase {

    private static final String CODE = FileMocker.MANAGED_POLICY_PREFIX + "LOGO";

    private static final String POLICIES = BASE + "/scan-policies";

    private static final String PNG = "image/png";

    private static final String JPEG = "image/jpeg";

    private static final String PDF = "application/pdf";

    @Autowired
    private FileTestMapper fileData;

    private Operator admin;

    /** 本类公共：平台管理员。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = operationsMocker.operator(OperatorBuilder.administrator());
    }

    /**
     * 全流程：新建（修订 1 草稿与标准格式检查要求）→ 修改草稿 → 激活 → 以 ACTIVE 为模板新建修订 2（已有草稿时 409）→ 修改并激活修订 2
     * （修订 1 RETIRED）→ 路由引用后不能停用 → 删除路由后停用 → 没有 ACTIVE 时不能以模板新建修订；列表与详情按修订状态展示。
     */
    @Test
    void test_normalFlow() {
        assertMutation(post(POLICIES, admin, create(CODE, List.of(PNG, JPEG), "1048576")), CODE + "/1", "DRAFT", 1);
        auditChecker.assertEvent(requestId, "OPERATIONS_FILE_SCAN_POLICY_CREATE", AuditModels.Result.SUCCEEDED.getCode(),
            admin.actor(), FileStorageChange.SCAN_POLICY, CODE + "/1");
        ScanPolicyListResponseBody drafts = get(POLICIES, admin).assertOk().as(ScanPolicyListResponseBody.class);
        ScanPolicySummary draft = summary(drafts);
        long firstId = revisionId(1);
        List<Map<String, Object>> rules = fileData.requirementsOf(firstId);
        assertAll(() -> assertEquals(FormatFileScanner.SUPPORTED_MIME_TYPES, drafts.getSupportedMimeTypes()),
            () -> assertEquals("DRAFT", draft.getStatus()), () -> assertNull(draft.getActiveRevisionNo()),
            () -> assertEquals(1, draft.getDraftRevisionNo()), () -> assertEquals(List.of(PNG, JPEG),
                draft.getAllowedMimeTypes()),
            () -> assertEquals("1048576", draft.getMaxSizeBytes()), () -> assertEquals(0, draft.getRouteCount()),
            () -> assertEquals(1, rules.size()),
            () -> assertEquals(FormatFileScanner.SCANNER_CODE, rules.getFirst().get("scanner_code")),
            () -> assertEquals(FileScanType.FORMAT.getCode(), rules.getFirst().get("scan_type")),
            () -> assertEquals(FormatFileScanner.MINIMUM_RUNTIME_VERSION, rules.getFirst().get("minimum_scanner_version")),
            () -> assertEquals(List.of(FormatFileScanner.VALID), objectMapper.readValue(
                (String) rules.getFirst().get("accepted_result_codes"), List.class)));

        assertMutation(put(revision(1), admin, update(List.of(PNG), "2097152", 1)), CODE + "/1", "DRAFT", 2);
        assertMutation(post(revision(1) + "/activation", admin, new ActivateScanPolicyRevisionRequestBody(2, REASON)),
            CODE + "/1", "ACTIVE", 3);
        assertMutation(post(POLICIES + "/" + CODE + "/revisions", admin, new CreateScanPolicyRevisionRequestBody(REASON)),
            CODE + "/2", "DRAFT", 1);
        post(POLICIES + "/" + CODE + "/revisions", admin, new CreateScanPolicyRevisionRequestBody(REASON))
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        assertEquals("2097152", detail().getRevisions().getFirst().getMaxSizeBytes(), "新修订以 ACTIVE 为模板");
        assertMutation(put(revision(2), admin, update(List.of(PNG, PDF), "4194304", 1)), CODE + "/2", "DRAFT", 2);
        assertMutation(post(revision(2) + "/activation", admin, new ActivateScanPolicyRevisionRequestBody(2, REASON)),
            CODE + "/2", "ACTIVE", 3);
        var activated = auditChecker.assertEvent(requestId, "OPERATIONS_FILE_SCAN_POLICY_ACTIVATE",
            AuditModels.Result.SUCCEEDED.getCode(), admin.actor(), FileStorageChange.SCAN_POLICY, CODE + "/2");

        List<Map<String, Object>> stored = fileData.policyRevisions(CODE);
        assertAll(() -> assertEquals("RETIRED", stored.getFirst().get("status")),
            () -> assertNull(stored.getFirst().get("active_slot")),
            () -> assertEquals("ACTIVE", stored.getLast().get("status")),
            () -> assertEquals(1, ((Number) stored.getLast().get("active_slot")).intValue()),
            () -> assertEquals("1", attributes(activated).get("before.REVISION_NO")),
            () -> assertEquals("2", attributes(activated).get("after.REVISION_NO")));

        String routePath = referencedByRoute();
        post(POLICIES + "/" + CODE + "/retirement", admin, new RetireScanPolicyRequestBody(3, REASON))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        ScanPolicyDetailResponseBody referenced = detail();
        ScanPolicySummary active = summary(get(POLICIES, admin).assertOk().as(ScanPolicyListResponseBody.class));
        assertAll(() -> assertEquals(1, referenced.getRoutes().size()),
            () -> assertEquals(StorageInstanceMocker.SOURCE, referenced.getRoutes().getFirst().getSourceSystem()),
            () -> assertEquals(FileAccessLevel.PRIVATE.getCode(), referenced.getRoutes().getFirst().getAccessLevel()),
            () -> assertEquals(StorageInstanceMocker.MINIO_INSTANCE, referenced.getRoutes().getFirst().getInstanceCode()),
            () -> assertEquals(List.of(2, 1), referenced.getRevisions().stream().map(r -> r.getRevisionNo()).toList()),
            () -> assertEquals(1, referenced.getRevisions().getFirst().getRequirements().size()),
            () -> assertEquals("ACTIVE", active.getStatus()), () -> assertEquals(2, active.getActiveRevisionNo()),
            () -> assertNull(active.getDraftRevisionNo()), () -> assertEquals(1, active.getRouteCount()),
            () -> assertEquals(List.of(PNG, PDF), active.getAllowedMimeTypes()));

        delete(routePath, admin, new DeleteStorageRouteRequestBody(1, REASON)).assertOk();
        assertMutation(post(POLICIES + "/" + CODE + "/retirement", admin, new RetireScanPolicyRequestBody(3, REASON)),
            CODE + "/2", "RETIRED", 4);
        assertEquals("RETIRED", summary(get(POLICIES, admin).assertOk().as(ScanPolicyListResponseBody.class)).getStatus());
        post(POLICIES + "/" + CODE + "/revisions", admin, new CreateScanPolicyRevisionRequestBody(REASON))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        post(POLICIES + "/" + CODE + "/retirement", admin, new RetireScanPolicyRequestBody(4, REASON))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
    }

    /** 废弃草稿：物理删除修订及其检查要求；只剩草稿的代码随之不存在；ACTIVE 修订不能废弃，版本不符 409。 */
    @Test
    void test_discardDraft() {
        post(POLICIES, admin, create(CODE, List.of(PNG), "1024")).assertOk();
        long draftId = revisionId(1);
        delete(revision(1), admin, new DiscardScanPolicyRevisionRequestBody(2, REASON))
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        assertMutation(delete(revision(1), admin, new DiscardScanPolicyRevisionRequestBody(1, REASON)), CODE + "/1", null,
            1);
        assertAll(() -> assertTrue(fileData.policyRevisions(CODE).isEmpty()),
            () -> assertTrue(fileData.requirementsOf(draftId).isEmpty()));
        get(POLICIES + "/" + CODE, admin).assertProblem(404, CommonErrorCode.NOT_FOUND);

        post(POLICIES, admin, create(CODE, List.of(PNG), "1024")).assertOk();
        post(revision(1) + "/activation", admin, new ActivateScanPolicyRevisionRequestBody(1, REASON)).assertOk();
        post(POLICIES + "/" + CODE + "/revisions", admin, new CreateScanPolicyRevisionRequestBody(REASON)).assertOk();
        delete(revision(1), admin, new DiscardScanPolicyRevisionRequestBody(2, REASON))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        delete(revision(2), admin, new DiscardScanPolicyRevisionRequestBody(1, REASON)).assertOk();
        assertEquals(List.of("ACTIVE"), fileData.policyRevisions(CODE).stream().map(row -> row.get("status")).toList());
    }

    /**
     * 拒绝：代码已存在 400；类型不受支持或重复、大小越界 400；字段格式 422；修改或激活非草稿 400；版本不符 409；不存在的代码或修订 404；
     * 修订号不是整数 400。均不改变已有修订。
     */
    @Test
    void test_invalidParameter() {
        post(POLICIES, admin, create(CODE, List.of("image/gif"), "1024")).assertProblem(400,
            CommonErrorCode.BUSINESS_CHECK_FAILED);
        post(POLICIES, admin, create(CODE, List.of(PNG, PNG), "1024")).assertProblem(400,
            CommonErrorCode.BUSINESS_CHECK_FAILED);
        post(POLICIES, admin, create(CODE, List.of(PNG), "104857601")).assertProblem(400,
            CommonErrorCode.BUSINESS_CHECK_FAILED);
        post(POLICIES, admin, create(CODE, List.of(PNG), "0")).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("maxSizeBytes");
        post(POLICIES, admin, create(CODE, List.of(), "1024")).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("allowedMimeTypes");
        post(POLICIES, admin, create("ut_mgmt_lower", List.of(PNG), "1024"))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("policyCode");
        assertTrue(fileData.policyRevisions(CODE).isEmpty());

        post(POLICIES, admin, create(CODE, List.of(PNG), "104857600")).assertOk();
        post(POLICIES, admin, create(CODE, List.of(JPEG), "1024")).assertProblem(400,
            CommonErrorCode.BUSINESS_CHECK_FAILED);
        put(revision(1), admin, update(List.of(JPEG), "1024", 2)).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        put(revision(1), admin, update(List.of("image/gif"), "1024", 1))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        post(revision(1) + "/activation", admin, new ActivateScanPolicyRevisionRequestBody(9, REASON))
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        post(revision(1) + "/activation", admin, new ActivateScanPolicyRevisionRequestBody(1, REASON)).assertOk();
        put(revision(1), admin, update(List.of(JPEG), "1024", 2)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        post(revision(1) + "/activation", admin, new ActivateScanPolicyRevisionRequestBody(2, REASON))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        put(revision(7), admin, update(List.of(JPEG), "1024", 1)).assertProblem(404, CommonErrorCode.NOT_FOUND);
        post(POLICIES + "/" + CODE + "/revisions/x/activation", admin, new ActivateScanPolicyRevisionRequestBody(1, REASON))
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(POLICIES + "/" + FileMocker.MANAGED_POLICY_PREFIX + "MISSING", admin)
            .assertProblem(404, CommonErrorCode.NOT_FOUND);
        post(POLICIES + "/" + FileMocker.MANAGED_POLICY_PREFIX + "MISSING/retirement", admin,
            new RetireScanPolicyRequestBody(1, REASON)).assertProblem(404, CommonErrorCode.NOT_FOUND);

        Map<String, Object> only = fileData.policyRevisions(CODE).getFirst();
        assertAll(() -> assertEquals("ACTIVE", only.get("status")), () -> assertEquals(2, ((Number) only.get("version"))
            .intValue()), () -> assertEquals(104857600L, ((Number) only.get("max_size_bytes")).longValue()));
    }

    /** 没有存储管理权限不能查看；只读权限可以查看不能维护。 */
    @Test
    void test_permissionDenied() {
        Operator viewer = operationsMocker.operator(OperatorBuilder.viewer());
        long role = operationsMocker.customRole("FILE_STORAGE_READER", "FILE_STORAGE_READ");
        Operator reader = operationsMocker.operator(OperatorBuilder.viewer().roles(role));

        get(POLICIES, viewer).assertProblem(403, "OPERATIONS_PERMISSION_DENIED");
        get(POLICIES, reader).assertOk();
        post(POLICIES, reader, create(CODE, List.of(PNG), "1024")).assertProblem(403, "OPERATIONS_PERMISSION_DENIED");
        assertTrue(fileData.policyRevisions(CODE).isEmpty());
    }

    /** 让一条私有写入路由引用本策略，返回其维护路径。 */
    private String referencedByRoute() {
        storageInstances.staticInstance(StorageInstanceMocker.MINIO_INSTANCE, FileStorageType.MINIO,
            StorageInstanceMocker.MINIO_TOKEN, false);
        storageInstances.bucket(StorageInstanceMocker.MINIO_INSTANCE, storage.bucket(), FileStorageBucketStatus.ACTIVE);
        post(BASE + "/routes", admin, new CreateStorageRouteRequestBody(StorageInstanceMocker.SOURCE,
            FileAccessLevel.PRIVATE.getCode(), StorageInstanceMocker.MINIO_INSTANCE, storage.bucket(), CODE, 300, REASON))
            .assertOk();
        return BASE + "/routes/" + StorageInstanceMocker.SOURCE + "/" + FileAccessLevel.PRIVATE.getCode();
    }

    /** 本类代码在列表中的一行。 */
    private static ScanPolicySummary summary(ScanPolicyListResponseBody list) {
        return list.getItems().stream().filter(item -> item.getPolicyCode().equals(CODE)).findFirst().orElseThrow();
    }

    /** 本类代码的详情。 */
    private ScanPolicyDetailResponseBody detail() {
        return get(POLICIES + "/" + CODE, admin).assertOk().as(ScanPolicyDetailResponseBody.class);
    }

    /** 修订号对应的修订主键。 */
    private long revisionId(int revisionNo) {
        return fileData.policyRevisions(CODE).stream()
            .filter(row -> ((Number) row.get("revision_no")).intValue() == revisionNo)
            .map(row -> ((Number) row.get("policy_id")).longValue()).findFirst().orElseThrow();
    }

    /** 修订路径。 */
    private static String revision(int revisionNo) {
        return POLICIES + "/" + CODE + "/revisions/" + revisionNo;
    }

    /** 新建请求。 */
    private static CreateScanPolicyRequestBody create(String code, List<String> types, String maxSize) {
        return new CreateScanPolicyRequestBody(code, types, maxSize, REASON);
    }

    /** 修改草稿请求。 */
    private static UpdateScanPolicyRevisionRequestBody update(List<String> types, String maxSize, int expectedVersion) {
        return new UpdateScanPolicyRevisionRequestBody(types, maxSize, expectedVersion, REASON);
    }

    /** 保存结果的编码、状态与版本。 */
    private static void assertMutation(PlatformResponseChecker checker, String code, String status, int version) {
        var mutation = checker.assertOk().as(ConfigurationMutationResponseBody.class);
        assertAll(() -> assertEquals(code, mutation.getObjectCode()), () -> assertEquals(status, mutation.getStatus()),
            () -> assertEquals(version, mutation.getVersion()));
    }
}
