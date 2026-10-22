package com.paytech.platform.support.auto.ext.http.files;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
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
import com.paytech.platform.support.api.enums.OnboardingFileAction;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileApi;
import com.paytech.platform.support.api.onboardingfiles.response.FileResponseBody;
import com.paytech.platform.support.test.mocker.local.StorageInstanceMocker;
import com.paytech.platform.support.test.base.OnboardingFileTestBase;
import com.paytech.platform.support.test.builder.OnboardingFileBuilder;
import com.paytech.platform.support.test.builder.OnboardingFileBuilder.Spec;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.OnboardingApplicantMocker.Applicant;

/**
 * 客户读取本人入驻文件元数据：会话、原上传账号、CCT 原申请主体与 Tenant IAM 的两次核验，以及按当前状态提示可下载。
 *
 * @see OnboardingFileApi#read(String, String, String)
 */
class ReadOnboardingFileTest extends OnboardingFileTestBase {

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

    /** 扫描可用且对象有效：返回展示信息并提示 DOWNLOAD；响应不含摘要、存储坐标或 URL。 */
    @Test
    void test_normalFlow() {
        Spec spec = fileMocker.file(material().version("v1").build());
        allowRead(applicant.clientId());

        var checker = read(headers(applicant), Long.toString(spec.fileId())).assertOk();

        FileResponseBody body = checker.as(FileResponseBody.class);
        assertAll(() -> assertEquals(Long.toString(spec.fileId()), body.getFileId()),
            () -> assertEquals(spec.displayName(), body.getDisplayName()),
            () -> assertEquals(Long.toString(spec.size()), body.getSizeBytes()),
            () -> assertEquals(spec.mime(), body.getMimeType()),
            () -> assertEquals(FileScanStatus.AVAILABLE.getCode(), body.getScanStatus()),
            () -> assertEquals(FileObjectStatus.ACTIVE.getCode(), body.getObjectStatus()),
            () -> assertEquals(List.of(OnboardingFileAction.DOWNLOAD.getCode()), body.getAllowedActions()),
            () -> assertFalse(checker.json().has("contentHash")),
            () -> assertFalse(checker.text().contains(spec.objectRef())));
        cct.assertCalls(applicant.accountId(), applicationId, subjectPartyId, 1);
    }

    /** 扫描未完成、未通过或对象失效时仍可读取元数据，但不提示任何动作。 */
    @ParameterizedTest(name = "{0}/{1}")
    @MethodSource("unavailableStates")
    void test_notDownloadable(FileScanStatus scanStatus, FileObjectStatus objectStatus) {
        Spec spec =
            fileMocker.file(material().scanStatus(scanStatus).objectStatus(objectStatus).version("v1").build());
        allowRead(applicant.clientId());

        FileResponseBody body =
            read(headers(applicant), Long.toString(spec.fileId())).assertOk().as(FileResponseBody.class);

        assertAll(() -> assertEquals(scanStatus.getCode(), body.getScanStatus()),
            () -> assertEquals(objectStatus.getCode(), body.getObjectStatus()),
            () -> assertTrue(body.getAllowedActions().isEmpty()));
    }

    /** 其他账号上传的文件与不存在的文件都按不可访问返回 404，不查询 CCT。 */
    @Test
    void test_noDataFound() {
        Spec spec = fileMocker.file(material().version("v1").build());
        Applicant other = applicants.applicant();

        read(headers(other), Long.toString(spec.fileId())).assertProblem(404, OnboardingFileErrorCode.FILE_NOT_FOUND);
        read(headers(applicant), Long.toString(OperatorBuilder.nextId()))
            .assertProblem(404, OnboardingFileErrorCode.FILE_NOT_FOUND);

        cct.assertNoCalls();
    }

    /** CCT 不再认可原申请时返回 404。 */
    @Test
    void test_applicationNotFound() {
        Spec spec = fileMocker.file(material().version("v1").build());
        cct.applicationNotFound(applicant.accountId(), applicationId, subjectPartyId);

        read(headers(applicant), Long.toString(spec.fileId())).assertProblem(404, OnboardingFileErrorCode.FILE_NOT_FOUND);
    }

    /** CCT 确认的 Client 与原 Tenant 所属 Client 不一致时，Tenant IAM 拒绝读取。 */
    @Test
    void test_clientMismatch() {
        Spec spec = fileMocker.file(material().version("v1").build());
        allowRead(OperatorBuilder.nextId());

        read(headers(applicant), Long.toString(spec.fileId())).assertProblem(404, OnboardingFileErrorCode.FILE_NOT_FOUND);

        cct.assertCalls(applicant.accountId(), applicationId, subjectPartyId, 1);
    }

    /** 会话坐标非规范正整数或会话不存在时返回 CLIENT_SESSION_INVALID，不查询 CCT。 */
    @Test
    void test_sessionInvalid() {
        Spec spec = fileMocker.file(material().version("v1").build());

        read(headers("0", applicant.session()), Long.toString(spec.fileId()))
            .assertProblem(401, ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        read(headers(applicant.actor(), Long.toString(OperatorBuilder.nextId())), Long.toString(spec.fileId()))
            .assertProblem(401, ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);

        cct.assertNoCalls();
    }

    /** 文件标识非规范正整数时在读库前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"abc", "0", "01", "-1", "9223372036854775808"})
    void test_invalidParameter(String fileId) {
        read(headers(applicant), fileId).assertProblem(400, CommonErrorCode.INVALID_REQUEST);

        cct.assertNoCalls();
    }

    /** 不提示下载的扫描与对象状态组合。 */
    static Stream<Arguments> unavailableStates() {
        return Stream.of(Arguments.of(FileScanStatus.PENDING_SCAN, FileObjectStatus.ACTIVE),
            Arguments.of(FileScanStatus.SCANNING, FileObjectStatus.ACTIVE),
            Arguments.of(FileScanStatus.REJECTED, FileObjectStatus.ACTIVE),
            Arguments.of(FileScanStatus.QUARANTINED, FileObjectStatus.ACTIVE),
            Arguments.of(FileScanStatus.AVAILABLE, FileObjectStatus.UNAVAILABLE),
            Arguments.of(FileScanStatus.AVAILABLE, FileObjectStatus.RETIRED));
    }

    /** 申请人在本类申请主体上、引用本类策略的已完成 PDF 材料。 */
    private OnboardingFileBuilder material() {
        return material(StorageInstanceMocker.MINIO_INSTANCE).policyId(policyId)
            .owner(applicant.tenantId(), applicant.accountId(), applicationId, subjectPartyId);
    }

    /** CCT 对本类申请主体的读取授权，返回给定 Client。 */
    private void allowRead(long clientId) {
        cct.allow(applicant.accountId(), applicationId, subjectPartyId, clientId, true);
    }

    /** 以给定可信 Header 读取文件元数据。 */
    private PlatformResponseChecker read(HttpHeaders headers, String fileId) {
        return call(HttpMethod.GET, "/onboarding-files/v1/files/" + fileId, headers, null);
    }
}
