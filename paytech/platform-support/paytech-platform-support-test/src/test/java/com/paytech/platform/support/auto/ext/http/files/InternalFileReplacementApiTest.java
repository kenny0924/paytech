package com.paytech.platform.support.auto.ext.http.files;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.api.files.InternalFileApi;
import com.paytech.platform.support.api.files.request.StartFileReplacementRequestBody;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.core.enums.FileVersionStatus;
import com.paytech.platform.support.filedocument.domain.enums.FileScanType;
import com.paytech.platform.support.filedocument.domain.enums.FileUploadStatus;
import com.paytech.platform.support.filedocument.domain.file.FileSourceCodes;
import com.paytech.platform.support.filedocument.integration.scanning.FormatFileScanner;
import com.paytech.platform.support.service.handler.FilePublicSyncHandler;
import com.paytech.platform.support.service.processor.files.CompleteFileUploadProcessor;
import com.paytech.platform.support.service.processor.files.StartFileReplacementProcessor;
import com.paytech.platform.support.service.processor.files.request.CompleteFileUploadRequest;
import com.paytech.platform.support.service.processor.files.request.StartFileReplacementRequest;
import com.paytech.platform.support.test.base.InternalFileApiTestBase;
import com.paytech.platform.support.test.builder.FileBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.FileMocker;
import com.paytech.platform.support.test.mocker.local.FileMocker.Policy;
import com.paytech.platform.support.test.mocker.local.StorageInstanceMocker;

/**
 * 同地址替换文件内容：文件 ID、文件地址与公开地址不变，新内容作为候选版本单独检查；私有文件检查通过前下载旧版本，公开文件的公开地址
 * 在 PUT 后即读到新内容，检查不通过或替换作废时由公开同步任务恢复。
 * <li>只有原来源系统可以替换；不能改变访问级别；同一文件同时只允许一个未结束的替换（上传未完成或候选检查中），并发开始返回 409。</li>
 * <li>完成要求存储中比开始时新的版本恰好一个：没有上传返回 409 且不改状态，多余写入作废本次替换；过期后不能完成。</li>
 *
 * @see InternalFileApi#startReplacement(String, StartFileReplacementRequestBody)
 * @see InternalFileApi#completeUpload(String)
 * @see InternalFileApi#getFile(String)
 * @see StartFileReplacementProcessor#process(StartFileReplacementRequest)
 * @see FilePublicSyncHandler#handle(CompensationEvent)
 */
class InternalFileReplacementApiTest extends InternalFileApiTestBase {

    private static final String SOURCE = StorageInstanceMocker.SOURCE;

    private static final String PEER = StorageInstanceMocker.PEER_SOURCE;

    private static final String PUBLIC = FileAccessLevel.PUBLIC.getCode();

    private static final String PRIVATE = FileAccessLevel.PRIVATE.getCode();

    @Autowired
    private StartFileReplacementProcessor replacementProcessor;

    @Autowired
    private CompleteFileUploadProcessor completeProcessor;

    @Autowired
    private FilePublicSyncHandler syncHandler;

    private Policy policy;

    private String publicBucket;

    /** 本类公共：测试扫描策略；公开临时桶、管理员实例与来源系统的公开路由。 */
    @BeforeEach
    void setUpClassMocks() {
        policy = fileMocker.formatPolicy();
        publicBucket = tempBuckets.publicReadable();
        storageInstances.rootInstance();
        storageInstances.publicBucket(StorageInstanceMocker.ROOT_INSTANCE, publicBucket,
            tempBuckets.publicBaseUrl(publicBucket), FileStorageBucketStatus.ACTIVE);
        storageInstances.route(SOURCE, FileAccessLevel.PUBLIC, StorageInstanceMocker.ROOT_INSTANCE, publicBucket,
            FileMocker.POLICY_CODE);
        storageInstances.refresh();
    }

    /**
     * 私有文件替换：只签 Content-Type 的 PUT 授权写到原对象名；完成返回新版本号、原文件 ID 与地址，重复完成返回原回执；检查通过后
     * 当前版本变为 v2，原版本 SUPERSEDED，下载得到新内容，存储保留两个版本。
     */
    @Test
    void test_normalFlow() {
        long fileId = availableFile(PRIVATE, FileBuilder.PDF, "application/pdf");
        String baseVersion = (String) fileData.file(fileId).get("object_version");

        FileUploadResponseBody replacement = replace(SOURCE, fileId, replacement(FileBuilder.PNG, "image/png"))
            .assertStatus(201).as(FileUploadResponseBody.class);
        Map<String, Object> row = fileData.uploadRow(Long.parseLong(replacement.getUploadId()));
        put(replacement, FileBuilder.PNG);
        var receipt = complete(SOURCE, replacement.getUploadId()).assertOk().as(FileUploadCompletionResponseBody.class);
        awaitVersion(fileId, 2, FileVersionStatus.CURRENT);
        var repeated = complete(SOURCE, replacement.getUploadId()).assertOk().as(FileUploadCompletionResponseBody.class);

        StoredFileResponseBody stored = describe(PEER, fileId);
        assertAll(() -> assertEquals(Map.of("content-type", "image/png"), lowerKeys(replacement.getAuthorization()
                .getHeaders()), "替换只签 Content-Type，不带 If-None-Match"),
            () -> assertEquals(fileId, ((Number) row.get("replaces_file_id")).longValue()),
            () -> assertEquals(1, ((Number) row.get("replacement_no")).intValue()),
            () -> assertEquals(baseVersion, row.get("base_object_version")),
            () -> assertEquals(PRIVATE, row.get("access_level")),
            () -> assertEquals(Long.toString(fileId), receipt.getFileId()),
            () -> assertEquals(FileAddress.of(fileId).value(), receipt.getFileAddress()),
            () -> assertEquals(2, receipt.getVersionNo()),
            () -> assertEquals(PRIVATE, receipt.getAccessLevel()),
            () -> assertEquals(receipt.getCompletedAt(), repeated.getCompletedAt()),
            () -> assertEquals(2, repeated.getVersionNo()),
            () -> assertEquals(2, stored.getVersionNo()),
            () -> assertNull(stored.getPendingVersionNo()),
            () -> assertEquals("image/png", stored.getMimeType()),
            () -> assertEquals(FileBuilder.sha256(FileBuilder.PNG), stored.getContentHash()),
            () -> assertEquals("replacement.png", stored.getDisplayName()),
            () -> assertEquals("42", stored.getUploaderAccountId(), "上传人为首次上传人"),
            () -> assertEquals(FileVersionStatus.SUPERSEDED.getCode(), version(fileId, 1).get("version_status")),
            () -> assertEquals(available(), scanStatus(fileId)),
            () -> assertEquals(2, storage.versions((String) row.get("storage_object_ref")).size()));
        assertArrayEquals(FileBuilder.PNG, downloadBytes(PEER, fileId));
    }

    /**
     * 候选版本检查中：当前版本仍为 v1，查询返回检查中的版本号，下载得到旧内容；此时再次开始替换返回 409。
     * <li>候选冻结的新修订要求一个未部署的检查器，检查任务保持可重试失败，候选停留在检查中。</li>
     */
    @Test
    void test_pendingKeepsCurrent() {
        long fileId = availableFile(PRIVATE, FileBuilder.PDF, "application/pdf");
        fileMocker.replaceRevision(policy, 2, "UT_ABSENT_SCANNER", FileScanType.ANTI_MALWARE.getCode(), "CLEAN");

        String uploadId = replaceWith(fileId, FileBuilder.PNG, "image/png");
        complete(SOURCE, uploadId).assertOk();
        await().atMost(Duration.ofSeconds(15)).until(() -> FileScanStatus.SCANNING.getCode()
            .equals(version(fileId, 2).get("scan_status")));

        StoredFileResponseBody stored = describe(PEER, fileId);
        assertAll(() -> assertEquals(1, stored.getVersionNo()), () -> assertEquals(2, stored.getPendingVersionNo()),
            () -> assertEquals("application/pdf", stored.getMimeType()),
            () -> assertEquals(FileVersionStatus.CANDIDATE.getCode(), version(fileId, 2).get("version_status")),
            () -> assertEquals(FileVersionStatus.CURRENT.getCode(), version(fileId, 1).get("version_status")));
        assertArrayEquals(FileBuilder.PDF, downloadBytes(PEER, fileId), "检查通过前下载旧版本");
        replace(SOURCE, fileId, replacement(FileBuilder.JPEG, "image/jpeg"))
            .assertProblem(409, FileErrorCode.FILE_STATE_CONFLICT);
    }

    /** 替换内容检查不通过：候选版本 REJECTED，文件保持旧版本可下载；检查结果记录在版本 2 上；之后可以再次替换。 */
    @Test
    void test_rejectedKeepsCurrent() {
        long fileId = availableFile(PRIVATE, FileBuilder.PDF, "application/pdf");
        fileMocker.replaceRevision(policy, 2, FormatFileScanner.SCANNER_CODE, FileScanType.FORMAT.getCode(),
            "UT_NEVER_ACCEPTED");

        String uploadId = replaceWith(fileId, FileBuilder.PNG, "image/png");
        complete(SOURCE, uploadId).assertOk();
        awaitVersion(fileId, 2, FileVersionStatus.REJECTED);

        StoredFileResponseBody stored = describe(PEER, fileId);
        var results = fileData.results(fileId);
        assertAll(() -> assertEquals(1, stored.getVersionNo()), () -> assertNull(stored.getPendingVersionNo()),
            () -> assertEquals(available(), stored.getScanStatus()),
            () -> assertEquals(FileScanStatus.REJECTED.getCode(), version(fileId, 2).get("scan_status")),
            () -> assertEquals(2, results.size()),
            () -> assertEquals(2, ((Number) results.getLast().get("file_version_no")).intValue()),
            () -> assertEquals(FormatFileScanner.VALID, results.getLast().get("result_code")));
        assertArrayEquals(FileBuilder.PDF, downloadBytes(PEER, fileId));
        replace(SOURCE, fileId, replacement(FileBuilder.JPEG, "image/jpeg")).assertStatus(201);
    }

    /**
     * 公开文件替换：公开地址在 PUT 后即读到新内容（已接受的检查空窗）；检查通过后公开地址不变、带版本参数的地址变为 ?v=2；开始替换时
     * 与替换上传原子登记 FILE_PUBLIC_SYNC 任务。
     */
    @Test
    void test_publicReplacement() {
        long fileId = availableFile(PUBLIC, FileBuilder.PNG, "image/png");
        String publicUrl = describe(PEER, fileId).getPublicUrl();

        FileUploadResponseBody replacement = replace(SOURCE, fileId, replacement(FileBuilder.JPEG, "image/jpeg"))
            .assertStatus(201).as(FileUploadResponseBody.class);
        put(replacement, FileBuilder.JPEG);
        assertArrayEquals(FileBuilder.JPEG, storage.browserGet(publicUrl).body(), "公开地址读的是存储最新版本");
        complete(SOURCE, replacement.getUploadId()).assertOk();
        awaitVersion(fileId, 2, FileVersionStatus.CURRENT);

        StoredFileResponseBody stored = describe(PEER, fileId);
        assertAll(() -> assertEquals(publicUrl, stored.getPublicUrl()),
            () -> assertEquals(publicUrl + "?v=2", stored.getVersionedUrl()),
            () -> assertEquals(PUBLIC, fileData.uploadRow(Long.parseLong(replacement.getUploadId())).get("access_level")),
            () -> assertEquals(List.of(fileId + ":" + replacement.getUploadId()),
                fileData.taskKeys(FilePublicSyncHandler.EVENT_TYPE)));
        assertArrayEquals(FileBuilder.JPEG, storage.browserGet(publicUrl).body());
    }

    /**
     * 竞态保护：公开文件的上一次替换已过期但最新版本仍是未核对的写入（公开同步尚未恢复）时，开始新替换返回 409；同步任务恢复当前版本后，
     * 公开地址回到原内容，新替换可以开始。
     */
    @Test
    void test_publicReplacementWaitsForRestore() {
        long fileId = availableFile(PUBLIC, FileBuilder.PNG, "image/png");
        String publicUrl = describe(PEER, fileId).getPublicUrl();
        FileUploadResponseBody first = replace(SOURCE, fileId, replacement(FileBuilder.JPEG, "image/jpeg"))
            .assertStatus(201).as(FileUploadResponseBody.class);
        put(first, FileBuilder.JPEG);
        awaitFirstSyncAttempt(fileId, Long.parseLong(first.getUploadId()));
        fileData.expireUpload(Long.parseLong(first.getUploadId()), now().minusSeconds(1));

        replace(SOURCE, fileId, replacement(FileBuilder.JPEG, "image/jpeg"))
            .assertProblem(409, FileErrorCode.FILE_STATE_CONFLICT);

        CompensationEventRequest sync = FilePublicSyncHandler.buildEvent(fileId, Long.parseLong(first.getUploadId()));
        assertEquals(CompensationHandlerResult.success(), syncHandler.handle(new CompensationEvent(1L,
            sync.eventKey(), sync.eventType(), sync.subEventType(), null, 2)));
        assertArrayEquals(FileBuilder.PNG, storage.browserGet(publicUrl).body(), "同步任务已恢复当前版本");
        replace(SOURCE, fileId, replacement(FileBuilder.JPEG, "image/jpeg")).assertStatus(201);
    }

    /**
     * 其他来源系统或不存在的文件返回 404；请求体带访问级别或上传原因返回 422；已有未完成的替换时返回 409；同时开始两个替换只有一个成功。
     */
    @Test
    void test_businessRuleViolation() {
        long fileId = availableFile(PRIVATE, FileBuilder.PDF, "application/pdf");

        replace(PEER, fileId, replacement(FileBuilder.PNG, "image/png")).assertProblem(404, FileErrorCode.FILE_NOT_FOUND);
        replace(SOURCE, OperatorBuilder.nextId(), replacement(FileBuilder.PNG, "image/png"))
            .assertProblem(404, FileErrorCode.FILE_NOT_FOUND);
        replace(SOURCE, fileId, new StartFileReplacementRequestBody("a.png", "image/png", "16", null, PUBLIC, null))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("accessLevel");
        replace(SOURCE, fileId, new StartFileReplacementRequestBody("a.png", "image/png", "16", null, null, "LOGO"))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("reasonCode");
        assertTrue(fileData.replacements(fileId).isEmpty());

        long second = availableFile(PRIVATE, FileBuilder.PDF, "application/pdf");
        List<Integer> statuses = concurrentStarts(second, 2);
        assertEquals(List.of(201, 409), statuses.stream().sorted().toList(), "同时开始两个替换只有一个成功");
        replace(SOURCE, second, replacement(FileBuilder.PNG, "image/png"))
            .assertProblem(409, FileErrorCode.FILE_STATE_CONFLICT);
        assertEquals(1, fileData.replacements(second).size());
    }

    /** 同一授权被 PUT 两次：比基准新的版本多于一个，完成返回 409 并作废本次替换，文件不变；之后可以重新开始替换。 */
    @Test
    void test_extraWritesCancelReplacement() {
        long fileId = availableFile(PRIVATE, FileBuilder.PDF, "application/pdf");
        FileUploadResponseBody replacement = replace(SOURCE, fileId, replacement(FileBuilder.PNG, "image/png"))
            .assertStatus(201).as(FileUploadResponseBody.class);
        put(replacement, FileBuilder.PNG);
        put(replacement, FileBuilder.PNG);

        complete(SOURCE, replacement.getUploadId()).assertProblem(409, FileErrorCode.FILE_STATE_CONFLICT);

        assertAll(() -> assertEquals(FileUploadStatus.CANCELLED.getCode(),
                fileData.uploadRow(Long.parseLong(replacement.getUploadId())).get("status")),
            () -> assertEquals(1, fileData.versions(fileId).size()),
            () -> assertEquals(1, describe(PEER, fileId).getVersionNo()));
        complete(SOURCE, replacement.getUploadId()).assertProblem(409, FileErrorCode.FILE_STATE_CONFLICT);
        replace(SOURCE, fileId, replacement(FileBuilder.PNG, "image/png")).assertStatus(201);
    }

    /** 未检测到 PUT 时完成返回 409 且上传保持可完成；授权过期后不能完成，过期的替换不阻止新的替换。 */
    @Test
    void test_noUploadAndExpiry() {
        long fileId = availableFile(PRIVATE, FileBuilder.PDF, "application/pdf");
        String uploadId = replace(SOURCE, fileId, replacement(FileBuilder.PNG, "image/png")).assertStatus(201)
            .as(FileUploadResponseBody.class).getUploadId();

        complete(SOURCE, uploadId).assertProblem(409, FileErrorCode.FILE_STATE_CONFLICT);
        assertEquals(FileUploadStatus.CREATED.getCode(), fileData.uploadRow(Long.parseLong(uploadId)).get("status"));

        fileData.expireUpload(Long.parseLong(uploadId), now().minusSeconds(1));
        complete(SOURCE, uploadId).assertProblem(409, FileErrorCode.FILE_STATE_CONFLICT);
        String next = replace(SOURCE, fileId, replacement(FileBuilder.PNG, "image/png")).assertStatus(201)
            .as(FileUploadResponseBody.class).getUploadId();
        assertEquals(2, ((Number) fileData.uploadRow(Long.parseLong(next)).get("replacement_no")).intValue());
    }

    /** 文件不可替换（检查未通过）返回 409；参数非法 400 / 422；类型或大小不符合策略 422；均不登记替换。 */
    @Test
    void test_invalidParameter() {
        var pending = fileMocker.file(material(StorageInstanceMocker.MINIO_INSTANCE).policyId(policy.policyId())
            .scanStatus(FileScanStatus.PENDING_SCAN).version("v1").build());
        replace(FileSourceCodes.PLATFORM_SUPPORT, pending.fileId(), replacement(FileBuilder.PNG, "image/png"))
            .assertProblem(409, FileErrorCode.FILE_STATE_CONFLICT);
        long fileId = availableFile(PRIVATE, FileBuilder.PDF, "application/pdf");

        call(HttpMethod.POST, BASE + "/files/0" + fileId + "/replacements", SOURCE,
            replacement(FileBuilder.PNG, "image/png")).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        replace(SOURCE, fileId, new StartFileReplacementRequestBody(" .. ", "image/png", "16", null, null, null))
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        replace(SOURCE, fileId, new StartFileReplacementRequestBody("a.txt", "text/plain", "16", null, null, null))
            .assertProblem(422, FileErrorCode.FILE_POLICY_REJECTED);
        replace(SOURCE, fileId, new StartFileReplacementRequestBody("a.png", "image/png", "10485761", null, null, null))
            .assertProblem(422, FileErrorCode.FILE_POLICY_REJECTED);
        replace(SOURCE, fileId, new StartFileReplacementRequestBody("a.png", "image/png", "016", null, null, null))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("sizeBytes");

        assertAll(() -> assertTrue(fileData.replacements(fileId).isEmpty()),
            () -> assertTrue(fileData.replacements(pending.fileId()).isEmpty()));
    }

    /**
     * Platform 内部模块入口：存量文件（与回填 DML 一致的 v1 版本行）经进程内 Processor 替换与完成，新版本检查通过后提升为当前版本。
     */
    @Test
    void test_internalEntryOnBackfilledFile() {
        var existing = stored(material(StorageInstanceMocker.MINIO_INSTANCE).policyId(policy.policyId()).build());

        var started = replacementProcessor.process(new StartFileReplacementRequest(FileSourceCodes.PLATFORM_SUPPORT,
            existing.fileId(), "logo.png", "image/png", FileBuilder.PNG.length, 7L));
        assertEquals(200, storage.browserPut(started.getGrant().url(), started.getGrant().headers(), FileBuilder.PNG)
            .statusCode());
        var completed = completeProcessor.process(new CompleteFileUploadRequest(FileSourceCodes.PLATFORM_SUPPORT,
            started.getUploadId()));
        awaitVersion(existing.fileId(), 2, FileVersionStatus.CURRENT);

        assertAll(() -> assertEquals(existing.fileId(), completed.getFileId()),
            () -> assertEquals(2, completed.getVersionNo()),
            () -> assertEquals(FileAccessLevel.PRIVATE, completed.getAccessLevel()),
            () -> assertEquals(FileBuilder.sha256(FileBuilder.PNG), fileData.file(existing.fileId()).get("content_hash")),
            () -> assertEquals(2, ((Number) fileData.file(existing.fileId()).get("current_version_no")).intValue()));
    }

    /** 经内部接口完成一次上传并等待 v1 检查通过，返回文件 ID。 */
    private long availableFile(String accessLevel, byte[] bytes, String mime) {
        FileUploadResponseBody upload = start(SOURCE, upload(accessLevel, bytes, mime)).assertStatus(201)
            .as(FileUploadResponseBody.class);
        if (PRIVATE.equals(accessLevel)) {
            storage.track(objectRef(upload.getUploadId()));
        }
        put(upload, bytes);
        long fileId = Long.parseLong(complete(SOURCE, upload.getUploadId()).assertOk()
            .as(FileUploadCompletionResponseBody.class).getFileId());
        awaitVersion(fileId, 1, FileVersionStatus.CURRENT);
        return fileId;
    }

    /** 开始替换并按授权 PUT 新内容，返回上传 ID。 */
    private String replaceWith(long fileId, byte[] bytes, String mime) {
        FileUploadResponseBody replacement = replace(SOURCE, fileId, replacement(bytes, mime)).assertStatus(201)
            .as(FileUploadResponseBody.class);
        put(replacement, bytes);
        return replacement.getUploadId();
    }

    /** 同时发起多个开始替换请求，返回各自的 HTTP 状态。 */
    private List<Integer> concurrentStarts(long fileId, int count) {
        CountDownLatch ready = new CountDownLatch(1);
        List<CompletableFuture<PlatformResponseChecker>> calls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            calls.add(CompletableFuture.supplyAsync(() -> {
                try {
                    ready.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                return replace(SOURCE, fileId, replacement(FileBuilder.PNG, "image/png"));
            }));
        }
        ready.countDown();
        return calls.stream().map(CompletableFuture::join).map(PlatformResponseChecker::status).toList();
    }

    /** 授权请求头按小写键比较。 */
    private static Map<String, String> lowerKeys(Map<String, String> headers) {
        return headers.entrySet().stream()
            .collect(Collectors.toMap(entry -> entry.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue));
    }

    /** 等待公开同步任务提交后的首次执行结束（替换未到期，可重试），避免其持有替换锁时与下一步请求交错。 */
    private void awaitFirstSyncAttempt(long fileId, long uploadId) {
        String key = FilePublicSyncHandler.buildEvent(fileId, uploadId).eventKey();
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50)).until(() -> {
            Integer attempts = fileData.settledAttempts(FilePublicSyncHandler.EVENT_TYPE, key);
            return attempts != null && attempts >= 1;
        });
    }
}
