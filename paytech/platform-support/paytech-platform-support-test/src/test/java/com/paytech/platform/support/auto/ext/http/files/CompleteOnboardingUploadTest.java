package com.paytech.platform.support.auto.ext.http.files;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileApi;
import com.paytech.platform.support.api.onboardingfiles.response.UploadCompletionResponseBody;
import com.paytech.platform.support.filedocument.domain.enums.FileUploadStatus;
import com.paytech.platform.support.filedocument.integration.storage.minio.MinioFileStorage;
import com.paytech.platform.support.service.handler.FileScanHandler;
import com.paytech.platform.support.test.base.OnboardingFileTestBase;
import com.paytech.platform.support.test.builder.OnboardingFileBuilder;
import com.paytech.platform.support.test.builder.OnboardingFileBuilder.Spec;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.OnboardingApplicantMocker.Applicant;

/**
 * 完成入驻文件上传：上传资源锁、准入校验、事务外核验实际字节、重新授权后登记文件与扫描任务，以及重复完成返回原回执。
 *
 * @see OnboardingFileApi#complete(String, String, String)
 */
class CompleteOnboardingUploadTest extends OnboardingFileTestBase {

    /** 原注解锁与模板锁共用的互斥域前缀。 */
    private static final String COMPLETE_LOCK_PREFIX = "file:upload:complete:";

    @Autowired
    private RedissonClient redisson;

    private long policyId;

    private Applicant applicant;

    private long applicationId;

    private long subjectPartyId;

    /** 本类共用：测试扫描策略、一个入驻申请人及其申请主体坐标。 */
    @BeforeEach
    void setUpClassMocks() {
        policyId = fileMocker.formatPolicy().policyId();
        applicant = applicants.applicant();
        applicationId = OperatorBuilder.nextId();
        subjectPartyId = OperatorBuilder.nextId();
    }

    /** 首次完成：返回 commandId=uploadId 的回执，登记文件、Owner 与扫描任务；提交后扫描任务推进文件为可用。 */
    @Test
    void test_normalFlow() {
        Spec spec = storedOpenUpload(material().build());
        allowUpload(true);

        UploadCompletionResponseBody receipt =
            complete(headers(applicant), spec.uploadId()).assertOk().as(UploadCompletionResponseBody.class);

        Map<String, Object> file = onlyFile(spec);
        String fileId = file.get("file_id").toString();
        assertAll(() -> assertEquals(Long.toString(spec.uploadId()), receipt.getCommandId()),
            () -> assertEquals(Long.toString(spec.uploadId()), receipt.getUploadId()),
            () -> assertEquals(fileId, receipt.getFileId()),
            () -> assertEquals(now(), receipt.getCompletedAt()),
            () -> assertEquals(FileUploadStatus.COMPLETED.getCode(), fileData.upload(spec.uploadId()).get("status")),
            () -> assertEquals(spec.contentHash(), file.get("content_hash")),
            () -> assertEquals(spec.size(), ((Number) file.get("size_bytes")).longValue()),
            () -> assertEquals(spec.mime(), file.get("mime_type")),
            () -> assertEquals(spec.version(), file.get("object_version")),
            () -> assertEquals(policyId, ((Number) file.get("scan_policy_id")).longValue()),
            () -> assertEquals(List.of(spec.ownerRefId()), fileData.ownerRefs(Long.parseLong(fileId))),
            () -> assertEquals(List.of(fileId), fileData.taskKeys(FileScanHandler.EVENT_TYPE)));
        cct.assertCalls(applicant.accountId(), applicationId, subjectPartyId, 2);
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50)).until(
            () -> FileScanStatus.AVAILABLE.getCode().equals(fileData.file(Long.parseLong(fileId)).get("scan_status")));
    }

    /** 同一 Upload 再次完成：返回同一回执，不重复校验存储、不再次向 CCT 授权、不登记第二个文件或扫描任务。 */
    @Test
    void test_repeatedCompletion() {
        Spec spec = storedOpenUpload(material().build());
        allowUpload(true);
        UploadCompletionResponseBody first =
            complete(headers(applicant), spec.uploadId()).assertOk().as(UploadCompletionResponseBody.class);

        UploadCompletionResponseBody second =
            complete(headers(applicant), spec.uploadId()).assertOk().as(UploadCompletionResponseBody.class);

        assertAll(() -> assertEquals(first.getCommandId(), second.getCommandId()),
            () -> assertEquals(first.getFileId(), second.getFileId()),
            () -> assertEquals(first.getCompletedAt(), second.getCompletedAt()),
            () -> assertEquals(1, fileData.filesOfUpload(spec.uploadId()).size()),
            () -> assertEquals(List.of(first.getFileId()), fileData.taskKeys(FileScanHandler.EVENT_TYPE)));
        cct.assertCalls(applicant.accountId(), applicationId, subjectPartyId, 3);
    }

    /** 实际字节与声明类型不符：存储核验失败返回 422，Upload 保持 CREATED，不重新授权、不登记文件。 */
    @Test
    void test_storageVerificationFailed() {
        Spec spec = storedOpenUpload(material().bytes(OnboardingFileBuilder.TEXT).build());
        allowUpload(true);

        complete(headers(applicant), spec.uploadId()).assertProblem(422, OnboardingFileErrorCode.FILE_POLICY_REJECTED);

        assertNotCompleted(spec);
        cct.assertCalls(applicant.accountId(), applicationId, subjectPartyId, 1);
    }

    /** CCT 判定当前目标不可上传：返回状态冲突，不校验存储、不登记文件。 */
    @Test
    void test_targetNotUploadable() {
        Spec spec = storedOpenUpload(material().build());
        allowUpload(false);

        complete(headers(applicant), spec.uploadId()).assertProblem(409, OnboardingFileErrorCode.FILE_STATE_CONFLICT);

        assertNotCompleted(spec);
        cct.assertCalls(applicant.accountId(), applicationId, subjectPartyId, 1);
    }

    /** 账号不再只有原入驻租户（目标变更）：返回目标冲突，不登记文件。 */
    @Test
    void test_targetConflict() {
        Spec spec = storedOpenUpload(material().build());
        allowUpload(true);
        applicants.secondOnboardingTenant(applicant);

        complete(headers(applicant), spec.uploadId()).assertProblem(409, OnboardingFileErrorCode.FILE_TARGET_CONFLICT);

        assertNotCompleted(spec);
    }

    /** 原上传授权已到期：返回状态冲突，不登记文件。 */
    @Test
    void test_uploadExpired() {
        Spec spec = storedOpenUpload(material().expiresAt(now().minusSeconds(1)).build());
        allowUpload(true);

        complete(headers(applicant), spec.uploadId()).assertProblem(409, OnboardingFileErrorCode.FILE_STATE_CONFLICT);

        assertNotCompleted(spec);
    }

    /** 同一 Upload 已被锁定（沿用原注解锁的完整 Key）：等待上限后返回并发冲突，不读取 CCT、不登记文件。 */
    @Test
    void test_lockContention() throws InterruptedException {
        Spec spec = storedOpenUpload(material().build());
        RLock held = redisson.getLock(COMPLETE_LOCK_PREFIX + spec.uploadId());
        assertTrue(held.tryLock(0, 30, TimeUnit.SECONDS));
        try {
            complete(headers(applicant), spec.uploadId()).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        } finally {
            held.unlock();
        }

        assertNotCompleted(spec);
        cct.assertNoCalls();
    }

    /** 其他账号的 Upload 不暴露存在性，不读取 CCT。 */
    @Test
    void test_noDataFound() {
        Spec spec = storedOpenUpload(material().build());
        Applicant other = applicants.applicant();

        complete(headers(other), spec.uploadId()).assertProblem(404, OnboardingFileErrorCode.FILE_NOT_FOUND);

        assertNotCompleted(spec);
        cct.assertNoCalls();
    }

    /** 会话坐标非规范正整数或会话不存在时返回 CLIENT_SESSION_INVALID。 */
    @Test
    void test_sessionInvalid() {
        Spec spec = storedOpenUpload(material().build());

        complete(headers(applicant.actor(), "abc"), spec.uploadId())
            .assertProblem(401, ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        complete(headers(applicant.actor(), Long.toString(OperatorBuilder.nextId())), spec.uploadId())
            .assertProblem(401, ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);

        assertNotCompleted(spec);
        cct.assertNoCalls();
    }

    /** 上传标识非规范正整数时在读库前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"abc", "0", "01", "-1", "9223372036854775808"})
    void test_invalidParameter(String uploadId) {
        call(HttpMethod.POST, completionPath(uploadId), headers(applicant), null)
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);

        cct.assertNoCalls();
    }

    /** 完成请求不接受任何 Body（包括浏览器自报摘要）。 */
    @Test
    void test_bodyRejected() {
        Spec spec = storedOpenUpload(material().build());

        call(HttpMethod.POST, completionPath(Long.toString(spec.uploadId())), headers(applicant),
            Map.of("contentHash", spec.contentHash())).assertProblem(400, CommonErrorCode.INVALID_REQUEST);

        assertNotCompleted(spec);
        cct.assertNoCalls();
    }

    /** 申请人在本类申请主体上的未完成 Upload，对象已以唯一版本写入存储。 */
    private Spec storedOpenUpload(Spec draft) {
        return fileMocker.openUpload(draft.withVersion(storage.put(draft.objectRef(), draft.bytes())));
    }

    /** 本类申请主体上、引用本类策略的 PDF 材料。 */
    private OnboardingFileBuilder material() {
        return material(MinioFileStorage.PROVIDER).policyId(policyId)
            .owner(applicant.tenantId(), applicant.accountId(), applicationId, subjectPartyId)
            .scanStatus(FileScanStatus.PENDING_SCAN).objectStatus(FileObjectStatus.ACTIVE);
    }

    /** CCT 对本类申请主体的授权，可读，是否可上传由用例指定。 */
    private void allowUpload(boolean canUpload) {
        cct.allow(applicant.accountId(), applicationId, subjectPartyId, applicant.clientId(), canUpload);
    }

    /** Upload 未完成，也没有登记文件或扫描任务。 */
    private void assertNotCompleted(Spec spec) {
        assertAll(
            () -> assertEquals(FileUploadStatus.CREATED.getCode(), fileData.upload(spec.uploadId()).get("status")),
            () -> assertTrue(fileData.filesOfUpload(spec.uploadId()).isEmpty()),
            () -> assertTrue(fileData.taskKeys(FileScanHandler.EVENT_TYPE).isEmpty()));
    }

    /** Upload 登记的唯一文件。 */
    private Map<String, Object> onlyFile(Spec spec) {
        List<Map<String, Object>> files = fileData.filesOfUpload(spec.uploadId());
        assertEquals(1, files.size(), "应恰好登记一个文件");
        return files.getFirst();
    }

    /** 以给定可信 Header 发送空 Body 的完成请求。 */
    private PlatformResponseChecker complete(HttpHeaders headers, long uploadId) {
        return call(HttpMethod.POST, completionPath(Long.toString(uploadId)), headers, null);
    }

    /** 完成上传路径。 */
    private static String completionPath(String uploadId) {
        return "/onboarding-files/v1/uploads/" + uploadId + "/completion";
    }
}
