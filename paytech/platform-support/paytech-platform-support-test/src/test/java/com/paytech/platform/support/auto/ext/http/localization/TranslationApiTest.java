package com.paytech.platform.support.auto.ext.http.localization;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.LocalizationErrorCode;
import com.paytech.platform.support.api.localization.LocalizationContentApi;
import com.paytech.platform.support.api.localization.request.ImportTranslationsRequestBody;
import com.paytech.platform.support.api.localization.request.SaveTranslationsRequestBody;
import com.paytech.platform.support.api.localization.response.ImportTranslationsResponseBody;
import com.paytech.platform.support.api.localization.response.SaveTranslationsResponseBody;
import com.paytech.platform.support.api.localization.response.TranslationEntryResponseBody;
import com.paytech.platform.support.api.localization.response.TranslationFileResponseBody;
import com.paytech.platform.support.localization.core.enums.KeyStatus;
import com.paytech.platform.support.localization.core.enums.TranslationState;
import com.paytech.platform.support.localization.domain.LocalizationCodes;
import com.paytech.platform.support.test.base.LocalizationApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.localization.LocalizationMocker;
import com.paytech.platform.support.test.localization.LocalizationRequestBuilder;

import tools.jackson.core.type.TypeReference;

/**
 * 翻译维护（需求 R4、AC-03、AC-09）：批量保存与 ICU 拒绝（字段级位置）、逐条版本冲突、工作台按填写状态筛选、导出再原样导入无写入。
 *
 * @see LocalizationContentApi#saveTranslations(String, String, String, SaveTranslationsRequestBody)
 * @see LocalizationContentApi#listTranslations(String, String, String, String, String, String, String, Integer, Integer)
 * @see LocalizationContentApi#exportTranslations(String, String, String, String)
 * @see LocalizationContentApi#importTranslations(String, String, String, ImportTranslationsRequestBody)
 */
class TranslationApiTest extends LocalizationApiTestBase {

    private Operator admin;

    private long applicationId;

    private long submitKey;

    /** 本类公共：应用 client-console 的 team 命名空间下三个 key，submit 已有中文翻译，count 的基准已变更。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = operationsMocker.operator(OperatorBuilder.administrator());
        applicationId = localization.application(LocalizationMocker.APPLICATION, 100, LocalizationMocker.ZH_CN);
        long team = localization.namespace(applicationId, "team");
        submitKey = localization.key(applicationId, team, "team.invite.submit", "Send invitation");
        localization.translation(submitKey, LocalizationMocker.ZH_CN, "发送邀请", 1);
        localization.key(applicationId, team, "team.invite.hello", "Hello {name}");
        long countKey = localization.key(applicationId, team, "team.member.count",
            "{count, plural, one {# member} other {# members}}", KeyStatus.ACTIVE, 2);
        localization.translation(countKey, LocalizationMocker.ZH_CN, "{count, plural, other {# 位成员}}", 1);
    }

    /** 新建与按版本更新一次保存；保存后依据当前基准，文案修订号加一。 */
    @Test
    void test_saveTranslations() {
        var saved = post(translations() + "/batch", admin, LocalizationRequestBuilder.save(LocalizationMocker.ZH_CN,
            LocalizationRequestBuilder.translation("team.invite.hello", "你好，{name}", 0),
            LocalizationRequestBuilder.translation("team.member.count", "{count, plural, other {# 名成员}}", 1)))
            .assertOk().as(SaveTranslationsResponseBody.class);
        Map<String, Object> count = localizationRows.translation(applicationId, "team.member.count",
            LocalizationMocker.ZH_CN);
        assertAll(() -> assertEquals(List.of(1, 2), saved.getTranslations().stream().map(item -> item.getVersion())
                .toList()),
            () -> assertEquals(1, saved.getContentRevision()),
            () -> assertEquals(2, ((Number) count.get("basedOnSourceRevision")).intValue()),
            () -> assertEquals("你好，{name}", localizationRows.translation(applicationId, "team.invite.hello",
                LocalizationMocker.ZH_CN).get("text")));
    }

    /** ICU 错误与参数不一致整体拒绝（422 I18N_MESSAGE_INVALID），errors 按 translations[i].text 定位并带字符位置；没有任何写入。 */
    @Test
    void test_icuRejected() {
        var rejected = post(translations() + "/batch", admin, LocalizationRequestBuilder.save(LocalizationMocker.ZH_CN,
            LocalizationRequestBuilder.translation("team.invite.hello", "你好", 0),
            LocalizationRequestBuilder.translation("team.invite.submit", "发送 {name", 1)))
            .assertProblem(422, LocalizationErrorCode.I18N_MESSAGE_INVALID);
        var errors = rejected.json().path("errors");
        assertAll(() -> assertEquals(2, errors.size()),
            () -> assertEquals("translations[0].text", errors.get(0).path("field").asString()),
            () -> assertTrue(errors.get(0).path("message").asString().contains("缺少参数 {name}")),
            () -> assertEquals("translations[1].text", errors.get(1).path("field").asString()),
            () -> assertTrue(errors.get(1).path("message").asString().startsWith("第 4 个字符")),
            () -> assertNull(localizationRows.translation(applicationId, "team.invite.hello", LocalizationMocker.ZH_CN)),
            () -> assertEquals(0, ((Number) localizationRows.application(LocalizationMocker.APPLICATION)
                .get("contentRevision")).intValue()));
    }

    /** 任一条版本不符整体 409，已有翻译不变。 */
    @Test
    void test_versionConflict() {
        post(translations() + "/batch", admin, LocalizationRequestBuilder.save(LocalizationMocker.ZH_CN,
            LocalizationRequestBuilder.translation("team.invite.hello", "你好，{name}", 0),
            LocalizationRequestBuilder.translation("team.invite.submit", "发出邀请", 3)))
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        assertEquals("发送邀请", localizationRows.translation(applicationId, "team.invite.submit",
            LocalizationMocker.ZH_CN).get("text"));
    }

    /** 工作台按填写状态筛选：未翻译、基准已变更、已翻译各一条。 */
    @Test
    void test_workbenchStates() {
        assertAll(
            () -> assertEquals(List.of("team.invite.hello"), entries(TranslationState.UNTRANSLATED)),
            () -> assertEquals(List.of("team.member.count"), entries(TranslationState.SOURCE_CHANGED)),
            () -> assertEquals(List.of("team.invite.submit"), entries(TranslationState.TRANSLATED)));
    }

    /** 导出全部 ACTIVE key（未翻译为空字符串）；原样导入不写入、修订号不变。 */
    @Test
    void test_exportThenImportUnchanged() {
        var exported = get(translations() + "/export?locale=" + LocalizationMocker.ZH_CN, admin).assertOk();
        TranslationFileResponseBody file = exported.as(TranslationFileResponseBody.class);
        long fileId = localization.importFile(LocalizationCodes.TRANSLATION_IMPORT_REASON, exported.text(),
            FileScanStatus.AVAILABLE);
        ImportTranslationsRequestBody body = new ImportTranslationsRequestBody();
        body.setFileId(Long.toString(fileId));
        body.setLocale(LocalizationMocker.ZH_CN);

        var imported = post(translations() + "/imports", admin, body).assertOk()
            .as(ImportTranslationsResponseBody.class);

        assertAll(() -> assertEquals("", file.getNamespaces().get("team").get("team.invite.hello")),
            () -> assertEquals(3, file.getNamespaces().get("team").size()),
            () -> assertEquals(0, imported.getUpdatedCount()), () -> assertEquals(2, imported.getUnchangedCount()),
            () -> assertEquals(1, imported.getEmptyCount()), () -> assertEquals(0, imported.getContentRevision()));
    }

    /** 翻译路径。 */
    static String translations() {
        return application(LocalizationMocker.APPLICATION) + "/translations";
    }

    /** 某填写状态的 key。 */
    List<String> entries(TranslationState state) {
        PagingResponseBody<TranslationEntryResponseBody> page = get(translations() + "?locale="
            + LocalizationMocker.ZH_CN + "&state=" + state.getCode(), admin).assertOk()
            .as(new TypeReference<PagingResponseBody<TranslationEntryResponseBody>>() {
            });
        assertFalse(page.items().stream().anyMatch(entry -> entry.getParameters() == null));
        return page.items().stream().map(TranslationEntryResponseBody::getKeyCode).toList();
    }
}
