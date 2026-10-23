package com.paytech.platform.support.auto.ext.http.localization;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.LocalizationErrorCode;
import com.paytech.platform.support.api.localization.LocalizationContentApi;
import com.paytech.platform.support.api.localization.request.ConfirmKeyImportRequestBody;
import com.paytech.platform.support.api.localization.request.PreviewKeyImportRequestBody;
import com.paytech.platform.support.api.localization.response.KeyImportItem;
import com.paytech.platform.support.api.localization.response.KeyImportPreviewResponseBody;
import com.paytech.platform.support.api.localization.response.KeyImportResultResponseBody;
import com.paytech.platform.support.localization.core.enums.KeyStatus;
import com.paytech.platform.support.localization.domain.LocalizationCodes;
import com.paytech.platform.support.test.base.LocalizationApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.localization.LocalizationMocker;
import com.paytech.platform.support.test.localization.LocalizationRequestBuilder;

/**
 * key 导入（技术方案 3.5、需求 B1、AC-02）：预览四类差异与摘要；确认后一个事务内生效、两个修订号各加一；预览后有人导入过返回 409；
 * 未创建的命名空间预览不可导入、确认返回 422；文件原因码不符返回 400。
 *
 * @see LocalizationContentApi#previewKeyImport(String, String, String, PreviewKeyImportRequestBody)
 * @see LocalizationContentApi#confirmKeyImport(String, String, String, ConfirmKeyImportRequestBody)
 */
class KeyImportApiTest extends LocalizationApiTestBase {

    private Operator admin;

    private long applicationId;

    private long teamId;

    /** 本类公共：管理员、应用 client-console 与命名空间 team，已有三个 key（其一已废弃）。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = operationsMocker.operator(OperatorBuilder.administrator());
        applicationId = localization.application(LocalizationMocker.APPLICATION, 100, LocalizationMocker.ZH_CN);
        teamId = localization.namespace(applicationId, "team");
        localization.key(applicationId, teamId, "team.invite.submit", "Send invitation");
        localization.key(applicationId, teamId, "team.invite.cancel", "Cancel");
        localization.key(applicationId, teamId, "team.member.count", "{count} members", KeyStatus.DEPRECATED, 1);
    }

    /** 预览四类差异；确认后新增、基准变更（修订号 + 1）、废弃与恢复生效，修订号各加一。 */
    @Test
    void test_previewAndConfirm() {
        long fileId = sourceFile(Map.of("team", Map.of("team.invite.cancel", "Cancel invitation",
            "team.member.count", "{count} people", "team.invite.email", "Email")));

        var preview = post(imports() + "/previews", admin, previewBody(fileId)).assertOk()
            .as(KeyImportPreviewResponseBody.class);
        var result = post(imports(), admin, confirmBody(fileId, preview.getPreviewDigest())).assertOk()
            .as(KeyImportResultResponseBody.class);

        Map<String, Map<String, Object>> keys = keysByCode();
        Map<String, Object> application = localizationRows.application(LocalizationMocker.APPLICATION);
        assertAll(() -> assertTrue(preview.isImportable()),
            () -> assertEquals(List.of("team.invite.email"), codes(preview.getAdded())),
            () -> assertEquals(List.of("team.invite.cancel"), codes(preview.getSourceChanged())),
            () -> assertEquals(List.of("team.invite.submit"), codes(preview.getDeprecated())),
            () -> assertEquals(List.of("team.member.count"), codes(preview.getRestored())),
            () -> assertEquals(1, result.getKeySetRevision()), () -> assertEquals(1, result.getContentRevision()),
            () -> assertEquals(KeyStatus.DEPRECATED.getCode(), keys.get("team.invite.submit").get("status")),
            () -> assertEquals(2, ((Number) keys.get("team.invite.cancel").get("sourceRevision")).intValue()),
            () -> assertEquals(KeyStatus.ACTIVE.getCode(), keys.get("team.member.count").get("status")),
            () -> assertEquals("Email", keys.get("team.invite.email").get("sourceText")),
            () -> assertEquals(1, ((Number) application.get("keySetRevision")).intValue()),
            () -> assertEquals(1, ((Number) application.get("contentRevision")).intValue()));
    }

    /** 预览后另一次导入已确认：原摘要确认返回 409，key 集合不再变化。 */
    @Test
    void test_stalePreview() {
        long first = sourceFile(Map.of("team", Map.of("team.invite.submit", "Send")));
        long second = sourceFile(Map.of("team", Map.of("team.invite.submit", "Send now")));
        var stale = post(imports() + "/previews", admin, previewBody(second)).assertOk()
            .as(KeyImportPreviewResponseBody.class);
        var fresh = post(imports() + "/previews", admin, previewBody(first)).assertOk()
            .as(KeyImportPreviewResponseBody.class);
        post(imports(), admin, confirmBody(first, fresh.getPreviewDigest())).assertOk();

        post(imports(), admin, confirmBody(second, stale.getPreviewDigest()))
            .assertProblem(409, LocalizationErrorCode.I18N_IMPORT_PREVIEW_STALE);
        assertEquals("Send", keysByCode().get("team.invite.submit").get("sourceText"));
    }

    /** 文件含未创建的命名空间：预览不可导入并列出；确认返回 422 I18N_NAMESPACE_NOT_FOUND，不写库。 */
    @Test
    void test_missingNamespace() {
        long fileId = sourceFile(Map.of("team", Map.of("team.invite.submit", "Send invitation"),
            "billing", Map.of("billing.title", "Billing")));
        var preview = post(imports() + "/previews", admin, previewBody(fileId)).assertOk()
            .as(KeyImportPreviewResponseBody.class);
        var rejected = post(imports(), admin, confirmBody(fileId, preview.getPreviewDigest()))
            .assertProblem(422, LocalizationErrorCode.I18N_NAMESPACE_NOT_FOUND);
        assertAll(() -> assertFalse(preview.isImportable()),
            () -> assertEquals(List.of("billing"), preview.getMissingNamespaces()),
            () -> assertEquals("namespaces.billing", rejected.json().path("errors").get(0).path("field").asString()),
            () -> assertEquals(0, ((Number) localizationRows.application(LocalizationMocker.APPLICATION)
                .get("keySetRevision")).intValue()));
    }

    /** 原因码不是 I18N_KEY_IMPORT 或检查被拒绝返回 400；仍在检查返回 409 I18N_IMPORT_FILE_NOT_READY。 */
    @Test
    void test_invalidFile() {
        long wrongReason = localization.importFile(LocalizationCodes.TRANSLATION_IMPORT_REASON,
            LocalizationRequestBuilder.sourceFile(LocalizationMocker.APPLICATION, Map.of()), FileScanStatus.AVAILABLE);
        long scanning = localization.importFile(LocalizationCodes.KEY_IMPORT_REASON,
            LocalizationRequestBuilder.sourceFile(LocalizationMocker.APPLICATION, Map.of()), FileScanStatus.SCANNING);
        long rejected = localization.importFile(LocalizationCodes.KEY_IMPORT_REASON,
            LocalizationRequestBuilder.sourceFile(LocalizationMocker.APPLICATION, Map.of()), FileScanStatus.REJECTED);
        post(imports() + "/previews", admin, previewBody(wrongReason))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        post(imports() + "/previews", admin, previewBody(scanning))
            .assertProblem(409, LocalizationErrorCode.I18N_IMPORT_FILE_NOT_READY);
        post(imports() + "/previews", admin, previewBody(rejected))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
    }

    /** 导入路径。 */
    static String imports() {
        return application(LocalizationMocker.APPLICATION) + "/key-imports";
    }

    /** 写入一个检查通过的 key 导入文件。 */
    long sourceFile(Map<String, Map<String, String>> namespaces) {
        return localization.importFile(LocalizationCodes.KEY_IMPORT_REASON,
            LocalizationRequestBuilder.sourceFile(LocalizationMocker.APPLICATION, namespaces), FileScanStatus.AVAILABLE);
    }

    /** 预览请求。 */
    static PreviewKeyImportRequestBody previewBody(long fileId) {
        PreviewKeyImportRequestBody body = new PreviewKeyImportRequestBody();
        body.setFileId(Long.toString(fileId));
        return body;
    }

    /** 确认请求。 */
    static ConfirmKeyImportRequestBody confirmBody(long fileId, String digest) {
        ConfirmKeyImportRequestBody body = new ConfirmKeyImportRequestBody();
        body.setFileId(Long.toString(fileId));
        body.setPreviewDigest(digest);
        return body;
    }

    /** 预览项的 key。 */
    static List<String> codes(List<KeyImportItem> items) {
        return items.stream().map(KeyImportItem::getKeyCode).toList();
    }

    /** 应用全部 key，按完整 key 索引。 */
    Map<String, Map<String, Object>> keysByCode() {
        return localizationRows.keys(applicationId).stream()
            .collect(java.util.stream.Collectors.toMap(row -> (String) row.get("keyCode"), row -> row));
    }
}
