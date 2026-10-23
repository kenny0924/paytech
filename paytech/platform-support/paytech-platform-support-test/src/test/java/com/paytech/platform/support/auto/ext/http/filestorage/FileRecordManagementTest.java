package com.paytech.platform.support.auto.ext.http.filestorage;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.FileDisposition;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.filestorage.FileStorageManagementApi;
import com.paytech.platform.support.api.filestorage.response.FileRecordDetailResponseBody;
import com.paytech.platform.support.api.filestorage.response.FileRecordSummary;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.core.enums.FileVersionStatus;
import com.paytech.platform.support.filedocument.domain.enums.FileUploadStatus;
import com.paytech.platform.support.filedocument.domain.file.FileSourceCodes;
import com.paytech.platform.support.filedocument.integration.scanning.FormatFileScanner;
import com.paytech.platform.support.test.base.InternalFileApiTestBase;
import com.paytech.platform.support.test.builder.FileBuilder;
import com.paytech.platform.support.test.builder.FileBuilder.Spec;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.dal.AuditTestMapper;
import com.paytech.platform.support.test.mocker.local.FileMocker;
import com.paytech.platform.support.test.mocker.local.FileMocker.Policy;
import com.paytech.platform.support.test.mocker.local.StorageInstanceMocker;

import tools.jackson.core.type.TypeReference;

/**
 * 运营文件记录：按创建时间范围（必填，最长 31 天）与可选条件数据库分页查询；详情返回元数据、存储位置、公开地址、版本历史、检查结果与
 * 上传记录；运营下载当前或历史版本并写 FILE_DOWNLOAD_AUTHORIZE 审计（附 version_no），检查中或被拒绝的版本不能下载。
 * <li>文件 A 经内部接口上传并替换（v2 当前、v1 已取代）；B 检查中；C 位于公开桶且可用；D 有检查中的替换版本（回填式 v1 + 直接插入的
 * 候选 v2）。</li>
 *
 * @see FileStorageManagementApi#listFileRecords(String, String, Instant, Instant, String, String, String, String, String,
 *      Boolean, Integer, Integer)
 * @see FileStorageManagementApi#getFileRecord(String, String, String)
 * @see FileStorageManagementApi#downloadFileRecord(String, String, String, Integer)
 */
class FileRecordManagementTest extends InternalFileApiTestBase {

    private static final String FILES = "/storage-admin/v1/files";

    private static final String PUBLIC_BUCKET = "ut-record-public";

    private static final String PUBLIC_BASE = "https://cdn.example.com/files";

    private static final TypeReference<com.paytech.foundation.web.paging.PagingResponseBody<FileRecordSummary>> PAGE =
        new TypeReference<>() {
        };

    @Autowired
    private AuditTestMapper auditData;

    private Operator admin;

    private Policy policy;

    private long fileA;

    private Spec fileB;

    private Spec fileC;

    private Spec fileD;

    /** 本类公共：测试策略、公开桶登记、平台管理员与四个文件。 */
    @BeforeEach
    void setUpClassMocks() {
        policy = fileMocker.formatPolicy();
        storageInstances.publicBucket(StorageInstanceMocker.MINIO_INSTANCE, PUBLIC_BUCKET, PUBLIC_BASE,
            FileStorageBucketStatus.ACTIVE);
        admin = operationsMocker.operator(OperatorBuilder.administrator());
        fileA = replacedFile();
        fileB = fileMocker.file(material(StorageInstanceMocker.MINIO_INSTANCE).policyId(policy.policyId())
            .scanStatus(FileScanStatus.PENDING_SCAN).version("vb").build());
        fileC = fileMocker.file(FileBuilder.template(StorageInstanceMocker.MINIO_INSTANCE, PUBLIC_BUCKET,
            storage.newObjectRef()).policyId(policy.policyId()).accessLevel(FileAccessLevel.PUBLIC).version("vc").build());
        fileD = fileMocker.file(material(StorageInstanceMocker.MINIO_INSTANCE).policyId(policy.policyId()).version("vd1")
            .build());
        long uploadId = fileMocker.replacement(fileD, 1, "vd1", FileUploadStatus.COMPLETED.getCode(),
            now().plusSeconds(600));
        fileMocker.version(fileD, 2, uploadId, "vd2", FileBuilder.PNG, "image/png", policy.policyId(),
            FileScanStatus.SCANNING, FileVersionStatus.CANDIDATE.getCode());
    }

    /** 时间范围内全部文件按创建时间、文件 ID 倒序；各条件精确筛选；分页保留总数。 */
    @Test
    void test_listAndFilters() {
        var all = list("");
        List<Long> ids = List.of(fileA, fileB.fileId(), fileC.fileId(), fileD.fileId());

        assertAll(() -> assertEquals(4, all.total()), () -> assertEquals(1, all.page()), () -> assertEquals(10, all.size()),
            () -> assertEquals(ids.stream().sorted(java.util.Comparator.reverseOrder()).toList(),
                all.items().stream().map(item -> Long.parseLong(item.getFileId())).toList()));
        FileRecordSummary a = all.items().stream().filter(item -> item.getFileId().equals(Long.toString(fileA)))
            .findFirst().orElseThrow();
        assertAll(() -> assertEquals(FileAddress.of(fileA).value(), a.getFileAddress()),
            () -> assertEquals(StorageInstanceMocker.SOURCE, a.getSourceSystem()),
            () -> assertEquals(REASON, a.getReasonCode()), () -> assertEquals(2, a.getCurrentVersionNo()),
            () -> assertNull(a.getPendingVersionNo()), () -> assertEquals("image/png", a.getMimeType()),
            () -> assertEquals("replacement.png", a.getDisplayName()),
            () -> assertEquals(Integer.toString(FileBuilder.PNG.length), a.getSizeBytes()));

        assertAll(() -> assertEquals(List.of(fileA), idsOf(list("&fileId=" + fileA))),
            () -> assertEquals(List.of(fileA), idsOf(list("&sourceSystem=" + StorageInstanceMocker.SOURCE))),
            () -> assertEquals(3, list("&sourceSystem=" + FileSourceCodes.PLATFORM_SUPPORT).total()),
            () -> assertEquals(List.of(fileA), idsOf(list("&reasonCode=" + REASON))),
            () -> assertEquals(List.of(fileC.fileId()), idsOf(list("&accessLevel=PUBLIC"))),
            () -> assertEquals(List.of(fileB.fileId()), idsOf(list("&scanStatus=PENDING_SCAN"))),
            () -> assertEquals(List.of(fileD.fileId()), idsOf(list("&hasPendingReplacement=true"))),
            () -> assertEquals(3, list("&hasPendingReplacement=false").total()),
            () -> assertEquals(0, list("&accessLevel=PUBLIC&scanStatus=PENDING_SCAN").total()));
        var second = list("&page=2&size=10");
        assertAll(() -> assertTrue(second.items().isEmpty()), () -> assertEquals(4, second.total()),
            () -> assertEquals(2, second.page()));
        var narrow = get(FILES + "?createdFrom=" + now().plusSeconds(1) + "&createdTo=" + now().plusSeconds(60), admin)
            .assertOk().as(PAGE);
        assertEquals(0, narrow.total(), "时间范围外的文件不返回");
    }

    /** 时间范围缺失、颠倒或超过 31 天，条件格式非法，分页条数不在允许值内：400。 */
    @Test
    void test_invalidParameter() {
        Instant from = now().minus(1, ChronoUnit.HOURS);
        get(FILES + "?createdTo=" + now(), admin).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(FILES + "?createdFrom=" + from, admin).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(FILES + "?createdFrom=" + now() + "&createdTo=" + now(), admin)
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        get(FILES + "?createdFrom=" + now().minus(32, ChronoUnit.DAYS) + "&createdTo=" + now(), admin)
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        for (String invalid : List.of("&fileId=0" + fileA, "&accessLevel=public", "&scanStatus=DONE", "&size=7",
            "&sourceSystem=File_Test", "&reasonCode=lower")) {
            list(invalid, 400);
        }
        get(FILES + "/abc", admin).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
    }

    /**
     * 详情：替换过的文件含存储位置、两版本（均可下载）、两条检查结果（策略代码与修订号）与两条上传记录（替换在前）；公开文件返回公开地址；
     * 有检查中替换的文件返回检查中的版本号且候选版本不可下载；不存在 404。
     */
    @Test
    void test_detail() {
        FileRecordDetailResponseBody a = get(FILES + "/" + fileA, admin).assertOk().as(FileRecordDetailResponseBody.class);
        FileRecordDetailResponseBody c = get(FILES + "/" + fileC.fileId(), admin).assertOk()
            .as(FileRecordDetailResponseBody.class);
        FileRecordDetailResponseBody d = get(FILES + "/" + fileD.fileId(), admin).assertOk()
            .as(FileRecordDetailResponseBody.class);

        assertAll(() -> assertEquals(StorageInstanceMocker.MINIO_INSTANCE, a.getInstanceCode()),
            () -> assertEquals(storage.bucket(), a.getBucketName()),
            () -> assertEquals(objectRefOf(fileA), a.getObjectRef()),
            () -> assertEquals(FileBuilder.sha256(FileBuilder.PNG), a.getContentHash()),
            () -> assertEquals("42", a.getUploaderAccountId()),
            () -> assertNull(a.getPublicUrl()),
            () -> assertEquals(List.of(2, 1), a.getVersions().stream().map(v -> v.getVersionNo()).toList()),
            () -> assertEquals(List.of(FileVersionStatus.CURRENT.getCode(), FileVersionStatus.SUPERSEDED.getCode()),
                a.getVersions().stream().map(v -> v.getVersionStatus()).toList()),
            () -> assertTrue(a.getVersions().stream().allMatch(v -> v.isDownloadable())),
            () -> assertEquals("43", a.getVersions().getFirst().getUploaderAccountId()),
            () -> assertEquals(List.of(2, 1), a.getScanResults().stream().map(r -> r.getVersionNo()).toList()),
            () -> assertEquals(FileMocker.POLICY_CODE, a.getScanResults().getFirst().getScanPolicyCode()),
            () -> assertEquals(1, a.getScanResults().getFirst().getRevisionNo()),
            () -> assertEquals(FormatFileScanner.SCANNER_CODE, a.getScanResults().getFirst().getScannerCode()),
            () -> assertEquals(FormatFileScanner.VALID, a.getScanResults().getFirst().getResultCode()),
            () -> assertEquals(List.of("REPLACEMENT", "ORIGINAL"), a.getUploads().stream().map(u -> u.getKind()).toList()),
            () -> assertEquals(List.of(2, 1), a.getUploads().stream().map(u -> u.getResultVersionNo()).toList()),
            () -> assertEquals(List.of(1, 0), a.getUploads().stream().map(u -> u.getReplacementNo()).toList()),
            () -> assertEquals(PUBLIC_BASE + "/" + fileC.objectRef(), c.getPublicUrl()),
            () -> assertEquals(PUBLIC_BASE + "/" + fileC.objectRef() + "?v=1", c.getVersionedUrl()),
            () -> assertEquals(FileAccessLevel.PUBLIC.getCode(), c.getAccessLevel()),
            () -> assertEquals(1, c.getVersions().size(), "回填式 v1 版本行"),
            () -> assertEquals(2, d.getPendingVersionNo()),
            () -> assertFalse(d.getVersions().getFirst().isDownloadable()),
            () -> assertEquals(FileVersionStatus.CANDIDATE.getCode(), d.getVersions().getFirst().getVersionStatus()),
            () -> assertEquals(List.of(2, 1), d.getUploads().stream().map(u -> u.getResultVersionNo()).toList()));
        get(FILES + "/" + OperatorBuilder.nextId(), admin).assertProblem(404, FileErrorCode.FILE_NOT_FOUND);
    }

    /**
     * 下载：缺省为当前版本，指定 versionNo 可下载已取代的版本，都按附件签发并写 FILE_DOWNLOAD_AUTHORIZE 审计（操作人、文件、version_no）；
     * 候选版本 409、不存在的版本 404、检查中的文件 409，失败不写审计。
     */
    @Test
    void test_download() {
        FileDownloadResponseBody current = post(FILES + "/" + fileA + "/download", admin, null).assertOk()
            .as(FileDownloadResponseBody.class);
        String currentRequest = requestId;
        FileDownloadResponseBody previous = post(FILES + "/" + fileA + "/download?versionNo=1", admin, null).assertOk()
            .as(FileDownloadResponseBody.class);
        String previousRequest = requestId;

        assertAll(() -> assertEquals(FileDisposition.ATTACHMENT.getCode(), current.getDisposition()),
            () -> assertArrayEquals(FileBuilder.PNG, storage.browserGet(current.getUrl()).body()),
            () -> assertArrayEquals(FileBuilder.PDF, storage.browserGet(previous.getUrl()).body()));
        var event = auditChecker.assertEvent(currentRequest, "FILE_DOWNLOAD_AUTHORIZE",
            AuditModels.Result.SUCCEEDED.getCode(), admin.actor(), "FILE", Long.toString(fileA));
        var history = auditChecker.assertEvent(previousRequest, "FILE_DOWNLOAD_AUTHORIZE",
            AuditModels.Result.SUCCEEDED.getCode(), admin.actor(), "FILE", Long.toString(fileA));
        assertAll(() -> assertEquals(List.of(Map.entry("version_no", "2")), attributes(event)),
            () -> assertEquals(List.of(Map.entry("version_no", "1")), attributes(history)));
        auditChecker.assertNoSecret(event, current.getUrl());

        long before = auditData.eventCount();
        post(FILES + "/" + fileD.fileId() + "/download?versionNo=2", admin, null)
            .assertProblem(409, FileErrorCode.FILE_STATE_CONFLICT);
        post(FILES + "/" + fileA + "/download?versionNo=9", admin, null).assertProblem(404, FileErrorCode.FILE_NOT_FOUND);
        post(FILES + "/" + fileB.fileId() + "/download", admin, null).assertProblem(409, FileErrorCode.FILE_STATE_CONFLICT);
        post(FILES + "/" + fileA + "/download?versionNo=0", admin, null)
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        assertEquals(before, auditData.eventCount(), "失败的下载不写审计");
    }

    /** 没有文件记录权限不能查看；只有查看权限不能下载。 */
    @Test
    void test_permissionDenied() {
        Operator viewer = operationsMocker.operator(OperatorBuilder.viewer());
        long role = operationsMocker.customRole("FILE_RECORD_READER", "FILE_RECORD_READ");
        Operator reader = operationsMocker.operator(OperatorBuilder.viewer().roles(role));

        get(FILES + "?createdFrom=" + now().minusSeconds(60) + "&createdTo=" + now().plusSeconds(60), viewer)
            .assertProblem(403, "OPERATIONS_PERMISSION_DENIED");
        get(FILES + "/" + fileA, reader).assertOk();
        post(FILES + "/" + fileA + "/download", reader, null).assertProblem(403, "OPERATIONS_PERMISSION_DENIED");
    }

    /** 经内部接口上传 PDF 并替换为 PNG，等待 v2 成为当前版本，返回文件 ID。 */
    private long replacedFile() {
        FileUploadResponseBody upload = start(StorageInstanceMocker.SOURCE, upload(FileAccessLevel.PRIVATE.getCode(),
            FileBuilder.PDF, "application/pdf")).assertStatus(201).as(FileUploadResponseBody.class);
        storage.track(objectRef(upload.getUploadId()));
        put(upload, FileBuilder.PDF);
        long fileId = Long.parseLong(complete(StorageInstanceMocker.SOURCE, upload.getUploadId()).assertOk()
            .as(FileUploadCompletionResponseBody.class).getFileId());
        awaitVersion(fileId, 1, FileVersionStatus.CURRENT);
        FileUploadResponseBody replacement = replace(StorageInstanceMocker.SOURCE, fileId,
            replacement(FileBuilder.PNG, "image/png")).assertStatus(201).as(FileUploadResponseBody.class);
        put(replacement, FileBuilder.PNG);
        complete(StorageInstanceMocker.SOURCE, replacement.getUploadId()).assertOk();
        awaitVersion(fileId, 2, FileVersionStatus.CURRENT);
        return fileId;
    }

    /** 本例时间范围内的列表，附加条件以 & 开头。 */
    private com.paytech.foundation.web.paging.PagingResponseBody<FileRecordSummary> list(String filters) {
        return get(FILES + range() + filters, admin).assertOk().as(PAGE);
    }

    /** 本例时间范围内的列表，期望失败状态。 */
    private void list(String filters, int status) {
        get(FILES + range() + filters, admin).assertProblem(status, CommonErrorCode.INVALID_REQUEST);
    }

    /** 覆盖本例全部文件的创建时间范围。 */
    private String range() {
        return "?createdFrom=" + now().minus(1, ChronoUnit.HOURS) + "&createdTo=" + now().plus(1, ChronoUnit.HOURS);
    }

    /** 文件记录中的对象引用。 */
    private String objectRefOf(long fileId) {
        return (String) fileData.file(fileId).get("storage_object_ref");
    }

    /** 列表中的文件 ID。 */
    private static List<Long> idsOf(com.paytech.foundation.web.paging.PagingResponseBody<FileRecordSummary> page) {
        return page.items().stream().map(item -> Long.parseLong(item.getFileId())).toList();
    }

    /** 审计事件的全部属性（保留顺序）。 */
    private List<Map.Entry<String, String>> attributes(Map<String, Object> event) {
        return auditData.attributes(((Number) event.get("audit_event_id")).longValue()).stream()
            .map(row -> Map.entry((String) row.get("attr_key"), (String) row.get("value_text"))).toList();
    }
}
