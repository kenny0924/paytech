package com.paytech.platform.support.auto.ext.http.localization;

import static com.paytech.platform.support.localization.core.enums.ReleaseChangeType.ADDED;
import static com.paytech.platform.support.localization.core.enums.ReleaseChangeType.CHANGED;
import static com.paytech.platform.support.localization.core.enums.ReleaseChangeType.REMOVED;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.localization.LocalizationReleaseApi;
import com.paytech.platform.support.api.localization.request.ActivateReleaseRequestBody;
import com.paytech.platform.support.api.localization.request.ConfirmKeyImportRequestBody;
import com.paytech.platform.support.api.localization.request.PreviewKeyImportRequestBody;
import com.paytech.platform.support.api.localization.request.PublishReleaseRequestBody;
import com.paytech.platform.support.api.localization.response.KeyImportPreviewResponseBody;
import com.paytech.platform.support.api.localization.response.LocaleTranslationChange;
import com.paytech.platform.support.api.localization.response.ReleaseChangeSummary;
import com.paytech.platform.support.api.localization.response.ReleaseDiffResponseBody;
import com.paytech.platform.support.api.localization.response.ReleasePrecheckResponseBody;
import com.paytech.platform.support.api.localization.response.ReleaseSummaryResponseBody;
import com.paytech.platform.support.api.localization.response.ReleaseTextChange;
import com.paytech.platform.support.core.integration.localization.FileServiceReleaseBundleStore;
import com.paytech.platform.support.localization.core.enums.LocaleStatus;
import com.paytech.platform.support.localization.core.enums.ReleaseChangeType;
import com.paytech.platform.support.localization.domain.LocalizationCodes;
import com.paytech.platform.support.test.base.LocalizationApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.localization.LocalizationMocker;
import com.paytech.platform.support.test.localization.LocalizationRequestBuilder;

/**
 * 发布变更明细（需求 20261030 AC-01—AC-06、AC-09）：发布在登记事务内写入相对上一发布号的文案变化与变化条数；预检、发布摘要与版本差异
 * 由明细还原发布内容后按文字比较，不读语言包文件。
 * <li>首次发布为全量基线；之后只记新增、修改、移除；改了又改回不算变化；语言停用 / 启用按整个语言移除 / 新增；key 废弃后恢复按文字比较；
 * 切回旧发布后预检与旧发布比较，而新发布的明细仍相对上一发布号；任意基准的差异；存在未补录明细的发布时返回服务端错误。</li>
 * <li>预检与差异期间不读取语言包文件：存储凭据的 Vault Reveal 次数不变；发布期间的 Reveal 次数只随本次语言包写入与检查变化。</li>
 *
 * @see LocalizationReleaseApi#precheckRelease(String, String, String)
 * @see LocalizationReleaseApi#publishRelease(String, String, String, PublishReleaseRequestBody)
 * @see LocalizationReleaseApi#diffRelease(String, String, String, Integer, Integer)
 * @see LocalizationReleaseApi#activateRelease(String, String, String, ActivateReleaseRequestBody)
 */
class ReleaseChangeApiTest extends LocalizationApiTestBase {

    private static final String APP = LocalizationMocker.APPLICATION;

    private static final String ZH = LocalizationMocker.ZH_CN;

    private static final String EN = LocalizationCodes.BASE_LOCALE;

    private Operator admin;

    private long applicationId;

    /** 本类公共：公开桶与写入路由；应用不要求覆盖率，team 三个 key（a、b、c），a、b 已有中文翻译。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = operationsMocker.operator(OperatorBuilder.administrator());
        publicBundleBucket();
        applicationId = localization.application(APP, 0);
        long team = localization.namespace(applicationId, "team");
        localization.translation(localization.key(applicationId, team, "team.a", "Alpha"), ZH, "阿尔法", 1);
        localization.translation(localization.key(applicationId, team, "team.b", "Beta"), ZH, "贝塔", 1);
        localization.key(applicationId, team, "team.c", "Gamma");
    }

    /**
     * T1、T7（无基准）、T8：首次发布写全量基线，全部为 ADDED，条数 = en 3 + 已填中文 2，change_count 一致；发布 1 的差异没有基准、全部新增。
     * 预检与差异期间 Vault Reveal 为 0。
     */
    @Test
    void test_firstReleaseBaseline() {
        var precheck = withoutVaultReveal(this::precheck);
        ReleaseSummaryResponseBody published = publishAndActivate();
        var diff = withoutVaultReveal(() -> diff(1, null));

        assertAll(() -> assertNull(precheck.getChangeSummary().getBaseReleaseNo()),
            () -> assertEquals(3, precheck.getChangeSummary().getAddedKeyCount()),
            () -> assertEquals(3, published.getChangeSummary().getAddedKeyCount()),
            () -> assertEquals(List.of(
                change(1, EN, "team.a", ADDED, "Alpha"),
                change(1, EN, "team.b", ADDED, "Beta"),
                change(1, EN, "team.c", ADDED, "Gamma"),
                change(1, ZH, "team.a", ADDED, "阿尔法"),
                change(1, ZH, "team.b", ADDED, "贝塔")), changes()),
            () -> assertEquals(5, changeCount(1)),
            () -> assertNull(diff.getBaseReleaseNo()),
            () -> assertEquals(List.of("en:team.a:null->Alpha", "en:team.b:null->Beta", "en:team.c:null->Gamma",
                "zh-CN:team.a:null->阿尔法", "zh-CN:team.b:null->贝塔"), describe(diff.getAdded())),
            () -> assertTrue(diff.getChanged().isEmpty() && diff.getRemoved().isEmpty()));
    }

    /**
     * T1（大基线）：首次发布超过 Snowflake 单毫秒 4096 个序列的变化行（2100 个 key 各有中文，共 4205 行）分批分配主键并写入，
     * 条数与 change_count 一致。
     */
    @Test
    void test_largeBaseline() {
        long bulk = localization.namespace(applicationId, "bulk");
        for (int i = 0; i < 2100; i++) {
            localization.translation(localization.key(applicationId, bulk, "bulk.k" + i, "Text " + i), ZH, "文 " + i, 1);
        }

        publishAndActivate();

        List<String> rows = changes(1);
        assertAll(() -> assertEquals(4205, rows.size()), () -> assertEquals(4205, changeCount(1)),
            () -> assertTrue(rows.contains(change(1, ZH, "bulk.k2099", ADDED, "文 2099"))));
    }

    /**
     * T2、T7（默认上一版）、T8：改英文基准（a）、废弃 key（b）、新增 key（d）、补中文翻译（c）后再发布，只写变化的 5 行：CHANGED 带改后文字，
     * REMOVED 文字为空；预检与发布摘要与之一致；默认基准的差异给出改前改后文字。两次发布的 Reveal 次数相同（不再读取线上发布的语言包）。
     */
    @Test
    void test_changesSinceLastRelease() {
        int firstPublish = vaultRevealsDuring(this::publishAndActivate);
        importKeys(Map.of("team.a", "Alpha 2", "team.c", "Gamma", "team.d", "Delta"));
        saveZh("team.c", "伽马", 0);

        var precheck = withoutVaultReveal(this::precheck);
        int secondPublish = vaultRevealsDuring(this::publishAndActivate);
        var diff = withoutVaultReveal(() -> diff(2, null));

        ReleaseChangeSummary summary = precheck.getChangeSummary();
        assertAll(() -> assertEquals(1, summary.getBaseReleaseNo()),
            () -> assertEquals(List.of("team.d"), summary.getAddedKeys()),
            () -> assertEquals(List.of("team.b"), summary.getDeprecatedKeys()),
            () -> assertEquals(List.of("team.a"), summary.getSourceChangedKeys()),
            () -> assertEquals(List.of("zh-CN:2:[team.b, team.c]"), describeLocales(summary)),
            () -> assertEquals(List.of(
                change(2, EN, "team.a", CHANGED, "Alpha 2"),
                change(2, EN, "team.b", REMOVED, null),
                change(2, EN, "team.d", ADDED, "Delta"),
                change(2, ZH, "team.b", REMOVED, null),
                change(2, ZH, "team.c", ADDED, "伽马")), changes(2)),
            () -> assertEquals(5, changeCount(2)),
            () -> assertEquals(1, diff.getBaseReleaseNo()),
            () -> assertEquals(List.of("en:team.d:null->Delta", "zh-CN:team.c:null->伽马"), describe(diff.getAdded())),
            () -> assertEquals(List.of("en:team.a:Alpha->Alpha 2"), describe(diff.getChanged())),
            () -> assertEquals(List.of("en:team.b:Beta->null", "zh-CN:team.b:贝塔->null"), describe(diff.getRemoved())),
            () -> assertTrue(firstPublish > 0, "语言包写入与检查经 Vault 读取存储凭据，观测点有效"),
            () -> assertEquals(firstPublish, secondPublish, "第二次发布不应多读第一次发布的语言包"));
    }

    /** T3：译文改了又改回原样，预检没有任何变化；再发布 change_count 为 0、不写变化行，差异为空。 */
    @Test
    void test_revertedChangeNotCounted() {
        publishAndActivate();
        saveZh("team.a", "阿尔法 X", 1);
        saveZh("team.a", "阿尔法", 2);

        var precheck = precheck();
        ReleaseSummaryResponseBody published = publishAndActivate();
        var diff = diff(2, null);

        assertAll(() -> assertEmptySummary(precheck.getChangeSummary(), 1),
            () -> assertEmptySummary(published.getChangeSummary(), 1),
            () -> assertEquals(0, changeCount(2)), () -> assertEquals(List.of(), changes(2)),
            () -> assertTrue(diff.getAdded().isEmpty() && diff.getChanged().isEmpty() && diff.getRemoved().isEmpty()));
    }

    /** T4、T7（任意基准）：停用 zh-CN 后整个语言计为移除，再启用后计为新增；发布 3 相对发布 1 没有差异。 */
    @Test
    void test_localeDisabledAndEnabled() {
        publishAndActivate();
        updateZh(1, LocaleStatus.DISABLED);
        var disabled = precheck();
        publishAndActivate();
        updateZh(2, LocaleStatus.ENABLED);
        var enabled = precheck();
        publishAndActivate();
        var sinceFirst = diff(3, 1);

        assertAll(() -> assertEquals(List.of("zh-CN:2:[team.a, team.b]"), describeLocales(disabled.getChangeSummary())),
            () -> assertEquals(0, disabled.getChangeSummary().getAddedKeyCount()
                + disabled.getChangeSummary().getDeprecatedKeyCount()),
            () -> assertEquals(List.of(
                change(2, ZH, "team.a", REMOVED, null),
                change(2, ZH, "team.b", REMOVED, null)), changes(2)),
            () -> assertEquals(2, enabled.getChangeSummary().getBaseReleaseNo()),
            () -> assertEquals(List.of("zh-CN:2:[team.a, team.b]"), describeLocales(enabled.getChangeSummary())),
            () -> assertEquals(List.of(
                change(3, ZH, "team.a", ADDED, "阿尔法"),
                change(3, ZH, "team.b", ADDED, "贝塔")), changes(3)),
            () -> assertEquals(1, sinceFirst.getBaseReleaseNo()),
            () -> assertTrue(sinceFirst.getAdded().isEmpty() && sinceFirst.getChanged().isEmpty()
                && sinceFirst.getRemoved().isEmpty()));
    }

    /**
     * T5：key 废弃后发布，再以同样文字恢复：预检相对线上发布 2 计为新增 key 与一条中文变化；发布 3 写回 en / zh-CN 两行 ADDED；
     * 发布 3 相对发布 1 没有差异。
     */
    @Test
    void test_keyDeprecatedThenRestored() {
        publishAndActivate();
        importKeys(Map.of("team.a", "Alpha", "team.c", "Gamma"));
        var deprecated = precheck();
        publishAndActivate();
        importKeys(Map.of("team.a", "Alpha", "team.b", "Beta", "team.c", "Gamma"));
        var restored = precheck();
        publishAndActivate();
        var sinceFirst = diff(3, 1);

        assertAll(() -> assertEquals(List.of("team.b"), deprecated.getChangeSummary().getDeprecatedKeys()),
            () -> assertEquals(List.of("team.b"), restored.getChangeSummary().getAddedKeys()),
            () -> assertEquals(List.of("zh-CN:1:[team.b]"), describeLocales(restored.getChangeSummary())),
            () -> assertEquals(List.of(
                change(3, EN, "team.b", ADDED, "Beta"),
                change(3, ZH, "team.b", ADDED, "贝塔")), changes(3)),
            () -> assertTrue(sinceFirst.getAdded().isEmpty() && sinceFirst.getChanged().isEmpty()
                && sinceFirst.getRemoved().isEmpty()));
    }

    /**
     * T6：发布 2 后切回发布 1，预检与发布 1 比较（一条中文变化）；随后的发布 3 摘要同样相对线上发布 1，而它的变化行相对发布号 2，
     * 内容相同，change_count 为 0。
     */
    @Test
    void test_precheckAfterRollback() {
        publishAndActivate();
        saveZh("team.a", "阿尔法 2", 1);
        publishAndActivate();
        post(application(APP) + "/current-release", admin, activateBody(1)).assertOk();

        var precheck = precheck();
        ReleaseSummaryResponseBody published = publishAndActivate();

        assertAll(() -> assertEquals(1, precheck.getChangeSummary().getBaseReleaseNo()),
            () -> assertEquals(List.of("zh-CN:1:[team.a]"), describeLocales(precheck.getChangeSummary())),
            () -> assertEquals(1, published.getChangeSummary().getBaseReleaseNo()),
            () -> assertEquals(List.of("zh-CN:1:[team.a]"), describeLocales(published.getChangeSummary())),
            () -> assertEquals(List.of(
                change(2, ZH, "team.a", CHANGED, "阿尔法 2")), changes(2)),
            () -> assertEquals(0, changeCount(3)), () -> assertEquals(List.of(), changes(3)));
    }

    /**
     * T9：存在未补录变更明细（change_count 为 NULL）的发布时，预检、差异与发布都返回 500，不给出摘要；发布不上传语言包、不登记新发布。
     */
    @Test
    void test_unrecordedRelease() {
        publishAndActivate();
        assertEquals(1, localizationRows.clearChangeCount(releaseId(1)));
        int uploads = localizationRows.uploads(FileServiceReleaseBundleStore.REASON_CODE).size();

        get(application(APP) + "/release-precheck", admin).assertProblem(500, CommonErrorCode.INTERNAL_ERROR);
        get(application(APP) + "/releases/1/diff", admin).assertProblem(500, CommonErrorCode.INTERNAL_ERROR);
        post(application(APP) + "/releases", admin, new PublishReleaseRequestBody())
            .assertProblem(500, CommonErrorCode.INTERNAL_ERROR);

        assertAll(() -> assertEquals(1, localizationRows.releaseCount(applicationId)),
            () -> assertEquals(uploads, localizationRows.uploads(FileServiceReleaseBundleStore.REASON_CODE).size()));
    }

    /** 预检。 */
    private ReleasePrecheckResponseBody precheck() {
        return get(application(APP) + "/release-precheck", admin).assertOk().as(ReleasePrecheckResponseBody.class);
    }

    /** 发布并等上线任务执行，成为当前发布。 */
    private ReleaseSummaryResponseBody publishAndActivate() {
        ReleaseSummaryResponseBody published = post(application(APP) + "/releases", admin,
            new PublishReleaseRequestBody()).assertOk().as(ReleaseSummaryResponseBody.class);
        runActivations();
        return published;
    }

    /** 差异；基准为空时用缺省基准。 */
    private ReleaseDiffResponseBody diff(int releaseNo, Integer baseReleaseNo) {
        String query = baseReleaseNo == null ? "" : "?baseReleaseNo=" + baseReleaseNo;
        return get(application(APP) + "/releases/" + releaseNo + "/diff" + query, admin).assertOk()
            .as(ReleaseDiffResponseBody.class);
    }

    /** 经 key 导入预览与确认替换 team 的全部 key（文件中没有的 key 被废弃）。 */
    private void importKeys(Map<String, String> keys) {
        long fileId = localization.importFile(LocalizationCodes.KEY_IMPORT_REASON,
            LocalizationRequestBuilder.sourceFile(APP, Map.of("team", new LinkedHashMap<>(keys))),
            FileScanStatus.AVAILABLE);
        PreviewKeyImportRequestBody preview = new PreviewKeyImportRequestBody();
        preview.setFileId(Long.toString(fileId));
        String digest = post(application(APP) + "/key-imports/previews", admin, preview).assertOk()
            .as(KeyImportPreviewResponseBody.class).getPreviewDigest();
        ConfirmKeyImportRequestBody confirm = new ConfirmKeyImportRequestBody();
        confirm.setFileId(Long.toString(fileId));
        confirm.setPreviewDigest(digest);
        post(application(APP) + "/key-imports", admin, confirm).assertOk();
    }

    /** 保存一条中文翻译。 */
    private void saveZh(String keyCode, String text, int expectedVersion) {
        post(application(APP) + "/translations/batch", admin, LocalizationRequestBuilder.save(ZH,
            LocalizationRequestBuilder.translation(keyCode, text, expectedVersion))).assertOk();
    }

    /** 停用或启用 zh-CN。 */
    private void updateZh(int expectedVersion, LocaleStatus status) {
        put(BASE + "/locales/" + ZH, admin, LocalizationRequestBuilder.updateLocale(expectedVersion, "简体中文", status))
            .assertOk();
    }

    /** 切换到指定发布的请求。 */
    private static ActivateReleaseRequestBody activateBody(int releaseNo) {
        ActivateReleaseRequestBody body = new ActivateReleaseRequestBody();
        body.setReleaseNo(releaseNo);
        body.setReason("切回发布 " + releaseNo);
        return body;
    }

    /** 执行期间存储凭据的 Vault Reveal 次数必须为 0（不读语言包文件）。 */
    private <T> T withoutVaultReveal(Supplier<T> call) {
        int before = storageVault.revealCount();
        T result = call.get();
        assertEquals(before, storageVault.revealCount(), "预检 / 差异期间不应读取语言包文件");
        return result;
    }

    /** 执行期间（含语言包检查与上线）存储凭据的 Vault Reveal 次数。 */
    private int vaultRevealsDuring(Runnable call) {
        int before = storageVault.revealCount();
        call.run();
        return storageVault.revealCount() - before;
    }

    /** 应用全部变化行的可读形式：发布号:语言:key:类型:文案。 */
    private List<String> changes() {
        return localizationRows.releaseChanges(applicationId).stream().map(row -> row.get("releaseNo") + ":"
            + row.get("locale") + ":" + row.get("keyCode") + ":" + row.get("changeType") + ":" + row.get("text"))
            .toList();
    }

    /** 期望的一条变化行，形式同 {@link #changes()}；REMOVED 的文案为 null。 */
    private static String change(int releaseNo, String locale, String keyCode, ReleaseChangeType type, String text) {
        return releaseNo + ":" + locale + ":" + keyCode + ":" + type.getCode() + ":" + text;
    }

    /** 某发布的变化行。 */
    private List<String> changes(int releaseNo) {
        return changes().stream().filter(row -> row.startsWith(releaseNo + ":")).toList();
    }

    /** 某发布登记的变化条数。 */
    private int changeCount(int releaseNo) {
        return ((Number) release(releaseNo).get("changeCount")).intValue();
    }

    /** 某发布号的发布 ID。 */
    private long releaseId(int releaseNo) {
        return ((Number) release(releaseNo).get("releaseId")).longValue();
    }

    /** 某发布号的发布行。 */
    private Map<String, Object> release(int releaseNo) {
        return localizationRows.releases(applicationId).stream()
            .filter(row -> ((Number) row.get("releaseNo")).intValue() == releaseNo).findFirst()
            .orElseThrow(() -> new AssertionError("发布不存在: " + releaseNo));
    }

    /** 摘要相对给定发布、没有任何变化。 */
    private static void assertEmptySummary(ReleaseChangeSummary summary, int baseReleaseNo) {
        assertAll(() -> assertEquals(baseReleaseNo, summary.getBaseReleaseNo()),
            () -> assertEquals(0, summary.getAddedKeyCount()), () -> assertEquals(0, summary.getDeprecatedKeyCount()),
            () -> assertEquals(0, summary.getSourceChangedKeyCount()),
            () -> assertTrue(summary.getTranslationChanges().isEmpty()));
    }

    /** 各语言翻译变化的可读形式：语言:条数:[示例 key]。 */
    private static List<String> describeLocales(ReleaseChangeSummary summary) {
        return summary.getTranslationChanges().stream().map((LocaleTranslationChange change) -> change.getLocale() + ":"
            + change.getChangedCount() + ":" + change.getSampleKeys()).toList();
    }

    /** 差异项的可读形式：语言:key:改前->改后。 */
    private static List<String> describe(List<ReleaseTextChange> changes) {
        return changes.stream().map(change -> change.getLocale() + ":" + change.getKeyCode() + ":"
            + change.getBefore() + "->" + change.getAfter()).toList();
    }
}
