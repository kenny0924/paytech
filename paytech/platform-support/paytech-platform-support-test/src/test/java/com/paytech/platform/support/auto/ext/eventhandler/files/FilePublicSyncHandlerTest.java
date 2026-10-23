package com.paytech.platform.support.auto.ext.eventhandler.files;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.compensation.api.CompensationHandlerResult.Outcome;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.core.enums.FileVersionStatus;
import com.paytech.platform.support.filedocument.domain.enums.FileUploadStatus;
import com.paytech.platform.support.service.handler.FilePublicSyncHandler;
import com.paytech.platform.support.service.processor.files.StartFileReplacementProcessor;
import com.paytech.platform.support.test.base.FileTestBase;
import com.paytech.platform.support.test.builder.FileBuilder;
import com.paytech.platform.support.test.builder.FileBuilder.Spec;
import com.paytech.platform.support.test.mocker.local.FileMocker.Policy;
import com.paytech.platform.support.test.mocker.local.StorageInstanceMocker;

/**
 * 公开文件同步补偿入口：经真实 Handler、同步 Processor、文件生命周期服务与 MinIO 适配器，替换结束后保证公开地址读到文件当前版本
 * <li>替换未结束（授权未到期或候选检查中）、替换锁被占用时返回可重试；之后已开始新替换时直接完成，由新替换收敛；最新版本已是当前版本
 * 或其恢复复制时不再复制。</li>
 * <li>公开桶为本机 MinIO 的临时匿名只读桶，经管理员密钥的实例访问；替换与版本行直接插入，不经开始替换接口，也不登记任务。</li>
 *
 * @see FilePublicSyncHandler#handle(CompensationEvent)
 */
class FilePublicSyncHandlerTest extends FileTestBase {

    private static final String PENDING = "FILE_PUBLIC_SYNC_PENDING";

    private static final String IDENTITY_INVALID = "FILE_PUBLIC_SYNC_IDENTITY_INVALID";

    @Autowired
    private FilePublicSyncHandler handler;

    @Autowired
    private RedissonClient redisson;

    private Policy policy;

    private String bucket;

    /** 本类公共：测试扫描策略、公开临时桶与管理员实例。 */
    @BeforeEach
    void setUpClassMocks() {
        policy = fileMocker.formatPolicy();
        bucket = tempBuckets.publicReadable();
        storageInstances.rootInstance();
        storageInstances.publicBucket(StorageInstanceMocker.ROOT_INSTANCE, bucket, tempBuckets.publicBaseUrl(bucket),
            FileStorageBucketStatus.ACTIVE);
        storageInstances.refresh();
    }

    /** 替换已过期而最新版本是未核对的写入：复制当前版本为最新版本，公开地址回到当前内容；再次执行不重复复制。 */
    @Test
    void test_normalFlow() {
        Spec file = publicFile();
        long uploadId = fileMocker.replacement(file, 1, file.version(), FileUploadStatus.CREATED.getCode(), expired());
        tempBuckets.put(bucket, file.objectRef(), FileBuilder.JPEG);

        assertEquals(CompensationHandlerResult.success(), handle(file.fileId(), uploadId));

        assertAll(() -> assertEquals(3, tempBuckets.versions(bucket, file.objectRef()).size(), "恢复复制产生新版本"),
            () -> assertArrayEquals(FileBuilder.PNG, storage.browserGet(publicUrl(file)).body()));
        assertEquals(CompensationHandlerResult.success(), handle(file.fileId(), uploadId));
        assertEquals(3, tempBuckets.versions(bucket, file.objectRef()).size(), "已恢复时不再复制");
    }

    /** 替换已结束且最新版本就是当前版本（没有发生写入或新版本已提升）：直接完成，不复制。 */
    @Test
    void test_inSyncWithoutCopy() {
        Spec file = publicFile();
        long uploadId = fileMocker.replacement(file, 1, file.version(), FileUploadStatus.EXPIRED.getCode(), expired());

        assertEquals(CompensationHandlerResult.success(), handle(file.fileId(), uploadId));

        assertEquals(1, tempBuckets.versions(bucket, file.objectRef()).size());
    }

    /** 授权尚未到期、或已完成但候选版本仍在检查中：返回可重试，不复制。 */
    @Test
    void test_pendingRetries() {
        Spec file = publicFile();
        long open = fileMocker.replacement(file, 1, file.version(), FileUploadStatus.CREATED.getCode(),
            now().plusSeconds(600));
        tempBuckets.put(bucket, file.objectRef(), FileBuilder.JPEG);
        assertPending(handle(file.fileId(), open));

        Spec checking = publicFile();
        long completed = fileMocker.replacement(checking, 1, checking.version(), FileUploadStatus.COMPLETED.getCode(),
            expired());
        String candidate = tempBuckets.put(bucket, checking.objectRef(), FileBuilder.JPEG);
        fileMocker.version(checking, 2, completed, candidate, FileBuilder.JPEG, "image/jpeg", policy.policyId(),
            FileScanStatus.SCANNING, FileVersionStatus.CANDIDATE.getCode());
        assertPending(handle(checking.fileId(), completed));

        assertAll(() -> assertEquals(2, tempBuckets.versions(bucket, file.objectRef()).size()),
            () -> assertEquals(2, tempBuckets.versions(bucket, checking.objectRef()).size()));
    }

    /** 之后已开始新的替换：本任务直接完成，不复制（由新替换的同步任务负责收敛）。 */
    @Test
    void test_supersededByNewerReplacement() {
        Spec file = publicFile();
        long first = fileMocker.replacement(file, 1, file.version(), FileUploadStatus.EXPIRED.getCode(), expired());
        String junk = tempBuckets.put(bucket, file.objectRef(), FileBuilder.JPEG);
        fileMocker.replacement(file, 2, junk, FileUploadStatus.CREATED.getCode(), now().plusSeconds(600));

        assertEquals(CompensationHandlerResult.success(), handle(file.fileId(), first));

        assertEquals(2, tempBuckets.versions(bucket, file.objectRef()).size());
    }

    /** 替换锁被开始替换占用时返回可重试，释放后可完成。 */
    @Test
    void test_replaceLockBusy() throws Exception {
        Spec file = publicFile();
        long uploadId = fileMocker.replacement(file, 1, file.version(), FileUploadStatus.EXPIRED.getCode(), expired());
        // 单资源模板锁在 Key 前加 lock: 并使用 SpinLock（AbstractLockProcessor.LockCommand.tryLock 合同）。
        RLock held = redisson.getSpinLock("lock:" + StartFileReplacementProcessor.replaceLock(file.fileId()));
        assertTrue(held.tryLock(0, 30, TimeUnit.SECONDS));
        CompensationHandlerResult busy;
        try {
            busy = CompletableFuture.supplyAsync(() -> handle(file.fileId(), uploadId)).get(10, TimeUnit.SECONDS);
        } finally {
            held.unlock();
        }

        assertPending(busy);
        assertEquals(CompensationHandlerResult.success(), handle(file.fileId(), uploadId));
    }

    /** 损坏的任务身份、不是公开文件替换的上传或不存在的上传为终局失败。 */
    @Test
    void test_invalidParameter() {
        Spec file = publicFile();
        long uploadId = fileMocker.replacement(file, 1, file.version(), FileUploadStatus.EXPIRED.getCode(), expired());
        Spec privateFile = fileMocker.file(material(StorageInstanceMocker.MINIO_INSTANCE).policyId(policy.policyId())
            .version("v1").build());
        long privateUpload = fileMocker.replacement(privateFile, 1, "v1", FileUploadStatus.EXPIRED.getCode(),
            expired());
        CompensationEventRequest request = FilePublicSyncHandler.buildEvent(file.fileId(), uploadId);

        for (String key : new String[] {"x", Long.toString(file.fileId()), "0:" + uploadId, file.fileId() + ":0"}) {
            assertTerminal(handler.handle(new CompensationEvent(1L, key, request.eventType(), "-", null, 1)));
        }
        assertTerminal(handler.handle(new CompensationEvent(1L, request.eventKey(), request.eventType(), "-", "{}", 1)));
        assertTerminal(handle(privateFile.fileId(), privateUpload));
        assertTerminal(handle(privateFile.fileId(), uploadId));
        assertTerminal(handle(file.fileId(), uploadId + 1));
    }

    /** 公开桶中检查通过的 PNG 文件（v1 由回填式版本行登记）。 */
    private Spec publicFile() {
        String objectRef = storage.newObjectRef();
        String version = tempBuckets.put(bucket, objectRef, FileBuilder.PNG);
        return fileMocker.file(FileBuilder.template(StorageInstanceMocker.ROOT_INSTANCE, bucket, objectRef)
            .bytes(FileBuilder.PNG).mime("image/png").policyId(policy.policyId()).accessLevel(FileAccessLevel.PUBLIC)
            .version(version).build());
    }

    /** 文件的公开地址。 */
    private String publicUrl(Spec file) {
        return tempBuckets.publicBaseUrl(bucket) + "/" + file.objectRef();
    }

    /** 已过期的上传期限。 */
    private Instant expired() {
        return now().minusSeconds(1);
    }

    /** 以 Handler 事件工厂构造的正常补偿事件执行一次。 */
    private CompensationHandlerResult handle(long fileId, long uploadId) {
        CompensationEventRequest request = FilePublicSyncHandler.buildEvent(fileId, uploadId);
        return handler.handle(new CompensationEvent(1L, request.eventKey(), request.eventType(),
            request.subEventType(), null, 1));
    }

    /** 替换未结束或锁被占用：可重试，错误码稳定。 */
    private static void assertPending(CompensationHandlerResult result) {
        assertAll(() -> assertEquals(Outcome.RETRYABLE_FAILURE, result.outcome()),
            () -> assertEquals(PENDING, result.errorCode()));
    }

    /** 身份损坏：终局失败。 */
    private static void assertTerminal(CompensationHandlerResult result) {
        assertAll(() -> assertEquals(Outcome.TERMINAL_FAILURE, result.outcome()),
            () -> assertEquals(IDENTITY_INVALID, result.errorCode()));
    }
}
