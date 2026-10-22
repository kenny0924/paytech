package com.paytech.platform.support.auto.ext.http.files;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.api.enums.FileStorageInstanceStatus;
import com.paytech.platform.support.api.enums.FileDisposition;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.api.files.InternalFileApi;
import com.paytech.platform.support.api.files.request.StartFileUploadRequestBody;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Result;
import com.paytech.platform.support.auditsecurity.domain.enums.AuditActorType;
import com.paytech.platform.support.filedocument.domain.enums.FileUploadStatus;
import com.paytech.platform.support.filedocument.integration.storage.StoredContentType;
import com.paytech.platform.support.service.handler.FileScanHandler;
import com.paytech.platform.support.test.base.FileTestBase;
import com.paytech.platform.support.test.builder.FileBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.dal.AuditTestMapper;
import com.paytech.platform.support.test.dal.FileStorageTestMapper;
import com.paytech.platform.support.test.mocker.local.FileMocker;
import com.paytech.platform.support.test.mocker.local.StorageInstanceMocker;
import com.paytech.platform.support.test.util.InternalTokens;

/**
 * 内部文件接口：调用身份决定来源系统、按来源路由选择存储、完成与扫描、元数据查询、任意内部服务下载，以及多存储并存下读路径不受路由影响；
 * CSV 按内容识别为 text/csv，二进制伪装的 CSV 在完成时被拒。
 * <li>internal-auth.mode=DUAL：合法内部 Token 以 sub 为来源系统；无 Token 时回退 X-Caller-Service；两者都没有返回 401。</li>
 * <li>带 fileId 的响应同时返回 fileAddress；下载带 X-Operator-Account-Id 时写一条人工下载审计；下载方式缺省为附件。</li>
 *
 * @see InternalFileApi#startUpload(StartFileUploadRequestBody)
 * @see InternalFileApi#completeUpload(String)
 * @see InternalFileApi#getFile(String)
 * @see InternalFileApi#authorizeDownload(String, String, String)
 */
@TestPropertySource(properties = "internal-auth.mode=DUAL")
class InternalFileApiTest extends FileTestBase {

    private static final String BASE = "/internal/files/v1";

    private static final String SELF = "platform-support-service";

    private static final String REASON = "WORKFLOW_ATTACHMENT";

    private static final String SOURCE = StorageInstanceMocker.SOURCE;

    private static final String PEER = StorageInstanceMocker.PEER_SOURCE;

    @Autowired
    private InternalTokens tokens;

    @Autowired
    private FileStorageTestMapper storageData;

    @Autowired
    private AuditTestMapper auditData;

    private long policyId;

    /** 本类公共：测试扫描策略（默认路由冻结的策略）。 */
    @BeforeEach
    void setUpClassMocks() {
        policyId = fileMocker.formatPolicy().policyId();
    }

    /** 无调用身份返回 401，不登记上传；来源系统只取调用身份，请求体无法指定。 */
    @Test
    void test_callerIdentity() {
        start(null, null, body()).assertProblem(401, CommonErrorCode.INTERNAL_TOKEN_INVALID);
        assertTrue(storageData.uploadsFrom(SOURCE).isEmpty());

        String token = tokens.issue(SOURCE, SELF);
        FileUploadResponseBody upload = start(null, token, body()).assertStatus(201).as(FileUploadResponseBody.class);

        Map<String, Object> row = onlyUpload(SOURCE);
        storage.track((String) row.get("storage_object_ref"));
        assertAll(() -> assertEquals(upload.getUploadId(), row.get("upload_id").toString()),
            () -> assertEquals(REASON, row.get("reason_code")),
            () -> assertNull(row.get("tenant_id")),
            () -> assertEquals(42L, ((Number) row.get("uploader_id")).longValue()));
    }

    /** 来源系统有精确路由时写入其桶，其他来源走默认路由；签发的上传期限取路由。 */
    @Test
    void test_routeBySource() {
        storageInstances.route(SOURCE, StorageInstanceMocker.S3_INSTANCE, FileMocker.POLICY_CODE);
        storageInstances.refresh();

        FileUploadResponseBody routed = start(SOURCE, null, body()).assertStatus(201).as(FileUploadResponseBody.class);
        start(PEER, null, body()).assertStatus(201);

        Map<String, Object> sourceRow = onlyUpload(SOURCE);
        Map<String, Object> peerRow = onlyUpload(PEER);
        storage.track((String) sourceRow.get("storage_object_ref"));
        storage.track((String) peerRow.get("storage_object_ref"));
        assertAll(() -> assertEquals(StorageInstanceMocker.S3_INSTANCE, sourceRow.get("storage_provider")),
            () -> assertEquals(StorageInstanceMocker.MINIO_INSTANCE, peerRow.get("storage_provider")),
            () -> assertEquals(now().plusSeconds(StorageInstanceMocker.UPLOAD_SECONDS), routed.getExpiresAt()),
            () -> assertEquals("https", URI.create(routed.getAuthorization().getUrl()).getScheme()));
    }

    /** 上传、完成、扫描、查询与任意内部服务下载；回执与元数据带文件地址，重复完成返回原回执，无操作人的下载不写审计。 */
    @Test
    void test_normalFlow() {
        String uploadId = uploadThroughGrant(SOURCE);

        FileUploadCompletionResponseBody receipt =
            complete(SOURCE, uploadId).assertOk().as(FileUploadCompletionResponseBody.class);
        String fileId = receipt.getFileId();
        awaitAvailable(fileId);
        FileUploadCompletionResponseBody repeated =
            complete(SOURCE, uploadId).assertOk().as(FileUploadCompletionResponseBody.class);

        StoredFileResponseBody file = call(HttpMethod.GET, BASE + "/files/" + fileId, PEER, null).assertOk()
            .as(StoredFileResponseBody.class);
        FileDownloadResponseBody download = call(HttpMethod.POST, BASE + "/files/" + fileId + "/download", PEER, null)
            .assertOk().as(FileDownloadResponseBody.class);
        String address = FileAddress.of(Long.parseLong(fileId)).value();
        assertAll(() -> assertEquals(fileId, repeated.getFileId()),
            () -> assertEquals(address, receipt.getFileAddress()),
            () -> assertEquals(address, repeated.getFileAddress()),
            () -> assertEquals(address, file.getFileAddress()),
            () -> assertEquals(fileId, file.getFileId()),
            () -> assertEquals(receipt.getCompletedAt(), repeated.getCompletedAt()),
            () -> assertEquals(SOURCE, file.getSourceSystem()),
            () -> assertEquals(REASON, file.getReasonCode()),
            () -> assertEquals("42", file.getUploaderAccountId()),
            () -> assertEquals("contract.pdf", file.getDisplayName()),
            () -> assertEquals(FileBuilder.sha256(FileBuilder.PDF), file.getContentHash()),
            () -> assertEquals(Integer.toString(FileBuilder.PDF.length), file.getSizeBytes()),
            () -> assertEquals(FileScanStatus.AVAILABLE.getCode(), file.getScanStatus()),
            () -> assertEquals(0, auditData.eventCount()),
            () -> assertTrue(download.getExpiresAt().isAfter(now())));
        assertArrayEquals(FileBuilder.PDF, storage.browserGet(download.getUrl()).body());
    }

    /** 声明为 text/csv 的 UTF-8 CSV：完成时按内容识别为 text/csv，格式检查通过后文件可用，元数据类型为 text/csv。 */
    @Test
    void test_normalFlow_csv() {
        String uploadId = uploadThroughGrant(SOURCE, "bin-2026-10.csv", StoredContentType.CSV, FileBuilder.CSV);

        String fileId = complete(SOURCE, uploadId).assertOk().as(FileUploadCompletionResponseBody.class).getFileId();
        awaitAvailable(fileId);

        StoredFileResponseBody file = call(HttpMethod.GET, BASE + "/files/" + fileId, PEER, null).assertOk()
            .as(StoredFileResponseBody.class);
        assertAll(() -> assertEquals(StoredContentType.CSV, file.getMimeType()),
            () -> assertEquals("bin-2026-10.csv", file.getDisplayName()),
            () -> assertEquals(FileBuilder.sha256(FileBuilder.CSV), file.getContentHash()));
    }

    /** 二进制内容伪装成 text/csv：完成上传时识别类型不符，422 拒绝，不登记文件。 */
    @Test
    void test_businessRuleViolation_binaryDisguisedAsCsv() {
        String uploadId = uploadThroughGrant(SOURCE, "bin.csv", StoredContentType.CSV, FileBuilder.BINARY);

        complete(SOURCE, uploadId).assertProblem(422, FileErrorCode.FILE_POLICY_REJECTED);

        assertAll(() -> assertEquals(FileUploadStatus.CREATED.getCode(),
            fileData.uploadRow(Long.parseLong(uploadId)).get("status")),
            () -> assertTrue(fileData.filesOfUpload(Long.parseLong(uploadId)).isEmpty(), "不登记文件"));
    }

    /**
     * 其他来源系统不能完成本来源的上传；扫描未通过或对象不可用的文件不能下载；不存在的上传与文件返回 404；纯系统上传查询时上传人为空。
     */
    @Test
    void test_businessRuleViolation() {
        String uploadId = uploadThroughGrant(SOURCE, null);

        complete(PEER, uploadId).assertProblem(404, FileErrorCode.FILE_NOT_FOUND);
        assertEquals(FileUploadStatus.CREATED.getCode(), fileData.uploadRow(Long.parseLong(uploadId)).get("status"));

        String fileId = complete(SOURCE, uploadId).assertOk().as(FileUploadCompletionResponseBody.class).getFileId();
        fileMocker.awaitScanTasks(platformDataCleaner);
        call(HttpMethod.GET, BASE + "/files/1", PEER, null).assertProblem(404, FileErrorCode.FILE_NOT_FOUND);
        call(HttpMethod.GET, BASE + "/files/0" + fileId, PEER, null).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        download("1").assertProblem(404, FileErrorCode.FILE_NOT_FOUND);
        complete(SOURCE, Long.toString(OperatorBuilder.nextId()))
            .assertProblem(404, FileErrorCode.FILE_NOT_FOUND);
        StoredFileResponseBody stored =
            call(HttpMethod.GET, BASE + "/files/" + fileId, PEER, null).assertOk().as(StoredFileResponseBody.class);
        assertAll(() -> assertEquals(FileScanStatus.AVAILABLE.getCode(), stored.getScanStatus()),
            () -> assertNull(stored.getUploaderAccountId()));

        var pending = fileMocker.file(material(StorageInstanceMocker.MINIO_INSTANCE).policyId(policyId)
            .scanStatus(FileScanStatus.PENDING_SCAN).version("v1").build());
        download(Long.toString(pending.fileId())).assertProblem(409, FileErrorCode.FILE_STATE_CONFLICT);
        var unavailable = fileMocker.file(material(StorageInstanceMocker.MINIO_INSTANCE).policyId(policyId)
            .objectStatus(FileObjectStatus.UNAVAILABLE).version("v2").build());
        download(Long.toString(unavailable.fileId())).assertProblem(409, FileErrorCode.FILE_STATE_CONFLICT);
    }

    /**
     * 下载方式：缺省与空值按附件；PDF 请求 INLINE 时内联并签入登记的 Content-Type；不在内联名单内的 MIME 静默降为附件；
     * 未知值返回 400。地址都签入原文件名。
     */
    @Test
    void test_downloadDisposition() {
        var pdf = fileMocker.file(material(StorageInstanceMocker.MINIO_INSTANCE).policyId(policyId)
            .displayName("营业执照.pdf").version("v1").build());
        var gif = fileMocker.file(material(StorageInstanceMocker.MINIO_INSTANCE).policyId(policyId).mime("image/gif")
            .displayName("logo.gif").version("v2").build());
        String pdfPath = BASE + "/files/" + pdf.fileId() + "/download";

        FileDownloadResponseBody byDefault = call(HttpMethod.POST, pdfPath, PEER, null).assertOk()
            .as(FileDownloadResponseBody.class);
        FileDownloadResponseBody empty = call(HttpMethod.POST, pdfPath + "?disposition=", PEER, null).assertOk()
            .as(FileDownloadResponseBody.class);
        FileDownloadResponseBody inline = call(HttpMethod.POST, pdfPath + "?disposition=INLINE", PEER, null).assertOk()
            .as(FileDownloadResponseBody.class);
        FileDownloadResponseBody downgraded = call(HttpMethod.POST,
            BASE + "/files/" + gif.fileId() + "/download?disposition=INLINE", PEER, null).assertOk()
            .as(FileDownloadResponseBody.class);

        assertAll(() -> assertEquals(FileDisposition.ATTACHMENT.getCode(), byDefault.getDisposition()),
            () -> assertEquals(FileDisposition.ATTACHMENT.getCode(), empty.getDisposition()),
            () -> assertEquals(FileDisposition.INLINE.getCode(), inline.getDisposition()),
            () -> assertEquals(FileDisposition.ATTACHMENT.getCode(), downgraded.getDisposition()),
            () -> assertEquals("attachment; filename=\"____.pdf\"; filename*=UTF-8''%E8%90%A5%E4%B8%9A%E6%89%A7%E7%85%A7.pdf",
                queryParam(byDefault.getUrl(), "response-content-disposition")),
            () -> assertNull(queryParam(byDefault.getUrl(), "response-content-type")),
            () -> assertEquals("inline; filename=\"____.pdf\"; filename*=UTF-8''%E8%90%A5%E4%B8%9A%E6%89%A7%E7%85%A7.pdf",
                queryParam(inline.getUrl(), "response-content-disposition")),
            () -> assertEquals("application/pdf", queryParam(inline.getUrl(), "response-content-type")),
            () -> assertEquals("attachment; filename=\"logo.gif\"; filename*=UTF-8''logo.gif",
                queryParam(downgraded.getUrl(), "response-content-disposition")),
            () -> assertNull(queryParam(downgraded.getUrl(), "response-content-type")));

        for (String invalid : List.of("inline", "DOWNLOAD", " INLINE")) {
            call(HttpMethod.POST, pdfPath + "?disposition=" + java.net.URLEncoder.encode(invalid,
                java.nio.charset.StandardCharsets.UTF_8), PEER, null).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        }
    }

    /**
     * 人工操作人下载：签发成功后写一条 FILE_DOWNLOAD_AUTHORIZE 审计，主体为操作人账号，资源为文件，属性只含调用服务，不含下载地址；
     * 操作人格式不合法返回 400 且不签发；文件状态不允许时不写审计。
     */
    @Test
    void test_operatorDownloadAudit() {
        var file = fileMocker.file(material(StorageInstanceMocker.MINIO_INSTANCE).policyId(policyId).version("v1").build());
        String fileId = Long.toString(file.fileId());
        String requestId = "plt-file-" + UUID.randomUUID();

        FileDownloadResponseBody download = download(fileId, "880000000901", requestId).assertOk()
            .as(FileDownloadResponseBody.class);

        var event = auditChecker.assertEvent(requestId, "FILE_DOWNLOAD_AUTHORIZE", Result.SUCCEEDED.getCode(),
            "880000000901", "FILE", fileId);
        long eventId = ((Number) event.get("audit_event_id")).longValue();
        var attributes = auditData.attributes(eventId);
        assertAll(() -> assertEquals(AuditActorType.USER.getCode(), event.get("actor_type")),
            () -> assertEquals(1, attributes.size()),
            () -> assertEquals("caller_service", attributes.getFirst().get("attr_key")),
            () -> assertEquals(PEER, attributes.getFirst().get("value_text")));
        auditChecker.assertNoSecret(event, download.getUrl());

        for (String invalid : List.of("0", "0880000000901", "abc", "-1", "99999999999999999999")) {
            download(fileId, invalid, "plt-file-" + UUID.randomUUID())
                .assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        }
        var pending = fileMocker.file(material(StorageInstanceMocker.MINIO_INSTANCE).policyId(policyId)
            .scanStatus(FileScanStatus.PENDING_SCAN).version("v2").build());
        download(Long.toString(pending.fileId()), "880000000901", "plt-file-" + UUID.randomUUID())
            .assertProblem(409, FileErrorCode.FILE_STATE_CONFLICT);
        assertEquals(1, auditData.eventCount());
    }

    /**
     * 写入目标不可用时开始上传返回 503 且不登记上传：来源路由指向的桶已不存在、默认路由所指实例未启用（均为数据不一致），
     * 以及没有任何路由。
     */
    @Test
    void test_routeUnavailable() {
        assertEquals(1, storageData.insertRoute(OperatorBuilder.nextId(), SOURCE, OperatorBuilder.nextId(),
            FileMocker.POLICY_CODE, StorageInstanceMocker.UPLOAD_SECONDS, now()));
        storageInstances.refresh();
        start(SOURCE, null, body()).assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE);

        storageData.deleteRoute(SOURCE);
        storageInstances.instanceStatus(StorageInstanceMocker.MINIO_INSTANCE, FileStorageInstanceStatus.DISABLED);
        start(SOURCE, null, body()).assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE);

        storageData.deleteRoute(StorageInstanceMocker.DEFAULT_ROUTE);
        storageInstances.instanceStatus(StorageInstanceMocker.MINIO_INSTANCE, FileStorageInstanceStatus.ACTIVE);
        start(PEER, null, body()).assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE);

        assertAll(() -> assertTrue(storageData.uploadsFrom(SOURCE).isEmpty()),
            () -> assertTrue(storageData.uploadsFrom(PEER).isEmpty()));
    }

    /** 参数非法与策略不允许的类型、大小在登记前拒绝。 */
    @Test
    void test_invalidParameter() {
        start(SOURCE, null, new StartFileUploadRequestBody("workflow", "a.pdf", "application/pdf", "10", null))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("reasonCode");
        start(SOURCE, null, new StartFileUploadRequestBody(REASON, " .. ", "application/pdf", "10", null))
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        start(SOURCE, null, new StartFileUploadRequestBody(REASON, ".", "application/pdf", "10", null))
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        start(SOURCE, null, new StartFileUploadRequestBody(REASON, "a".repeat(252) + ".pdf", "application/pdf", "10",
            null)).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        start(SOURCE, null, new StartFileUploadRequestBody(REASON, "a.txt", "text/plain", "10", null))
            .assertProblem(422, FileErrorCode.FILE_POLICY_REJECTED);
        start(SOURCE, null, new StartFileUploadRequestBody(REASON, "a.pdf", "application/pdf", "10485761", null))
            .assertProblem(422, FileErrorCode.FILE_POLICY_REJECTED);

        assertTrue(storageData.uploadsFrom(SOURCE).isEmpty());
    }

    /**
     * 多存储并存：路由从 MINIO 实例改到 S3 实例后，旧上传仍按记录的 MINIO 实例完成、检查与下载，新上传写入 S3；
     * 记录的桶只读时仍可读，停用桶或实例时下载返回 503 且不读取密钥；记录的实例未登记（数据不一致）同样返回 503。
     */
    @Test
    void test_multipleStorages() {
        String uploadId = uploadThroughGrant(SOURCE);
        storageInstances.route(SOURCE, StorageInstanceMocker.S3_INSTANCE, FileMocker.POLICY_CODE);
        storageInstances.refresh();

        String fileId = complete(SOURCE, uploadId).assertOk().as(FileUploadCompletionResponseBody.class).getFileId();
        awaitAvailable(fileId);
        String next = uploadThroughGrant(SOURCE);
        assertEquals(StorageInstanceMocker.S3_INSTANCE, fileData.uploadRow(Long.parseLong(next)).get("storage_provider"));

        storageInstances.bucketStatus(StorageInstanceMocker.MINIO_INSTANCE, FileStorageBucketStatus.READ_ONLY);
        download(fileId).assertOk();
        int reveals = storageVault.revealCount(StorageInstanceMocker.MINIO_TOKEN);

        storageInstances.bucketStatus(StorageInstanceMocker.MINIO_INSTANCE, FileStorageBucketStatus.DISABLED);
        download(fileId).assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE);
        storageInstances.bucketStatus(StorageInstanceMocker.MINIO_INSTANCE, FileStorageBucketStatus.ACTIVE);
        storageInstances.instanceStatus(StorageInstanceMocker.MINIO_INSTANCE, FileStorageInstanceStatus.DISABLED);
        download(fileId).assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE);
        assertEquals(reveals, storageVault.revealCount(StorageInstanceMocker.MINIO_TOKEN), "停用后不读取密钥");

        var unregistered = fileMocker.file(material("UT_UNREGISTERED").policyId(policyId).version("v3").build());
        download(Long.toString(unregistered.fileId())).assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    /** Vault 不可用时 STATIC 实例的上传签发返回 503；上传记录已登记但不返回授权。 */
    @Test
    void test_vaultUnavailable() {
        storageVault.revealUnavailable();

        start(SOURCE, null, body()).assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE);

        storage.track((String) onlyUpload(SOURCE).get("storage_object_ref"));
    }

    /** 开始上传并以浏览器身份按授权直传 PDF，返回上传 ID。 */
    private String uploadThroughGrant(String caller) {
        return uploadThroughGrant(caller, "42");
    }

    /** 指定上传人（可空）开始上传并直传 PDF，返回上传 ID。 */
    private String uploadThroughGrant(String caller, String uploaderAccountId) {
        return uploadThroughGrant(caller, "contract.pdf", "application/pdf", FileBuilder.PDF, uploaderAccountId);
    }

    /** 以声明的文件名与类型开始上传并直传给定字节，上传人 42，返回上传 ID。 */
    private String uploadThroughGrant(String caller, String filename, String mimeType, byte[] bytes) {
        return uploadThroughGrant(caller, filename, mimeType, bytes, "42");
    }

    /** 以声明的文件名、类型与上传人开始上传并按授权直传给定字节，返回上传 ID。 */
    private String uploadThroughGrant(String caller, String filename, String mimeType, byte[] bytes,
        String uploaderAccountId) {
        FileUploadResponseBody upload = start(caller, null, new StartFileUploadRequestBody(REASON, filename, mimeType,
            Integer.toString(bytes.length), uploaderAccountId)).assertStatus(201).as(FileUploadResponseBody.class);
        storage.track((String) fileData.uploadRow(Long.parseLong(upload.getUploadId())).get("storage_object_ref"));
        var authorization = upload.getAuthorization();
        assertEquals(200, storage.browserPut(authorization.getUrl(), authorization.getHeaders(), bytes).statusCode());
        return upload.getUploadId();
    }

    /** 有界等待扫描任务把文件推进为 AVAILABLE。 */
    private void awaitAvailable(String fileId) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50)).until(
            () -> FileScanStatus.AVAILABLE.getCode().equals(fileData.file(Long.parseLong(fileId)).get("scan_status")));
        await().atMost(Duration.ofSeconds(15)).until(() -> fileData.activeTasks(FileScanHandler.EVENT_TYPE) == 0);
    }

    /** 来源系统唯一的上传行。 */
    private Map<String, Object> onlyUpload(String source) {
        List<Map<String, Object>> rows = storageData.uploadsFrom(source);
        assertEquals(1, rows.size(), "应恰好登记一个上传");
        return rows.getFirst();
    }

    /** 默认请求：PDF、上传人 42。 */
    private static StartFileUploadRequestBody body() {
        return new StartFileUploadRequestBody(REASON, "contract.pdf", "application/pdf",
            Integer.toString(FileBuilder.PDF.length), "42");
    }

    /** 开始上传。 */
    private PlatformResponseChecker start(String caller, String token, StartFileUploadRequestBody body) {
        return call(HttpMethod.POST, BASE + "/uploads", caller, token, body);
    }

    /** 完成上传。 */
    private PlatformResponseChecker complete(String caller, String uploadId) {
        return call(HttpMethod.POST, BASE + "/uploads/" + uploadId + "/completion", caller, null);
    }

    /** 解码预签名地址中的一个 Query 参数；不存在为空。 */
    private static String queryParam(String url, String name) {
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            if (java.net.URLDecoder.decode(parts[0], java.nio.charset.StandardCharsets.UTF_8).equals(name)) {
                return parts.length > 1 ? java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8) : "";
            }
        }
        return null;
    }

    /** 以对端身份签发下载。 */
    private PlatformResponseChecker download(String fileId) {
        return call(HttpMethod.POST, BASE + "/files/" + fileId + "/download", PEER, null);
    }

    /** 以对端身份并带人工操作人签发下载；请求 ID 由调用方指定，便于定位审计。 */
    private PlatformResponseChecker download(String fileId, String operatorAccountId, String requestId) {
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Caller-Service", PEER);
        headers.set(InternalFileApi.OPERATOR_ACCOUNT_HEADER, operatorAccountId);
        return new PlatformResponseChecker(httpTestInvoker.exchange(HttpMethod.POST,
            BASE + "/files/" + fileId + "/download", headers, null), objectMapper, requestId);
    }

    /** 以 X-Caller-Service 身份调用。 */
    private PlatformResponseChecker call(HttpMethod method, String path, String caller, Object body) {
        return call(method, path, caller, null, body);
    }

    /** 调用内部接口；caller 为空不带 X-Caller-Service，token 为空不带 Authorization。 */
    private PlatformResponseChecker call(HttpMethod method, String path, String caller, String token, Object body) {
        String requestId = "plt-file-" + UUID.randomUUID();
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        if (caller != null) {
            headers.set("X-Caller-Service", caller);
        }
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new PlatformResponseChecker(httpTestInvoker.exchange(method, path, headers, body), objectMapper,
            requestId);
    }
}
