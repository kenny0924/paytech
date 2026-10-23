package com.paytech.platform.support.auto.ext.http.localization;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.api.localization.LocalizationCatalogApi;
import com.paytech.platform.support.api.localization.request.CreateApplicationRequestBody;
import com.paytech.platform.support.api.localization.request.CreateLocaleRequestBody;
import com.paytech.platform.support.api.localization.request.CreateNamespaceRequestBody;
import com.paytech.platform.support.api.localization.request.UpdateLocaleRequestBody;
import com.paytech.platform.support.api.localization.response.ApplicationResponseBody;
import com.paytech.platform.support.api.localization.response.LocaleListResponseBody;
import com.paytech.platform.support.api.localization.response.LocaleResponseBody;
import com.paytech.platform.support.api.localization.response.NamespaceListResponseBody;
import com.paytech.platform.support.api.localization.response.NamespaceResponseBody;
import com.paytech.platform.support.localization.core.enums.LocaleStatus;
import com.paytech.platform.support.localization.domain.LocalizationCodes;
import com.paytech.platform.support.test.base.LocalizationApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.localization.LocalizationMocker;
import com.paytech.platform.support.test.localization.LocalizationRequestBuilder;

/**
 * 语言、应用与命名空间维护（需求 R1、R2、AC-01）：新增与修改、en 不可停用、被应用引用的语言不可停用、版本冲突 409、
 * 命名空间有 key 时不能删除、只读人员不能写。
 *
 * @see LocalizationCatalogApi#createLocale(String, String, CreateLocaleRequestBody)
 * @see LocalizationCatalogApi#updateLocale(String, String, String, UpdateLocaleRequestBody)
 * @see LocalizationCatalogApi#listLocales(String, String)
 * @see LocalizationCatalogApi#createApplication(String, String, CreateApplicationRequestBody)
 * @see LocalizationCatalogApi#createNamespace(String, String, String, CreateNamespaceRequestBody)
 * @see LocalizationCatalogApi#listNamespaces(String, String, String)
 * @see LocalizationCatalogApi#deleteNamespace(String, String, String, String, Integer)
 */
class LocalizationCatalogApiTest extends LocalizationApiTestBase {

    private Operator admin;

    /** 本类公共：持有全部目录权限的平台管理员。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = operationsMocker.operator(OperatorBuilder.administrator());
    }

    /** 新增语言为 ENABLED v1；修改后 v2；列表按排序值排列。 */
    @Test
    void test_localeCreateAndUpdate() {
        var created = post(BASE + "/locales", admin, LocalizationRequestBuilder.createLocale("fr", "Français"))
            .assertOk().as(LocaleResponseBody.class);
        var updated = put(BASE + "/locales/fr", admin, LocalizationRequestBuilder.updateLocale(1, "Français (FR)",
            LocaleStatus.DISABLED)).assertOk().as(LocaleResponseBody.class);
        var listed = get(BASE + "/locales", admin).assertOk().as(LocaleListResponseBody.class);
        assertAll(() -> assertEquals(LocaleStatus.ENABLED.getCode(), created.getStatus()),
            () -> assertEquals(1, created.getVersion()),
            () -> assertEquals(LocaleStatus.DISABLED.getCode(), updated.getStatus()),
            () -> assertEquals(2, updated.getVersion()),
            () -> assertEquals(List.of(LocalizationCodes.BASE_LOCALE, LocalizationMocker.ZH_CN, "fr"),
                listed.getItems().stream().map(LocaleResponseBody::getLocaleCode).toList()));
    }

    /** en 不能停用；被应用用作必须覆盖语言的 zh-CN 不能停用且保持原状态；版本不符 409；重复代码 400。 */
    @Test
    void test_localeRules() {
        localization.application(LocalizationMocker.APPLICATION, 100, LocalizationMocker.ZH_CN);

        put(BASE + "/locales/en", admin, LocalizationRequestBuilder.updateLocale(1, "English", LocaleStatus.DISABLED))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        var used = put(BASE + "/locales/zh-CN", admin, LocalizationRequestBuilder.updateLocale(1, "简体中文",
            LocaleStatus.DISABLED)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        put(BASE + "/locales/zh-CN", admin, LocalizationRequestBuilder.updateLocale(9, "简体中文", LocaleStatus.ENABLED))
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        post(BASE + "/locales", admin, LocalizationRequestBuilder.createLocale("en", "English"))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        Map<String, Object> zh = localizationRows.locale(LocalizationMocker.ZH_CN);
        assertAll(() -> assertTrue(used.json().path("detail").asString().contains(LocalizationMocker.APPLICATION)),
            () -> assertEquals(LocaleStatus.ENABLED.getCode(), zh.get("status")),
            () -> assertEquals(1, ((Number) zh.get("version")).intValue()));
    }

    /** 新增应用：默认与必须覆盖语言须为 ENABLED；不存在的语言返回 400 且不落库。 */
    @Test
    void test_applicationCreate() {
        var created = post(BASE + "/applications", admin, LocalizationRequestBuilder.createApplication("checkout",
            LocalizationMocker.ZH_CN)).assertOk().as(ApplicationResponseBody.class);
        post(BASE + "/applications", admin, LocalizationRequestBuilder.createApplication("partner-console", "ja"))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        assertAll(() -> assertEquals(List.of(LocalizationMocker.ZH_CN), created.getRequiredLocales()),
            () -> assertEquals(1, created.getVersion()), () -> assertEquals(null, created.getCurrentReleaseNo()),
            () -> assertEquals(null, localizationRows.application("partner-console")));
    }

    /** 命名空间：新建、列表含 key 数；有 key 时不能删除，空命名空间按版本删除。 */
    @Test
    void test_namespaces() {
        long applicationId = localization.application(LocalizationMocker.APPLICATION, 100);
        long team = localization.namespace(applicationId, "team");
        localization.key(applicationId, team, "team.invite.submit", "Send invitation");
        String namespaces = application(LocalizationMocker.APPLICATION) + "/namespaces";

        var created = post(namespaces, admin, LocalizationRequestBuilder.createNamespace("billing")).assertOk()
            .as(NamespaceResponseBody.class);
        var listed = get(namespaces, admin).assertOk().as(NamespaceListResponseBody.class);
        delete(namespaces + "/team?expectedVersion=1", admin).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        delete(namespaces + "/billing?expectedVersion=1", admin).assertNoContent();

        assertAll(() -> assertEquals(1, created.getVersion()),
            () -> assertEquals(List.of(1L, 0L), listed.getItems().stream()
                .map(NamespaceResponseBody::getActiveKeyCount).toList()),
            () -> assertEquals(1, localizationRows.namespaceCount(applicationId, "team")),
            () -> assertEquals(0, localizationRows.namespaceCount(applicationId, "billing")));
    }

    /** 只读人员可以查看，不能写入（403）。 */
    @Test
    void test_viewerCannotWrite() {
        Operator viewer = operationsMocker.operator(OperatorBuilder.viewer());
        get(BASE + "/locales", viewer).assertOk();
        post(BASE + "/locales", viewer, LocalizationRequestBuilder.createLocale("fr", "Français"))
            .assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
    }
}
