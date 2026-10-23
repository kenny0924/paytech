package com.paytech.platform.support.auto.ext.eventhandler.files;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.compensation.api.CompensationHandlerResult.Outcome;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.api.enums.FileStorageInstanceStatus;
import com.paytech.platform.support.filedocument.core.enums.FileVersionStatus;
import com.paytech.platform.support.filedocument.integration.scanning.FormatFileScanner;
import com.paytech.platform.support.filedocument.integration.storage.StoredContentType;
import com.paytech.platform.support.test.mocker.local.StorageInstanceMocker;
import com.paytech.platform.support.service.handler.FileScanHandler;
import com.paytech.platform.support.test.base.FileTestBase;
import com.paytech.platform.support.test.builder.FileBuilder;
import com.paytech.platform.support.test.builder.FileBuilder.Spec;
import com.paytech.platform.support.test.mocker.local.FileMocker.Policy;

/**
 * 文件扫描补偿入口：经真实 Handler、文件生命周期服务、Platform 格式检查器与存储注册表按记录实例读取 MinIO 准确版本，
 * 追加终局结果并推进扫描状态；记录的实例或桶停用时不读取密钥、不调用存储。text/csv 另复核全文为不含 NUL 的 UTF-8。替换版本按版本行检查，
 * 通过后提升为当前版本。
 *
 * @see FileScanHandler#handle(CompensationEvent)
 */
class FileScanHandlerTest extends FileTestBase {

    /** 格式检查器当前运行版本（生产常量为包内可见）。 */
    private static final String FORMAT_RUNTIME_VERSION = "1";

    private static final String UNAVAILABLE = "FILE_SCAN_UNAVAILABLE";

    private static final String IDENTITY_INVALID = "FILE_SCAN_IDENTITY_INVALID";

    @Autowired
    private FileScanHandler handler;

    private Policy policy;

    /** 本类共用：只含 PLATFORM_FORMAT 要求、接受 FORMAT_VALID 的策略。 */
    @BeforeEach
    void setUpClassMocks() {
        policy = fileMocker.formatPolicy();
    }

    /** 登记类型与实际文件头一致：结果 FORMAT_VALID，文件变为 AVAILABLE；重放不追加第二条结果。 */
    @Test
    void test_normalFlow() {
        Spec spec = stored(material().build());

        assertEquals(CompensationHandlerResult.success(), handle(spec.fileId()));

        assertScan(spec, FileScanStatus.AVAILABLE, FormatFileScanner.VALID);
        assertEquals(now(), instant(fileData.file(spec.fileId()).get("available_time")));
        assertEquals(CompensationHandlerResult.success(), handle(spec.fileId()));
        assertScan(spec, FileScanStatus.AVAILABLE, FormatFileScanner.VALID);
    }

    /** 登记为 PDF 但实际是 PNG：结果 FORMAT_INVALID 不在接受集合，FORMAT 要求使文件 REJECTED。 */
    @Test
    void test_businessRuleViolation() {
        Spec spec = stored(material().bytes(FileBuilder.PNG).build());

        assertEquals(CompensationHandlerResult.success(), handle(spec.fileId()));

        assertScan(spec, FileScanStatus.REJECTED, FormatFileScanner.INVALID);
        assertNull(fileData.file(spec.fileId()).get("available_time"));
    }

    /** 登记为 text/csv 的合法 UTF-8 CSV（含 BOM 与多字节字符）：结果 FORMAT_VALID，文件变为 AVAILABLE。 */
    @Test
    void test_normalFlow_csv() {
        Spec spec = stored(material().mime(StoredContentType.CSV).bytes(FileBuilder.CSV).build());

        assertEquals(CompensationHandlerResult.success(), handle(spec.fileId()));

        assertScan(spec, FileScanStatus.AVAILABLE, FormatFileScanner.VALID);
    }

    /**
     * CSV 开头 8 KiB 合法但其后含 NUL，或登记为 text/csv 的实际是二进制内容：结果 FORMAT_INVALID，文件 REJECTED。
     */
    @Test
    void test_businessRuleViolation_csvNotText() {
        Spec lateNul = stored(material().mime(StoredContentType.CSV).bytes(FileBuilder.csvWithLateNul()).build());
        Spec binary = stored(material().mime(StoredContentType.CSV).bytes(FileBuilder.BINARY).build());

        assertEquals(CompensationHandlerResult.success(), handle(lateNul.fileId()));
        assertEquals(CompensationHandlerResult.success(), handle(binary.fileId()));

        assertScan(lateNul, FileScanStatus.REJECTED, FormatFileScanner.INVALID);
        assertScan(binary, FileScanStatus.REJECTED, FormatFileScanner.INVALID);
    }

    /** 登记版本在存储中不存在：可重试失败，不追加结果，文件停留在 SCANNING。 */
    @Test
    void test_noDataFound() {
        Spec spec = fileMocker.file(material().version("missing-version").build());

        assertRetryable(handle(spec.fileId()));

        assertTrue(fileData.results(spec.fileId()).isEmpty(), "存储不可用时不能伪造结果");
        assertEquals(FileScanStatus.SCANNING.getCode(), fileData.file(spec.fileId()).get("scan_status"));
    }

    /** 实际字节与登记摘要不符：可重试失败，不追加结果。 */
    @Test
    void test_contentHashMismatch() {
        Spec spec = stored(material().contentHash(FileBuilder.sha256(FileBuilder.TEXT)).build());

        assertRetryable(handle(spec.fileId()));

        assertTrue(fileData.results(spec.fileId()).isEmpty());
        assertEquals(FileScanStatus.SCANNING.getCode(), fileData.file(spec.fileId()).get("scan_status"));
    }

    /** 多存储并存：文件记录在 S3 类型实例上，默认路由指向 MINIO 实例时仍按记录的实例读取并完成检查。 */
    @Test
    void test_readsRecordedInstance() {
        Spec spec = stored(material(StorageInstanceMocker.S3_INSTANCE).policyId(policy.policyId())
            .scanStatus(FileScanStatus.PENDING_SCAN).objectStatus(FileObjectStatus.ACTIVE).build());

        assertEquals(CompensationHandlerResult.success(), handle(spec.fileId()));

        assertScan(spec, FileScanStatus.AVAILABLE, FormatFileScanner.VALID);
        assertAll(() -> assertEquals(1, storageVault.revealCount(StorageInstanceMocker.S3_TOKEN)),
            () -> assertEquals(0, storageVault.revealCount(StorageInstanceMocker.MINIO_TOKEN)));
    }

    /** 记录的存储桶或实例已停用：可重试失败，不读取密钥、不发起存储调用、不追加结果。 */
    @Test
    void test_disabledStorageRetries() {
        Spec spec = stored(material().build());
        storageInstances.bucketStatus(StorageInstanceMocker.MINIO_INSTANCE, FileStorageBucketStatus.DISABLED);

        assertRetryable(handle(spec.fileId()));

        storageInstances.bucketStatus(StorageInstanceMocker.MINIO_INSTANCE, FileStorageBucketStatus.READ_ONLY);
        storageInstances.instanceStatus(StorageInstanceMocker.MINIO_INSTANCE, FileStorageInstanceStatus.DISABLED);
        assertRetryable(handle(spec.fileId()));

        assertEquals(0, storageVault.revealCount());
        assertTrue(fileData.results(spec.fileId()).isEmpty());
        storageInstances.instanceStatus(StorageInstanceMocker.MINIO_INSTANCE, FileStorageInstanceStatus.ACTIVE);
        assertEquals(CompensationHandlerResult.success(), handle(spec.fileId()), "只读桶仍可读");
        assertScan(spec, FileScanStatus.AVAILABLE, FormatFileScanner.VALID);
    }

    /**
     * 替换版本（事件键 fileId:v2）通过检查：在同一短事务中提升为当前版本——文件镜像改为 v2、当前版本号 2，原版本 SUPERSEDED；
     * 结果记录在版本 2 上。
     */
    @Test
    void test_replacementVersionPromoted() {
        Spec spec = stored(material().scanStatus(FileScanStatus.AVAILABLE).build());
        String replaced = storage.put(spec.objectRef(), FileBuilder.PNG);
        fileMocker.version(spec, 2, null, replaced, FileBuilder.PNG, "image/png", policy.policyId(),
            FileScanStatus.PENDING_SCAN, FileVersionStatus.CANDIDATE.getCode());

        assertEquals(CompensationHandlerResult.success(), handle(spec.fileId(), 2));

        var file = fileData.file(spec.fileId());
        var versions = fileData.versions(spec.fileId());
        var results = fileData.results(spec.fileId());
        assertAll(() -> assertEquals(FileScanStatus.AVAILABLE.getCode(), file.get("scan_status")),
            () -> assertEquals(2, ((Number) file.get("current_version_no")).intValue()),
            () -> assertEquals(replaced, file.get("object_version")),
            () -> assertEquals(FileBuilder.sha256(FileBuilder.PNG), file.get("content_hash")),
            () -> assertEquals("image/png", file.get("mime_type")),
            () -> assertEquals(FileVersionStatus.SUPERSEDED.getCode(), versions.getFirst().get("version_status")),
            () -> assertEquals(FileVersionStatus.CURRENT.getCode(), versions.getLast().get("version_status")),
            () -> assertEquals(FileScanStatus.AVAILABLE.getCode(), versions.getLast().get("scan_status")),
            () -> assertEquals(1, results.size()),
            () -> assertEquals(2, ((Number) results.getFirst().get("file_version_no")).intValue()),
            () -> assertEquals(FormatFileScanner.VALID, results.getFirst().get("result_code")));
        assertEquals(CompensationHandlerResult.success(), handle(spec.fileId(), 2), "重放不再追加结果");
        assertEquals(1, fileData.results(spec.fileId()).size());
    }

    /** 替换版本检查不通过：只标记版本 REJECTED，文件保持原版本与 AVAILABLE。 */
    @Test
    void test_replacementVersionRejected() {
        Spec spec = stored(material().scanStatus(FileScanStatus.AVAILABLE).build());
        String replaced = storage.put(spec.objectRef(), FileBuilder.PNG);
        fileMocker.version(spec, 2, null, replaced, FileBuilder.PNG, spec.mime(), policy.policyId(),
            FileScanStatus.PENDING_SCAN, FileVersionStatus.CANDIDATE.getCode());

        assertEquals(CompensationHandlerResult.success(), handle(spec.fileId(), 2));

        var file = fileData.file(spec.fileId());
        var candidate = fileData.versions(spec.fileId()).getLast();
        assertAll(() -> assertEquals(FileScanStatus.AVAILABLE.getCode(), file.get("scan_status")),
            () -> assertEquals(1, ((Number) file.get("current_version_no")).intValue()),
            () -> assertEquals(spec.version(), file.get("object_version")),
            () -> assertEquals(FileScanStatus.REJECTED.getCode(), candidate.get("scan_status")),
            () -> assertEquals(FileVersionStatus.REJECTED.getCode(), candidate.get("version_status")),
            () -> assertEquals(FileVersionStatus.CURRENT.getCode(),
                fileData.versions(spec.fileId()).getFirst().get("version_status")));
    }

    /** 存量文件缺少版本行（未执行回填 DML）时可重试失败，不改写文件。 */
    @Test
    void test_missingVersionRow() {
        Spec spec = stored(material().build());

        assertRetryable(handle(spec.fileId(), 3));

        assertEquals(FileScanStatus.PENDING_SCAN.getCode(), fileData.file(spec.fileId()).get("scan_status"));
    }

    /** 损坏的任务身份为终局失败，不读取文件。 */
    @Test
    void test_invalidParameter() {
        Spec spec = stored(material().build());
        CompensationEventRequest request = FileScanHandler.buildEvent(spec.fileId(), 1);

        var malformedKey = handler.handle(new CompensationEvent(1L, "0" + spec.fileId(), request.eventType(),
            request.subEventType(), null, 1));
        var unexpectedPayload = handler.handle(new CompensationEvent(1L, request.eventKey(), request.eventType(),
            request.subEventType(), "{}", 1));
        for (String key : new String[] {spec.fileId() + ":v0", spec.fileId() + ":2", spec.fileId() + ":vx"}) {
            var malformedVersion = handler.handle(new CompensationEvent(1L, key, request.eventType(),
                request.subEventType(), null, 1));
            assertEquals(Outcome.TERMINAL_FAILURE, malformedVersion.outcome(), key);
        }
        assertThrows(IllegalArgumentException.class, () -> FileScanHandler.buildEvent(spec.fileId(), 0));
        assertEquals(spec.fileId() + ":v3", FileScanHandler.buildEvent(spec.fileId(), 3).eventKey());

        assertAll(() -> assertEquals(Outcome.TERMINAL_FAILURE, malformedKey.outcome()),
            () -> assertEquals(IDENTITY_INVALID, malformedKey.errorCode()),
            () -> assertEquals(Outcome.TERMINAL_FAILURE, unexpectedPayload.outcome()),
            () -> assertEquals(IDENTITY_INVALID, unexpectedPayload.errorCode()));
        assertEquals(FileScanStatus.PENDING_SCAN.getCode(), fileData.file(spec.fileId()).get("scan_status"));
        assertTrue(fileData.results(spec.fileId()).isEmpty());
    }

    /** 本例对象上待扫描的 PDF 材料，使用本类策略。 */
    private FileBuilder material() {
        return material(StorageInstanceMocker.MINIO_INSTANCE).policyId(policy.policyId()).scanStatus(FileScanStatus.PENDING_SCAN)
            .objectStatus(FileObjectStatus.ACTIVE);
    }

    /** 以 Handler 事件工厂构造的 v1 补偿事件执行一次。 */
    private CompensationHandlerResult handle(long fileId) {
        return handle(fileId, 1);
    }

    /** 以 Handler 事件工厂构造的指定版本补偿事件执行一次。 */
    private CompensationHandlerResult handle(long fileId, int versionNo) {
        CompensationEventRequest request = FileScanHandler.buildEvent(fileId, versionNo);
        return handler.handle(new CompensationEvent(1L, request.eventKey(), request.eventType(),
            request.subEventType(), null, 1));
    }

    /** 依赖不可用：可重试，错误码稳定。 */
    private void assertRetryable(CompensationHandlerResult result) {
        assertAll(() -> assertEquals(Outcome.RETRYABLE_FAILURE, result.outcome()),
            () -> assertEquals(UNAVAILABLE, result.errorCode()));
    }

    /** 文件终态与唯一一条第 1 次尝试的结果。 */
    private void assertScan(Spec spec, FileScanStatus status, String resultCode) {
        assertEquals(status.getCode(), fileData.file(spec.fileId()).get("scan_status"));
        List<Map<String, Object>> results = fileData.results(spec.fileId());
        assertEquals(1, results.size(), "每项要求只追加一条结果");
        Map<String, Object> result = results.getFirst();
        Instant scannedTime = instant(result.get("scanned_time"));
        assertAll(() -> assertEquals(policy.requirementId(), ((Number) result.get("scan_requirement_id")).longValue()),
            () -> assertEquals(policy.policyId(), ((Number) result.get("scan_policy_id")).longValue()),
            () -> assertEquals(1, ((Number) result.get("attempt_no")).intValue()),
            () -> assertEquals(FORMAT_RUNTIME_VERSION, result.get("scanner_runtime_version")),
            () -> assertEquals(resultCode, result.get("result_code")),
            () -> assertEquals(now(), scannedTime));
    }

    /** 数据库 UTC 微秒时间列读取为 Instant。 */
    private static Instant instant(Object value) {
        return value instanceof java.time.LocalDateTime local ? local.toInstant(java.time.ZoneOffset.UTC) : (Instant) value;
    }
}
