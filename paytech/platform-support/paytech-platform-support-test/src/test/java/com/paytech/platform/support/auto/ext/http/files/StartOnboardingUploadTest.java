package com.paytech.platform.support.auto.ext.http.files;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileApi;
import com.paytech.platform.support.api.onboardingfiles.request.StartUploadRequestBody;
import com.paytech.platform.support.api.onboardingfiles.response.UploadAuthorization;
import com.paytech.platform.support.api.onboardingfiles.response.UploadResponseBody;
import com.paytech.platform.support.filedocument.domain.enums.FileUploadStatus;
import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.filedocument.domain.file.FileSourceCodes;
import com.paytech.platform.support.test.mocker.local.StorageInstanceMocker;
import com.paytech.platform.support.test.base.OnboardingFileTestBase;
import com.paytech.platform.support.test.builder.OnboardingFileBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.StartUploadRequestBuilder;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.OnboardingApplicantMocker.Applicant;
import com.paytech.platform.support.test.mocker.local.OnboardingFileMocker;
import com.paytech.platform.support.test.mocker.local.OnboardingFileMocker.Policy;

/**
 * 开始入驻文件上传：会话与 CCT 授权、按当前策略登记 Upload、签发可用的 PUT 授权，以及同一上传目标的资源锁互斥。
 *
 * @see OnboardingFileApi#start(String, String, StartUploadRequestBody)
 */
class StartOnboardingUploadTest extends OnboardingFileTestBase {

    /** 开始上传路径。 */
    private static final String UPLOADS = "/onboarding-files/v1/uploads";

    /** 原注解锁与模板锁共用的互斥域前缀。 */
    private static final String START_LOCK_PREFIX = "file:upload:start:";

    /** Upload 有效秒数，取默认写入路由的上传期限。 */
    private static final long UPLOAD_SECONDS = StorageInstanceMocker.UPLOAD_SECONDS;

    @Autowired
    private RedissonClient redisson;

    private Policy policy;

    private Applicant applicant;

    private long applicationId;

    private long subjectPartyId;

    /** 本类共用：测试扫描策略、一个入驻申请人及其申请主体坐标。 */
    @BeforeEach
    void setUpClassMocks() {
        policy = fileMocker.formatPolicy();
        applicant = applicants.applicant();
        applicationId = OperatorBuilder.nextId();
        subjectPartyId = OperatorBuilder.nextId();
    }

    /** CCT 允许上传：登记 CREATED Upload，返回 201 与指向原对象的 HTTPS PUT 授权，浏览器可按授权直传。 */
    @Test
    void test_normalFlow() {
        cct.allow(applicant.accountId(), applicationId, subjectPartyId, applicant.clientId(), true);

        UploadResponseBody body = start(headers(applicant), request().build()).assertStatus(201).assertRequestId()
            .as(UploadResponseBody.class);

        Map<String, Object> upload = onlyUpload();
        Instant expiresAt = now().plusSeconds(UPLOAD_SECONDS);
        UploadAuthorization authorization = body.getAuthorization();
        assertAll(() -> assertEquals(upload.get("upload_id").toString(), body.getUploadId()),
            () -> assertEquals(applicant.tenantId(), ((Number) upload.get("tenant_id")).longValue()),
            () -> assertEquals(applicationId + ":" + subjectPartyId, upload.get("owner_ref_id")),
            () -> assertEquals(StartUploadRequestBuilder.DEFAULT_FILENAME, upload.get("display_name")),
            () -> assertEquals((long) OnboardingFileBuilder.PDF.length, ((Number) upload.get("expected_size")).longValue()),
            () -> assertEquals(StartUploadRequestBuilder.DEFAULT_MIME, upload.get("expected_mime")),
            () -> assertEquals(FileSourceCodes.PLATFORM_SUPPORT, upload.get("source_system")),
            () -> assertEquals(FileSourceCodes.CLIENT_ONBOARDING_MATERIAL, upload.get("reason_code")),
            () -> assertEquals(applicant.accountId(), ((Number) upload.get("uploader_id")).longValue()),
            () -> assertEquals(StorageInstanceMocker.MINIO_INSTANCE, upload.get("storage_provider")),
            () -> assertEquals(storage.bucket(), upload.get("storage_container_ref")),
            () -> assertEquals(policy.policyId(), ((Number) upload.get("scan_policy_id")).longValue()),
            () -> assertEquals(FileUploadStatus.CREATED.getCode(), upload.get("status")),
            () -> assertEquals(expiresAt, instant(upload.get("expires_time"))),
            () -> assertEquals(expiresAt, body.getExpiresAt()),
            () -> assertEquals("PUT", authorization.getMethod()),
            () -> assertEquals("https", URI.create(authorization.getUrl()).getScheme()),
            () -> assertEquals(StartUploadRequestBuilder.DEFAULT_MIME, authorization.getHeaders().get("Content-Type")),
            () -> assertTrue(authorization.getExpiresAt().isAfter(now())),
            () -> assertFalse(authorization.getExpiresAt().isAfter(body.getExpiresAt())));
        cct.assertCalls(applicant.accountId(), applicationId, subjectPartyId, 1);

        storage.track((String) upload.get("storage_object_ref"));
        var put = storage.browserPut(authorization.getUrl(), authorization.getHeaders(), OnboardingFileBuilder.PDF);
        assertEquals(200, put.statusCode());
        assertEquals(1, storage.versions((String) upload.get("storage_object_ref")).size());
    }

    /** 文件名按服务器规则归一化：反斜杠视为路径分隔、只保留最后一段、清除控制与格式字符并去除首尾空白。 */
    @Test
    void test_filenameNormalized() {
        cct.allow(applicant.accountId(), applicationId, subjectPartyId, applicant.clientId(), true);

        start(headers(applicant), request().filename("C:\\fakepath\\board\u0007resolution\u200B.pdf ").build())
            .assertStatus(201);

        Map<String, Object> upload = onlyUpload();
        assertEquals("boardresolution.pdf", upload.get("display_name"));
    }

    /** 字段缺失、格式或范围非法，以及归一化后展示名无效时，在读取会话与 CCT 之前拒绝，不登记 Upload。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    void test_invalidParameter(String scenario, StartUploadRequestBody request) {
        start(headers(applicant), request).assertProblem(400, CommonErrorCode.INVALID_REQUEST);

        cct.assertNoCalls();
        assertTrue(fileData.uploadsOf(applicant.accountId()).isEmpty());
    }

    /** 严格 JSON 边界：Body 含合同外字段（例如自报 Tenant）时拒绝。 */
    @Test
    void test_unknownField() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("applicationId", Long.toString(applicationId));
        body.put("subjectPartyId", Long.toString(subjectPartyId));
        body.put("filename", StartUploadRequestBuilder.DEFAULT_FILENAME);
        body.put("mimeType", StartUploadRequestBuilder.DEFAULT_MIME);
        body.put("sizeBytes", Long.toString(OnboardingFileBuilder.PDF.length));
        body.put("tenantId", Long.toString(applicant.tenantId()));

        call(HttpMethod.POST, UPLOADS, headers(applicant), body).assertProblem(400, CommonErrorCode.INVALID_REQUEST);

        cct.assertNoCalls();
        assertTrue(fileData.uploadsOf(applicant.accountId()).isEmpty());
    }

    /** 会话坐标非规范正整数或会话不存在时返回 CLIENT_SESSION_INVALID，不查询 CCT。 */
    @Test
    void test_sessionInvalid() {
        start(headers(applicant.actor(), "01"), request().build())
            .assertProblem(401, ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        start(headers(applicant.actor(), Long.toString(OperatorBuilder.nextId())), request().build())
            .assertProblem(401, ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);

        cct.assertNoCalls();
        assertTrue(fileData.uploadsOf(applicant.accountId()).isEmpty());
    }

    /** CCT 判定当前目标不可上传（申请不可编辑或主体不在材料范围）时返回状态冲突，不登记 Upload。 */
    @Test
    void test_targetNotUploadable() {
        cct.allow(applicant.accountId(), applicationId, subjectPartyId, applicant.clientId(), false);

        start(headers(applicant), request().build()).assertProblem(409, OnboardingFileErrorCode.FILE_STATE_CONFLICT);

        cct.assertCalls(applicant.accountId(), applicationId, subjectPartyId, 1);
        assertTrue(fileData.uploadsOf(applicant.accountId()).isEmpty());
    }

    /** CCT 不认可原申请时按文件目标不可访问返回 404。 */
    @Test
    void test_noDataFound() {
        cct.applicationNotFound(applicant.accountId(), applicationId, subjectPartyId);

        start(headers(applicant), request().build()).assertProblem(404, OnboardingFileErrorCode.FILE_NOT_FOUND);

        assertTrue(fileData.uploadsOf(applicant.accountId()).isEmpty());
    }

    /** 声明类型不在策略允许范围或大小超过策略上限时返回 422，登记事务回滚。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("policyRejections")
    void test_businessRuleViolation(String scenario, String mimeType, String sizeBytes) {
        cct.allow(applicant.accountId(), applicationId, subjectPartyId, applicant.clientId(), true);

        start(headers(applicant), request().mimeType(mimeType).sizeBytes(sizeBytes).build())
            .assertProblem(422, OnboardingFileErrorCode.FILE_POLICY_REJECTED);

        assertTrue(fileData.uploadsOf(applicant.accountId()).isEmpty());
    }

    /**
     * 同一账号、申请与主体的上传目标已被锁定时（沿用原注解锁的完整 Key），新请求在等待上限后返回并发冲突且不访问 CCT；
     * 同一账号的其他主体不受影响。
     */
    /** 入驻上传按来源系统 platform-support 的精确路由选择存储；路由指向的桶只读时返回 503，不调用 CCT 之后的登记与签发。 */
    @Test
    void test_routedByPlatformSource() {
        storageInstances.route(FileSourceCodes.PLATFORM_SUPPORT, StorageInstanceMocker.S3_INSTANCE,
            OnboardingFileMocker.POLICY_CODE);
        storageInstances.refresh();
        cct.allow(applicant.accountId(), applicationId, subjectPartyId, applicant.clientId(), true);

        start(headers(applicant), request().build()).assertStatus(201);
        storage.track((String) onlyUpload().get("storage_object_ref"));
        assertEquals(StorageInstanceMocker.S3_INSTANCE, onlyUpload().get("storage_provider"));

        storageInstances.bucketStatus(StorageInstanceMocker.S3_INSTANCE, FileStorageBucketStatus.READ_ONLY);
        start(headers(applicant), request().build()).assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE);
        assertEquals(1, fileData.uploadsOf(applicant.accountId()).size());
    }

    @Test
    void test_concurrentStartSameTarget() throws InterruptedException {
        long otherSubjectPartyId = OperatorBuilder.nextId();
        cct.allow(applicant.accountId(), applicationId, otherSubjectPartyId, applicant.clientId(), true);
        RLock held = redisson.getLock(START_LOCK_PREFIX + applicant.accountId() + ":" + applicationId + ":"
            + subjectPartyId);
        assertTrue(held.tryLock(0, 30, TimeUnit.SECONDS));
        try {
            start(headers(applicant), request().build()).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
            cct.assertNoCalls();
            assertTrue(fileData.uploadsOf(applicant.accountId()).isEmpty());

            start(headers(applicant), StartUploadRequestBuilder.template(applicationId, otherSubjectPartyId).build())
                .assertStatus(201);
        } finally {
            held.unlock();
        }
        cct.assertCalls(applicant.accountId(), applicationId, otherSubjectPartyId, 1);
        assertEquals(1, fileData.uploadsOf(applicant.accountId()).size());
    }

    /** 会被入口拒绝的请求；申请主体使用固定合法标识，校验失败先于任何查询。 */
    static Stream<Arguments> invalidRequests() {
        long application = 7_000_000_000_000_000_001L;
        long subject = 7_000_000_000_000_000_002L;
        return Stream.of(
            Arguments.of("filename null", StartUploadRequestBuilder.template(application, subject).filename(null).build()),
            Arguments.of("filename blank", StartUploadRequestBuilder.template(application, subject).filename("   ").build()),
            Arguments.of("filename ..", StartUploadRequestBuilder.template(application, subject).filename("..").build()),
            Arguments.of("filename path only",
                StartUploadRequestBuilder.template(application, subject).filename("docs/\u0007").build()),
            Arguments.of("filename 256 chars",
                StartUploadRequestBuilder.template(application, subject).filename("a".repeat(256)).build()),
            Arguments.of("mimeType uppercase", StartUploadRequestBuilder.template(application, subject).mimeType("PDF").build()),
            Arguments.of("mimeType null", StartUploadRequestBuilder.template(application, subject).mimeType(null).build()),
            Arguments.of("mimeType too long",
                StartUploadRequestBuilder.template(application, subject).mimeType("application/" + "x".repeat(117)).build()),
            Arguments.of("sizeBytes zero", StartUploadRequestBuilder.template(application, subject).sizeBytes("0").build()),
            Arguments.of("sizeBytes leading zero",
                StartUploadRequestBuilder.template(application, subject).sizeBytes("01").build()),
            Arguments.of("sizeBytes overflow",
                StartUploadRequestBuilder.template(application, subject).sizeBytes("9223372036854775808").build()),
            Arguments.of("applicationId not numeric",
                StartUploadRequestBuilder.template(application, subject).applicationId("abc").build()),
            Arguments.of("subjectPartyId null",
                StartUploadRequestBuilder.template(application, subject).subjectPartyId(null).build()));
    }

    /** 违反测试策略（PDF/PNG/JPEG，最大 10 MiB）的声明。 */
    static Stream<Arguments> policyRejections() {
        String pdfSize = Long.toString(OnboardingFileBuilder.PDF.length);
        return Stream.of(Arguments.of("mime not allowed", "text/plain", pdfSize),
            Arguments.of("size over limit", StartUploadRequestBuilder.DEFAULT_MIME, "10485761"));
    }

    /** 本类申请主体上的默认请求。 */
    private StartUploadRequestBuilder request() {
        return StartUploadRequestBuilder.template(applicationId, subjectPartyId);
    }

    /** 本类唯一 Upload；数量必须为 1。 */
    private Map<String, Object> onlyUpload() {
        List<Map<String, Object>> uploads = fileData.uploadsOf(applicant.accountId());
        assertEquals(1, uploads.size(), "应恰好登记一个 Upload");
        return uploads.getFirst();
    }

    /** 以给定可信 Header 发送开始上传请求。 */
    private PlatformResponseChecker start(HttpHeaders headers, StartUploadRequestBody request) {
        return call(HttpMethod.POST, UPLOADS, headers, request);
    }

    /** 数据库 DATETIME(6) 读出的 UTC 时间。 */
    private static Instant instant(Object value) {
        return value instanceof LocalDateTime local ? local.toInstant(ZoneOffset.UTC) : (Instant) value;
    }
}
