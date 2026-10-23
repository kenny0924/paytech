package com.paytech.platform.support.auto.ext.http.localization;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.LocalizationErrorCode;
import com.paytech.platform.support.api.files.InternalFileApi;
import com.paytech.platform.support.api.files.request.StartFileUploadRequestBody;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.localization.LocalizationContentApi;
import com.paytech.platform.support.api.localization.request.ConfirmKeyImportRequestBody;
import com.paytech.platform.support.api.localization.request.ImportTranslationsRequestBody;
import com.paytech.platform.support.api.localization.request.PreviewKeyImportRequestBody;
import com.paytech.platform.support.api.localization.response.KeyImportItem;
import com.paytech.platform.support.api.localization.response.KeyImportPreviewResponseBody;
import com.paytech.platform.support.api.localization.response.KeyImportResultResponseBody;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.enums.FileScanType;
import com.paytech.platform.support.filedocument.integration.storage.StoredContentType;
import com.paytech.platform.support.localization.domain.LocalizationCodes;
import com.paytech.platform.support.test.base.LocalizationApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.localization.LocalizationMocker;
import com.paytech.platform.support.test.localization.LocalizationRequestBuilder;
import com.paytech.platform.support.test.mocker.local.FileMocker;
import com.paytech.platform.support.test.mocker.local.StorageInstanceMocker;

/**
 * 导入文件经真实上传链路（技术方案 3.5，B-report 7.2）：调用方经内部文件接口开始上传 application/json、按授权直传本机 MinIO、完成上传，
 * Platform 格式检查复核为 JSON 对象后，key 导入预览与确认成功。
 * <li>检查未完成时 key 导入预览与翻译导入返回 409 I18N_IMPORT_FILE_NOT_READY；带 BOM 的 JSON 在完成上传时即被拒绝。</li>
 * <li>调用方用内部文件测试身份 file-test，写入路由 file-test × PRIVATE 指向测试存储桶与允许 JSON 的测试策略，对应生产的
 * operations-console-bff × PRIVATE → OPERATIONS_BFF_IMPORT。</li>
 *
 * @see InternalFileApi#startUpload(StartFileUploadRequestBody)
 * @see InternalFileApi#completeUpload(String)
 * @see LocalizationContentApi#previewKeyImport(String, String, String, PreviewKeyImportRequestBody)
 * @see LocalizationContentApi#confirmKeyImport(String, String, String, ConfirmKeyImportRequestBody)
 * @see LocalizationContentApi#importTranslations(String, String, String, ImportTranslationsRequestBody)
 */
@TestPropertySource(properties = "internal-auth.mode=DUAL")
class LocalizationFileImportApiTest extends LocalizationApiTestBase {

    private static final String APP = LocalizationMocker.APPLICATION;

    private static final String CALLER = StorageInstanceMocker.SOURCE;

    private Operator admin;

    /** 本类公共：应用与命名空间 team；调用方的私有写入路由指向测试存储桶与测试策略。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = operationsMocker.operator(OperatorBuilder.administrator());
        long applicationId = localization.application(APP, 100, LocalizationMocker.ZH_CN);
        localization.namespace(applicationId, "team");
        storageInstances.route(CALLER, FileAccessLevel.PRIVATE, StorageInstanceMocker.MINIO_INSTANCE, storage.bucket(),
            FileMocker.POLICY_CODE);
        storageInstances.refresh();
    }

    /** 真实上传的 key 导入文件：完成上传登记为 application/json，检查通过后预览列出新增 key，确认后生效。 */
    @Test
    void test_normalFlow() {
        byte[] json = LocalizationRequestBuilder.sourceFile(APP, Map.of("team", Map.of("team.invite.submit",
            "Send invitation", "team.invite.cancel", "Cancel"))).getBytes(StandardCharsets.UTF_8);

        FileUploadCompletionResponseBody completed = upload(LocalizationCodes.KEY_IMPORT_REASON, json).assertOk()
            .as(FileUploadCompletionResponseBody.class);
        long fileId = Long.parseLong(completed.getFileId());
        awaitScan(fileId, FileScanStatus.AVAILABLE);
        var preview = post(imports() + "/previews", admin, KeyImportApiTest.previewBody(fileId)).assertOk()
            .as(KeyImportPreviewResponseBody.class);
        var result = post(imports(), admin, KeyImportApiTest.confirmBody(fileId, preview.getPreviewDigest())).assertOk()
            .as(KeyImportResultResponseBody.class);

        assertAll(() -> assertEquals(StoredContentType.JSON, fileData().get("mimeType")),
            () -> assertTrue(preview.isImportable()),
            () -> assertEquals(List.of("team.invite.cancel", "team.invite.submit"), preview.getAdded().stream()
                .map(KeyImportItem::getKeyCode).sorted().toList()),
            () -> assertEquals(1, result.getKeySetRevision()), () -> assertEquals(2, result.getAddedCount()));
    }

    /** 检查尚未完成（新修订要求一个未部署的检查器）：key 导入预览与翻译导入都返回 409 I18N_IMPORT_FILE_NOT_READY。 */
    @Test
    void test_fileNotReady() {
        fileMocker.replaceRevision(filePolicy, 2, "UT_ABSENT_SCANNER", FileScanType.ANTI_MALWARE.getCode(), "CLEAN");
        byte[] keys = LocalizationRequestBuilder.sourceFile(APP, Map.of("team", Map.of("team.invite.submit", "Send")))
            .getBytes(StandardCharsets.UTF_8);
        byte[] translations = ("{\"format\":\"code-mor-i18n-translation/1\",\"application\":\"" + APP
            + "\",\"locale\":\"zh-CN\",\"namespaces\":{\"team\":{\"team.invite.submit\":\"发送\"}}}")
            .getBytes(StandardCharsets.UTF_8);

        long keyFile = Long.parseLong(upload(LocalizationCodes.KEY_IMPORT_REASON, keys).assertOk()
            .as(FileUploadCompletionResponseBody.class).getFileId());
        long translationFile = Long.parseLong(upload(LocalizationCodes.TRANSLATION_IMPORT_REASON, translations)
            .assertOk().as(FileUploadCompletionResponseBody.class).getFileId());
        awaitScan(keyFile, FileScanStatus.SCANNING);
        awaitScan(translationFile, FileScanStatus.SCANNING);

        post(imports() + "/previews", admin, KeyImportApiTest.previewBody(keyFile))
            .assertProblem(409, LocalizationErrorCode.I18N_IMPORT_FILE_NOT_READY);
        ImportTranslationsRequestBody body = new ImportTranslationsRequestBody();
        body.setFileId(Long.toString(translationFile));
        body.setLocale(LocalizationMocker.ZH_CN);
        post(application(APP) + "/translations/imports", admin, body)
            .assertProblem(409, LocalizationErrorCode.I18N_IMPORT_FILE_NOT_READY);
    }

    /** 声明为 application/json 的带 BOM 内容按 CSV 识别，完成上传返回 422 FILE_POLICY_REJECTED，不登记文件。 */
    @Test
    void test_bomRejectedAtCompletion() {
        byte[] json = ("﻿" + LocalizationRequestBuilder.sourceFile(APP, Map.of("team",
            Map.of("team.invite.submit", "Send")))).getBytes(StandardCharsets.UTF_8);

        upload(LocalizationCodes.KEY_IMPORT_REASON, json).assertProblem(422, FileErrorCode.FILE_POLICY_REJECTED);

        assertTrue(localizationRows.uploads(LocalizationCodes.KEY_IMPORT_REASON).stream()
            .allMatch(row -> row.get("fileId") == null), "被拒绝的上传不登记文件");
    }

    /** 开始上传 → 按授权直传 → 完成上传；返回完成接口的响应。 */
    private PlatformResponseChecker upload(String reason, byte[] bytes) {
        FileUploadResponseBody started = call(HttpMethod.POST, "/internal/files/v1/uploads", new StartFileUploadRequestBody(
            reason, "i18n.json", StoredContentType.JSON, Integer.toString(bytes.length), "42", null)).assertStatus(201)
            .as(FileUploadResponseBody.class);
        List<Map<String, Object>> uploads = localizationRows.uploads(reason);
        storage.track((String) uploads.getLast().get("objectRef"));
        var authorization = started.getAuthorization();
        assertEquals(200, storage.browserPut(authorization.getUrl(), authorization.getHeaders(), bytes).statusCode());
        return call(HttpMethod.POST, "/internal/files/v1/uploads/" + started.getUploadId() + "/completion", null);
    }

    /** 有界等待文件扫描状态，并等待扫描任务停止执行。 */
    private void awaitScan(long fileId, FileScanStatus status) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50)).until(() -> localizationRows
            .uploads(LocalizationCodes.KEY_IMPORT_REASON).stream().anyMatch(row -> matches(row, fileId, status))
            || localizationRows.uploads(LocalizationCodes.TRANSLATION_IMPORT_REASON).stream()
                .anyMatch(row -> matches(row, fileId, status)));
        fileMocker.awaitScanTasks(platformDataCleaner);
    }

    /** 上传行对应该文件且扫描状态一致。 */
    private static boolean matches(Map<String, Object> row, long fileId, FileScanStatus status) {
        return row.get("fileId") != null && ((Number) row.get("fileId")).longValue() == fileId
            && status.getCode().equals(row.get("scanStatus"));
    }

    /** 最近一次 key 导入上传的登记。 */
    private Map<String, Object> fileData() {
        return localizationRows.uploads(LocalizationCodes.KEY_IMPORT_REASON).getLast();
    }

    /** key 导入路径。 */
    private static String imports() {
        return application(APP) + "/key-imports";
    }

    /** 以 X-Caller-Service 身份调用内部文件接口。 */
    private PlatformResponseChecker call(HttpMethod method, String path, Object body) {
        String id = "plt-i18n-" + UUID.randomUUID();
        HttpHeaders headers = TestRequestHeaders.internal(id);
        headers.set("X-Caller-Service", CALLER);
        return new PlatformResponseChecker(httpTestInvoker.exchange(method, path, headers, body), objectMapper, id);
    }
}
