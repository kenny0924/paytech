package com.paytech.platform.support.auto.ext.http.files;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.api.enums.OnboardingFilePurpose;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileVerificationApi;
import com.paytech.platform.support.api.onboardingfiles.response.FileVerificationResponseBody;
import com.paytech.platform.support.test.mocker.local.StorageInstanceMocker;
import com.paytech.platform.support.test.base.OnboardingFileTestBase;
import com.paytech.platform.support.test.builder.OnboardingFileBuilder.Spec;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.OnboardingApplicantMocker.Applicant;

/**
 * CCT 关联文件前的私网核验：调用方 Header、期望申请主体 / Client / 原账号的一致性、用途代码与最小文件证据；不回调 CCT。
 *
 * @see OnboardingFileVerificationApi#verify(String, String, String, String, String, String, String)
 */
class VerifyOnboardingFileTest extends OnboardingFileTestBase {

    /** 唯一允许的调用方。 */
    private static final String CCT_CALLER = "client-master-service";

    private Applicant applicant;

    private Spec spec;

    /** 本类共用：一个入驻申请人及其申请主体上的已完成 PDF 材料。 */
    @BeforeEach
    void setUpClassMocks() {
        long policyId = fileMocker.formatPolicy().policyId();
        applicant = applicants.applicant();
        spec = fileMocker.file(material(StorageInstanceMocker.MINIO_INSTANCE).policyId(policyId)
            .owner(applicant.tenantId(), applicant.accountId(), OperatorBuilder.nextId(), OperatorBuilder.nextId())
            .version("v1").build());
    }

    /** 期望值全部一致：返回原上传、摘要、大小、类型、当前状态与本次核验时间；任一合法用途代码均可。 */
    @ParameterizedTest(name = "{0}")
    @EnumSource(OnboardingFilePurpose.class)
    void test_normalFlow(OnboardingFilePurpose purpose) {
        Map<String, String> query = expected();
        query.put("purpose", purpose.getCode());

        FileVerificationResponseBody body =
            verify(CCT_CALLER, Long.toString(spec.fileId()), query).assertOk().as(FileVerificationResponseBody.class);

        assertAll(() -> assertEquals(Long.toString(spec.fileId()), body.getFileId()),
            () -> assertEquals(Long.toString(spec.uploadId()), body.getUploadId()),
            () -> assertEquals(spec.contentHash(), body.getContentHash()),
            () -> assertEquals(Long.toString(spec.size()), body.getSizeBytes()),
            () -> assertEquals(spec.mime(), body.getMimeType()),
            () -> assertEquals(FileScanStatus.AVAILABLE.getCode(), body.getScanStatus()),
            () -> assertEquals(FileObjectStatus.ACTIVE.getCode(), body.getObjectStatus()),
            () -> assertEquals(now(), body.getCheckedAt()));
        cct.assertNoCalls();
    }

    /** 非 CCT 调用方按文件不存在拒绝，不读取文件。 */
    @Test
    void test_callerRejected() {
        verify("client-console-bff", Long.toString(spec.fileId()), expected())
            .assertProblem(404, OnboardingFileErrorCode.FILE_NOT_FOUND);
    }

    /** 期望申请、主体、Client 或原账号任一不符时统一返回 404，不暴露哪一项不一致。 */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"expectedApplicationId", "expectedSubjectPartyId", "expectedClientId",
        "requestingAccountId"})
    void test_expectationMismatch(String parameter) {
        Map<String, String> query = expected();
        query.put(parameter, Long.toString(OperatorBuilder.nextId()));

        verify(CCT_CALLER, Long.toString(spec.fileId()), query)
            .assertProblem(404, OnboardingFileErrorCode.FILE_NOT_FOUND);
    }

    /** 未知用途代码返回参数错误。 */
    @Test
    void test_invalidPurpose() {
        Map<String, String> query = expected();
        query.put("purpose", "REVIEW");

        verify(CCT_CALLER, Long.toString(spec.fileId()), query).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
    }

    /** 文件或期望标识非规范正整数时返回参数错误。 */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"01", "abc", "9223372036854775808"})
    void test_invalidParameter(String value) {
        verify(CCT_CALLER, value, expected()).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        Map<String, String> query = expected();
        query.put("expectedClientId", value);
        verify(CCT_CALLER, Long.toString(spec.fileId()), query).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
    }

    /** 合同外的 Query 参数返回参数错误。 */
    @Test
    void test_unknownQueryParameter() {
        Map<String, String> query = expected();
        query.put("tenantId", Long.toString(applicant.tenantId()));

        verify(CCT_CALLER, Long.toString(spec.fileId()), query).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
    }

    /** 与本类文件完全一致的期望值及 ASSOCIATE 用途。 */
    private Map<String, String> expected() {
        String[] owner = spec.ownerRefId().split(":");
        Map<String, String> query = new LinkedHashMap<>();
        query.put("expectedApplicationId", owner[0]);
        query.put("expectedSubjectPartyId", owner[1]);
        query.put("expectedClientId", Long.toString(applicant.clientId()));
        query.put("requestingAccountId", applicant.actor());
        query.put("purpose", OnboardingFilePurpose.ASSOCIATE.getCode());
        return query;
    }

    /** 以给定调用方发送核验请求。 */
    private PlatformResponseChecker verify(String caller, String fileId, Map<String, String> query) {
        HttpHeaders headers = headers(null, null);
        headers.set("X-Caller-Service", caller);
        String queryString = query.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining("&"));
        return call(HttpMethod.GET, "/internal/onboarding-files/files/" + fileId + "/verification?" + queryString,
            headers, null);
    }
}
