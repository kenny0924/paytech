package com.paytech.platform.support.auto.ext.http.files;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileApi;
import com.paytech.platform.support.api.onboardingfiles.response.DownloadResponseBody;
import com.paytech.platform.support.filedocument.integration.storage.minio.MinioFileStorage;
import com.paytech.platform.support.test.base.OnboardingFileTestBase;
import com.paytech.platform.support.test.builder.OnboardingFileBuilder;
import com.paytech.platform.support.test.builder.OnboardingFileBuilder.Spec;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.OnboardingApplicantMocker.Applicant;

/**
 * 客户下载本人入驻文件：签发前后两次完整授权、准确版本的 60 秒 HTTPS 授权，每次请求独立签发且不落库、不写审计。
 *
 * @see OnboardingFileApi#download(String, String, String)
 */
class DownloadOnboardingFileTest extends OnboardingFileTestBase {

    /** 下载授权有效期上限。 */
    private static final long DOWNLOAD_SECONDS = 60;

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

    /** 可下载文件：返回 60 秒内有效的 HTTPS 地址并可取回原字节；签发前后各向 CCT 授权一次，不产生审计事件。 */
    @Test
    void test_normalFlow() {
        Spec spec = stored(material().build());
        allowRead();

        DownloadResponseBody body =
            download(headers(applicant), Long.toString(spec.fileId())).assertOk().as(DownloadResponseBody.class);

        assertAll(() -> assertEquals("https", URI.create(body.getUrl()).getScheme()),
            () -> assertEquals(now().plusSeconds(DOWNLOAD_SECONDS), body.getExpiresAt()));
        var fetched = storage.browserGet(body.getUrl());
        assertEquals(200, fetched.statusCode());
        assertArrayEquals(spec.bytes(), fetched.body());
        cct.assertCalls(applicant.accountId(), applicationId, subjectPartyId, 2);
        await().atMost(Duration.ofSeconds(5)).until(() -> auditDispatcher.pendingCount() == 0);
        auditChecker.assertNoEvent(requestId);
    }

    /** 两次下载各自完整授权并签发可用地址，不改写文件事实。 */
    @Test
    void test_eachDownloadIndependent() {
        Spec spec = stored(material().build());
        allowRead();
        Map<String, Object> before = fileData.file(spec.fileId());

        for (int attempt = 0; attempt < 2; attempt++) {
            DownloadResponseBody body =
                download(headers(applicant), Long.toString(spec.fileId())).assertOk().as(DownloadResponseBody.class);
            assertEquals(200, storage.browserGet(body.getUrl()).statusCode());
        }

        cct.assertCalls(applicant.accountId(), applicationId, subjectPartyId, 4);
        assertEquals(before, fileData.file(spec.fileId()));
    }

    /** 扫描未完成、未通过或对象失效时返回状态冲突，不签发授权。 */
    @ParameterizedTest(name = "{0}/{1}")
    @MethodSource("unavailableStates")
    void test_businessRuleViolation(FileScanStatus scanStatus, FileObjectStatus objectStatus) {
        Spec spec =
            fileMocker.file(material().scanStatus(scanStatus).objectStatus(objectStatus).version("v1").build());
        allowRead();

        download(headers(applicant), Long.toString(spec.fileId()))
            .assertProblem(409, OnboardingFileErrorCode.FILE_STATE_CONFLICT);

        cct.assertCalls(applicant.accountId(), applicationId, subjectPartyId, 1);
    }

    /** 其他账号的文件不暴露存在性，不查询 CCT。 */
    @Test
    void test_noDataFound() {
        Spec spec = fileMocker.file(material().version("v1").build());
        Applicant other = applicants.applicant();

        download(headers(other), Long.toString(spec.fileId()))
            .assertProblem(404, OnboardingFileErrorCode.FILE_NOT_FOUND);

        cct.assertNoCalls();
    }

    /** 会话不存在时返回 CLIENT_SESSION_INVALID。 */
    @Test
    void test_sessionInvalid() {
        Spec spec = fileMocker.file(material().version("v1").build());

        download(headers(applicant.actor(), Long.toString(OperatorBuilder.nextId())), Long.toString(spec.fileId()))
            .assertProblem(401, ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);

        cct.assertNoCalls();
    }

    /** 下载请求不接受 Body。 */
    @Test
    void test_bodyRejected() {
        Spec spec = fileMocker.file(material().version("v1").build());

        call(HttpMethod.POST, downloadPath(Long.toString(spec.fileId())), headers(applicant), Map.of("purpose", "x"))
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);

        cct.assertNoCalls();
    }

    /** 文件标识非规范正整数时在读库前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"abc", "0", "01", "9223372036854775808"})
    void test_invalidParameter(String fileId) {
        download(headers(applicant), fileId).assertProblem(400, CommonErrorCode.INVALID_REQUEST);

        cct.assertNoCalls();
    }

    /** 不可下载的扫描与对象状态组合。 */
    static Stream<Arguments> unavailableStates() {
        return Stream.of(Arguments.of(FileScanStatus.PENDING_SCAN, FileObjectStatus.ACTIVE),
            Arguments.of(FileScanStatus.REJECTED, FileObjectStatus.ACTIVE),
            Arguments.of(FileScanStatus.QUARANTINED, FileObjectStatus.ACTIVE),
            Arguments.of(FileScanStatus.AVAILABLE, FileObjectStatus.RETIRED));
    }

    /** 申请人在本类申请主体上、引用本类策略的已完成 PDF 材料。 */
    private OnboardingFileBuilder material() {
        return material(MinioFileStorage.PROVIDER).policyId(policyId)
            .owner(applicant.tenantId(), applicant.accountId(), applicationId, subjectPartyId);
    }

    /** CCT 对本类申请主体的读取授权。 */
    private void allowRead() {
        cct.allow(applicant.accountId(), applicationId, subjectPartyId, applicant.clientId(), true);
    }

    /** 以给定可信 Header 发送空 Body 的下载请求。 */
    private PlatformResponseChecker download(HttpHeaders headers, String fileId) {
        return call(HttpMethod.POST, downloadPath(fileId), headers, null);
    }

    /** 下载路径。 */
    private static String downloadPath(String fileId) {
        return "/onboarding-files/v1/files/" + fileId + "/download";
    }
}
