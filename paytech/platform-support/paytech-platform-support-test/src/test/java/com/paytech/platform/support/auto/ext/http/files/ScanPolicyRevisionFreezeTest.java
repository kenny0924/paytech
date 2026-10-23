package com.paytech.platform.support.auto.ext.http.files;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.api.files.InternalFileApi;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.filestorage.FileStorageManagementApi;
import com.paytech.platform.support.api.filestorage.request.ActivateScanPolicyRevisionRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateScanPolicyRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateScanPolicyRevisionRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateStorageRouteRequestBody;
import com.paytech.platform.support.api.filestorage.request.RetireScanPolicyRequestBody;
import com.paytech.platform.support.api.filestorage.request.UpdateScanPolicyRevisionRequestBody;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.core.enums.FileVersionStatus;
import com.paytech.platform.support.filedocument.integration.storage.StoredContentType;
import com.paytech.platform.support.test.base.InternalFileApiTestBase;
import com.paytech.platform.support.test.builder.FileBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.mocker.local.FileMocker;
import com.paytech.platform.support.test.mocker.local.StorageInstanceMocker;

/**
 * 检查策略修订冻结（AC-14）：上传在开始时冻结写入路由所引用策略当时 ACTIVE 的修订，之后激活新修订不影响已开始的上传，只影响之后
 * 的新上传；被路由引用的策略不能停用
 * <li>策略、修订与路由都经运营存储管理接口维护，上传经内部文件接口，检查经真实 FILE_SCAN 任务。</li>
 * <li>修订 1 只允许 PDF，修订 2 只允许 PNG：已开始的 PDF 上传在修订 2 激活后仍能完成并按修订 1 检查通过。</li>
 *
 * @see InternalFileApi#startUpload(com.paytech.platform.support.api.files.request.StartFileUploadRequestBody)
 * @see InternalFileApi#completeUpload(String)
 * @see FileStorageManagementApi#activateScanPolicyRevision(String, String, String, int, ActivateScanPolicyRevisionRequestBody)
 * @see FileStorageManagementApi#retireScanPolicy(String, String, String, RetireScanPolicyRequestBody)
 */
class ScanPolicyRevisionFreezeTest extends InternalFileApiTestBase {

    private static final String CODE = FileMocker.MANAGED_POLICY_PREFIX + "FREEZE";

    private static final String POLICIES = "/storage-admin/v1/scan-policies";

    private static final String CHANGE_REASON = "修订冻结测试";

    private static final String PDF = StoredContentType.PDF_TYPE;

    private static final String PNG = StoredContentType.PNG_TYPE;

    private Operator admin;

    /** 本类公共：默认路由的测试策略、平台管理员；新建本类策略（修订 1 只允许 PDF）并激活，来源系统的私有路由引用它。 */
    @BeforeEach
    void setUpClassMocks() {
        fileMocker.formatPolicy();
        admin = operationsMocker.operator(OperatorBuilder.administrator());
        post(POLICIES, admin, new CreateScanPolicyRequestBody(CODE, List.of(PDF), "1048576", CHANGE_REASON)).assertOk();
        post(revision(1) + "/activation", admin, new ActivateScanPolicyRevisionRequestBody(1, CHANGE_REASON)).assertOk();
        post("/storage-admin/v1/routes", admin, new CreateStorageRouteRequestBody(StorageInstanceMocker.SOURCE,
            FileAccessLevel.PRIVATE.getCode(), StorageInstanceMocker.MINIO_INSTANCE, storage.bucket(), CODE, 600,
            CHANGE_REASON)).assertOk();
    }

    /**
     * 修订 1 下开始 PDF 上传 → 新建、修改并激活修订 2（只允许 PNG，修订 1 改为 RETIRED）→ 完成上传：文件、v1 版本与检查结果都冻结修订 1，
     * 检查通过；之后新开始的 PDF 上传按修订 2 被拒绝，PNG 上传冻结修订 2 并按修订 2 检查通过。
     */
    @Test
    void test_normalFlow() {
        FileUploadResponseBody started = start(StorageInstanceMocker.SOURCE, upload(null, FileBuilder.PDF, PDF))
            .assertStatus(201).as(FileUploadResponseBody.class);
        storage.track(objectRef(started.getUploadId()));
        long first = revisionId(1);
        assertEquals(first, policyOf(started.getUploadId()), "开始上传冻结当时 ACTIVE 的修订 1");

        post(POLICIES + "/" + CODE + "/revisions", admin, new CreateScanPolicyRevisionRequestBody(CHANGE_REASON))
            .assertOk();
        put(revision(2), admin, new UpdateScanPolicyRevisionRequestBody(List.of(PNG), "1048576", 1, CHANGE_REASON))
            .assertOk();
        post(revision(2) + "/activation", admin, new ActivateScanPolicyRevisionRequestBody(2, CHANGE_REASON)).assertOk();
        long second = revisionId(2);
        assertEquals("RETIRED", status(1));

        put(started, FileBuilder.PDF);
        long fileId = Long.parseLong(complete(StorageInstanceMocker.SOURCE, started.getUploadId()).assertOk()
            .as(FileUploadCompletionResponseBody.class).getFileId());
        awaitVersion(fileId, 1, FileVersionStatus.CURRENT);

        List<Map<String, Object>> results = fileData.results(fileId);
        assertAll(() -> assertEquals(first, policyOf(started.getUploadId())),
            () -> assertEquals(first, ((Number) version(fileId, 1).get("scan_policy_id")).longValue()),
            () -> assertEquals(available(), scanStatus(fileId)), () -> assertEquals(1, results.size()),
            () -> assertEquals(first, ((Number) results.getFirst().get("scan_policy_id")).longValue(),
                "按冻结的修订 1 检查"));

        start(StorageInstanceMocker.SOURCE, upload(null, FileBuilder.PDF, PDF))
            .assertProblem(422, FileErrorCode.FILE_POLICY_REJECTED);
        FileUploadResponseBody later = start(StorageInstanceMocker.SOURCE, upload(null, FileBuilder.PNG, PNG))
            .assertStatus(201).as(FileUploadResponseBody.class);
        storage.track(objectRef(later.getUploadId()));
        assertEquals(second, policyOf(later.getUploadId()), "新上传冻结修订 2");
        put(later, FileBuilder.PNG);
        long laterFile = Long.parseLong(complete(StorageInstanceMocker.SOURCE, later.getUploadId()).assertOk()
            .as(FileUploadCompletionResponseBody.class).getFileId());
        awaitVersion(laterFile, 1, FileVersionStatus.CURRENT);
        assertEquals(second, ((Number) fileData.results(laterFile).getFirst().get("scan_policy_id")).longValue());
    }

    /** 被写入路由引用的策略不能停用：400，ACTIVE 修订保持不变。 */
    @Test
    void test_businessRuleViolation() {
        post(POLICIES + "/" + CODE + "/retirement", admin, new RetireScanPolicyRequestBody(2, CHANGE_REASON))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        assertAll(() -> assertEquals("ACTIVE", status(1)),
            () -> assertEquals(1, fileData.policyRevisions(CODE).size(), "没有产生新修订"));
    }

    /** 修订路径。 */
    private static String revision(int revisionNo) {
        return POLICIES + "/" + CODE + "/revisions/" + revisionNo;
    }

    /** 修订号对应的修订主键。 */
    private long revisionId(int revisionNo) {
        return ((Number) revisionRow(revisionNo).get("policy_id")).longValue();
    }

    /** 修订号对应的修订状态。 */
    private String status(int revisionNo) {
        return (String) revisionRow(revisionNo).get("status");
    }

    /** 修订号对应的修订行。 */
    private Map<String, Object> revisionRow(int revisionNo) {
        return fileData.policyRevisions(CODE).stream()
            .filter(row -> ((Number) row.get("revision_no")).intValue() == revisionNo).findFirst().orElseThrow();
    }

    /** 上传冻结的修订主键。 */
    private long policyOf(String uploadId) {
        return ((Number) fileData.uploadRow(Long.parseLong(uploadId)).get("scan_policy_id")).longValue();
    }
}
