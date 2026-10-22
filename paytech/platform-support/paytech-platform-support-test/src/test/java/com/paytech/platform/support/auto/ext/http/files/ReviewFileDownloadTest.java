package com.paytech.platform.support.auto.ext.http.files;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.api.reviewfiles.ReviewFileApi;
import com.paytech.platform.support.api.reviewfiles.response.ReviewFileDownloadResponseBody;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels;
import com.paytech.platform.support.test.mocker.local.StorageInstanceMocker;
import com.paytech.platform.support.test.base.OnboardingFileTestBase;
import com.paytech.platform.support.test.builder.OnboardingFileBuilder;
import com.paytech.platform.support.test.builder.OnboardingFileBuilder.Spec;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;

/**
 * 运营审核下载入驻材料：运营会话与任一审核权限、文件扫描/对象状态、原版本短时下载地址与审计。
 *
 * @see ReviewFileApi#download(String, String, String)
 */
class ReviewFileDownloadTest extends OnboardingFileTestBase {

    /** 客户入驻审核查看权限（由入驻审核权限目录授予）。 */
    private static final String REVIEW_READ = "CLIENT_ONBOARDING_REVIEW_READ";

    /** 客户入驻材料核验权限。 */
    private static final String EVIDENCE_REVIEW = "CLIENT_ONBOARDING_EVIDENCE_REVIEW";

    private static final String AUDIT_ACTION = "OPERATIONS_REVIEW_FILE_DOWNLOAD";

    private static final String AUDIT_RESOURCE = "FILE";

    /** 下载授权有效期上限。 */
    private static final long DOWNLOAD_SECONDS = 60;

    /** 权限编码 → 只授予该权限的平台角色。 */
    private final Map<String, Long> roles = new HashMap<>();

    private long policyId;

    /** 本类共用：两个入驻审核权限各自的角色，以及文件引用的扫描策略。 */
    @BeforeEach
    void setUpClassMocks() {
        permissionRole(REVIEW_READ, "READ", "LOW");
        permissionRole(EVIDENCE_REVIEW, "REVIEW_EVIDENCE", "MEDIUM");
        policyId = fileMocker.formatPolicy().policyId();
    }

    /** 持有任一审核权限即可下载：返回展示信息与 60 秒内有效的原版本 HTTPS 地址，地址可取回原字节。 */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {REVIEW_READ, EVIDENCE_REVIEW})
    void test_normalFlow(String permission) {
        Operator reviewer = operationsMocker.operator(OperatorBuilder.administrator().roles(roles.get(permission)));
        Spec spec = stored(material().build());

        var body = download(reviewer, Long.toString(spec.fileId())).assertOk()
            .as(ReviewFileDownloadResponseBody.class);

        assertAll(() -> assertEquals(Long.toString(spec.fileId()), body.getFileId()),
            () -> assertEquals(spec.displayName(), body.getDisplayName()),
            () -> assertEquals(spec.mime(), body.getMimeType()),
            () -> assertEquals(Long.toString(spec.size()), body.getSizeBytes()),
            () -> assertEquals("https", URI.create(body.getUrl()).getScheme()),
            () -> assertEquals(now().plusSeconds(DOWNLOAD_SECONDS), body.getExpiresAt()));
        var fetched = storage.browserGet(body.getUrl());
        assertEquals(200, fetched.statusCode());
        assertArrayEquals(spec.bytes(), fetched.body());
        var event = auditChecker.assertEvent(requestId, AUDIT_ACTION, AuditModels.Result.SUCCEEDED.getCode(), reviewer.actor(),
            AUDIT_RESOURCE, Long.toString(spec.fileId()));
        auditChecker.assertNoSecret(event, body.getUrl());
    }

    /** 没有任一审核权限的运营人员被拒绝，记录拒绝审计。 */
    @Test
    void test_permissionDenied() {
        Operator viewer = operationsMocker.operator(OperatorBuilder.viewer());
        Spec spec = stored(material().build());

        download(viewer, Long.toString(spec.fileId()))
            .assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);

        auditChecker.assertEvent(requestId, AUDIT_ACTION, AuditModels.Result.DENIED.getCode(), viewer.actor(),
            AUDIT_RESOURCE, Long.toString(spec.fileId()));
    }

    /** 扫描未完成、未通过或对象不再有效的文件不能下载。 */
    @ParameterizedTest(name = "{0}/{1}")
    @MethodSource("unavailableStates")
    void test_businessRuleViolation(FileScanStatus scanStatus, FileObjectStatus objectStatus) {
        Operator reviewer = operationsMocker.operator(OperatorBuilder.administrator().roles(roles.get(REVIEW_READ)));
        Spec spec = fileMocker.file(material().scanStatus(scanStatus).objectStatus(objectStatus).version("v1").build());

        download(reviewer, Long.toString(spec.fileId()))
            .assertProblem(409, OnboardingFileErrorCode.FILE_STATE_CONFLICT);
    }

    /** 不存在的文件不暴露存在性。 */
    @Test
    void test_noDataFound() {
        Operator reviewer = operationsMocker.operator(OperatorBuilder.administrator().roles(roles.get(REVIEW_READ)));

        download(reviewer, Long.toString(OperatorBuilder.nextId()))
            .assertProblem(404, OnboardingFileErrorCode.FILE_NOT_FOUND);
    }

    /** 非规范十进制正整数或超出范围的文件标识在读库前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"abc", "0", "01", "-1", "1.0", "9223372036854775808", "99999999999999999999"})
    void test_invalidParameter(String fileId) {
        Operator reviewer = operationsMocker.operator(OperatorBuilder.administrator().roles(roles.get(REVIEW_READ)));

        download(reviewer, fileId).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
    }

    /** 不可下载的扫描与对象状态组合。 */
    static Stream<Arguments> unavailableStates() {
        return Stream.of(Arguments.of(FileScanStatus.PENDING_SCAN, FileObjectStatus.ACTIVE),
            Arguments.of(FileScanStatus.SCANNING, FileObjectStatus.ACTIVE),
            Arguments.of(FileScanStatus.REJECTED, FileObjectStatus.ACTIVE),
            Arguments.of(FileScanStatus.QUARANTINED, FileObjectStatus.ACTIVE),
            Arguments.of(FileScanStatus.AVAILABLE, FileObjectStatus.UNAVAILABLE),
            Arguments.of(FileScanStatus.AVAILABLE, FileObjectStatus.RETIRED));
    }

    /** 插入 ACTIVE 权限及只授予该权限的平台角色。 */
    private void permissionRole(String code, String action, String risk) {
        long permissionId = OperatorBuilder.nextId();
        assertEquals(1, operationsData.insertPermission(permissionId, code, "CLIENT_ONBOARDING", action, risk,
            "ACTIVE", now()));
        roles.put(code, operationsMocker.role(OperatorBuilder.nextId(), code + "_ROLE", code, false, permissionId));
    }

    /** 本例对象上扫描可用的 PDF 材料。 */
    private OnboardingFileBuilder material() {
        return material(StorageInstanceMocker.MINIO_INSTANCE).policyId(policyId);
    }

    /** 以运营人员身份发送空 Body 的下载请求。 */
    private PlatformResponseChecker download(Operator operator, String fileId) {
        return post("/review-files/v1/files/" + fileId + "/download", operator, null);
    }
}
