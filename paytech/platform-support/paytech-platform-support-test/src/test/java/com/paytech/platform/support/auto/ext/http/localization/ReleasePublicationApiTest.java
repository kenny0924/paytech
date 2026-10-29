package com.paytech.platform.support.auto.ext.http.localization;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.compensation.api.CompensationHandlerResult.Outcome;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.LocalizationErrorCode;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.api.localization.LocalizationDeliveryApi;
import com.paytech.platform.support.api.localization.LocalizationReleaseApi;
import com.paytech.platform.support.api.localization.request.ActivateReleaseRequestBody;
import com.paytech.platform.support.api.localization.request.PublishReleaseRequestBody;
import com.paytech.platform.support.api.localization.response.CurrentReleaseResponseBody;
import com.paytech.platform.support.api.localization.response.ManifestBundle;
import com.paytech.platform.support.api.localization.response.ManifestResponseBody;
import com.paytech.platform.support.api.localization.response.ReleaseDetailResponseBody;
import com.paytech.platform.support.api.localization.response.ReleaseDiffResponseBody;
import com.paytech.platform.support.api.localization.response.ReleasePrecheckResponseBody;
import com.paytech.platform.support.api.localization.response.ReleaseSummaryResponseBody;
import com.paytech.platform.support.api.localization.response.ReleaseTextChange;
import com.paytech.platform.support.core.integration.localization.FileServiceReleaseBundleStore;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.enums.FileScanType;
import com.paytech.platform.support.filedocument.domain.file.FileSourceCodes;
import com.paytech.platform.support.filedocument.integration.scanning.FormatFileScanner;
import com.paytech.platform.support.filedocument.integration.storage.StoredContentType;
import com.paytech.platform.support.localization.core.enums.ReleaseStatus;
import com.paytech.platform.support.localization.domain.LocalizationCodes;
import com.paytech.platform.support.localization.domain.release.BundleContent;
import com.paytech.platform.support.localization.domain.release.ReleaseCheckDecision;
import com.paytech.platform.support.localization.service.handler.ReleaseActivationHandler;
import com.paytech.platform.support.test.base.LocalizationApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.localization.LocalizationMocker;
import com.paytech.platform.support.test.localization.LocalizationRequestBuilder;

/**
 * 发布全链路（技术方案 3.3、3.7，需求 AC-05—AC-08）：发布第 1 步生成语言包并经 Platform 文件入口以 platform-support × PUBLIC
 * 写入本机 MinIO 公开桶，第 2 步登记 PENDING_CHECK 与上线任务；文件检查（FILE_SCAN）通过后，上线任务经正式 Scanner / Dispatcher
 * 把发布标为 READY 并切换当前发布；manifest 返回文件服务计算的公开地址，匿名可读到原字节。
 * <li>检查拒绝标 FAILED 且不切指针；生成期间文案被改返回 409、不登记发布；更新的发布先上线后，旧发布再上线不改指针；回滚与切回；
 * 两次发布差异；最后一次尝试仍在检查时按超时标 FAILED；任务身份不合法或发布不存在为终态失败。</li>
 *
 * @see LocalizationReleaseApi#publishRelease(String, String, String, PublishReleaseRequestBody)
 * @see LocalizationReleaseApi#activateRelease(String, String, String, ActivateReleaseRequestBody)
 * @see LocalizationReleaseApi#diffRelease(String, String, String, Integer, Integer)
 * @see LocalizationDeliveryApi#getManifest(String, String, Integer)
 * @see ReleaseActivationHandler#handle(CompensationEvent)
 */
class ReleasePublicationApiTest extends LocalizationApiTestBase {

    private static final String APP = LocalizationMocker.APPLICATION;

    private static final String ZH = LocalizationMocker.ZH_CN;

    private static final String EN = LocalizationCodes.BASE_LOCALE;

    /** 语言包对象名：i18n/应用/发布号/语言/命名空间/摘要前 16 位/随机部分。 */
    private static final String OBJECT_REF = "i18n/" + APP + "/%d/%s/%s/%s/[0-9a-f-]{36}";

    @Autowired
    private ReleaseActivationHandler activationHandler;

    private Operator admin;

    private String publicBase;

    private long applicationId;

    /** 本类公共：公开桶与写入路由；应用要求 zh-CN 覆盖 100%，team 两个 key、billing 一个 key，全部已有中文翻译。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = operationsMocker.operator(OperatorBuilder.administrator());
        publicBase = publicBundleBucket();
        applicationId = localization.application(APP, 100, ZH);
        long team = localization.namespace(applicationId, "team");
        long billing = localization.namespace(applicationId, "billing");
        localization.translation(localization.key(applicationId, team, "team.invite.submit", "Send invitation"), ZH,
            "发送邀请", 1);
        localization.translation(localization.key(applicationId, team, "team.invite.cancel", "Cancel"), ZH, "取消", 1);
        localization.translation(localization.key(applicationId, billing, "billing.title", "Billing"), ZH, "账单", 1);
    }

    /**
     * 预检带变更摘要；发布按语言 × 命名空间上传 4 个公开 JSON 文件（来源 platform-support、PUBLIC、对象名含应用 / 发布号 / 语言 /
     * 命名空间 / 摘要），登记 PENDING_CHECK；检查通过后上线、切换当前发布；详情检查进度 4/4；locale=en 的 manifest 只含 en；manifest 的地址是公开桶地址，匿名读到
     * 与登记摘要一致的语言包；发布写 I18N_RELEASE_PUBLISH 审计。
     */
    @Test
    void test_normalFlow() {
        var precheck = get(application(APP) + "/release-precheck", admin).assertOk()
            .as(ReleasePrecheckResponseBody.class);
        ReleaseSummaryResponseBody published = publish();
        runActivations();

        long releaseId = releaseId(1);
        List<Map<String, Object>> bundles = localizationRows.bundles(releaseId);
        List<Map<String, Object>> uploads = localizationRows.uploads(bundleReason());
        var detail = get(application(APP) + "/releases/1", admin).assertOk().as(ReleaseDetailResponseBody.class);
        ManifestResponseBody manifest = manifest("?locale=" + ZH);
        ManifestResponseBody baseOnly = manifest("?locale=" + EN);
        assertAll(() -> assertTrue(precheck.isPublishable()), () -> assertNull(precheck.getChangeSummary()
                .getBaseReleaseNo()), () -> assertEquals(3, precheck.getChangeSummary().getAddedKeyCount()),
            () -> assertEquals(1, published.getReleaseNo()),
            () -> assertEquals(ReleaseStatus.PENDING_CHECK.getCode(), published.getStatus()),
            () -> assertEquals(3, published.getChangeSummary().getAddedKeyCount()),
            () -> assertEquals(List.of(EN + "/billing", EN + "/team", ZH + "/billing", ZH + "/team"), bundles.stream()
                .map(row -> row.get("locale") + "/" + row.get("namespaceCode")).toList()),
            () -> assertEquals(4, uploads.size()),
            () -> assertEquals(ReleaseStatus.READY.getCode(), localizationRows.releases(applicationId).getFirst()
                .get("status")),
            () -> assertEquals(releaseId, ((Number) localizationRows.application(APP).get("currentReleaseId"))
                .longValue()),
            () -> assertTrue(localizationRows.activationTasks().isEmpty(), "上线成功后任务结束"),
            () -> assertTrue(detail.getRelease().isCurrent()),
            () -> assertEquals(List.of(4, 4, 0, 0), List.of(detail.getCheckProgress().getTotal(),
                detail.getCheckProgress().getAvailable(), detail.getCheckProgress().getPending(),
                detail.getCheckProgress().getRejected())),
            () -> assertEquals(1, manifest.getReleaseNo()),
            () -> assertEquals(Map.of(EN, List.of("billing", "team"), ZH, List.of("billing", "team")), manifest
                .getBundles().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                    entry -> entry.getValue().keySet().stream().sorted().toList()))),
            () -> assertEquals(1, baseOnly.getReleaseNo()),
            () -> assertEquals(Map.of(EN, List.of("billing", "team")), baseOnly.getBundles().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().keySet().stream().sorted()
                    .toList()))));
        for (Map<String, Object> bundle : bundles) {
            assertStoredBundle(1, bundle, uploads, manifest);
        }
        assertEquals("发送邀请", BundleContent.read(storage.browserGet(manifest.getBundles().get(ZH).get("team")
            .getUrl()).body()).get("team.invite.submit"));
        assertEquals(List.of(Map.of("action", "I18N_RELEASE_PUBLISH", "resourceType", "I18N_RELEASE", "resourceId",
            Long.toString(releaseId), "result", "SUCCEEDED")), releaseAudits());
    }

    /**
     * 检查拒绝：语言包检查结果不在接受集合，上线任务把发布标为 FAILED 并列出被拒绝的语言包（拒绝优先于等待，先被拒绝的即可结束）；
     * 不切换指针，切换与 manifest 预览都拒绝。
     */
    @Test
    void test_bundleRejected() {
        fileMocker.replaceRevision(filePolicy, 2, FormatFileScanner.SCANNER_CODE, FileScanType.FORMAT.getCode(),
            "UT_NEVER_ACCEPTED");

        publish();
        runActivations();

        Map<String, Object> release = localizationRows.releases(applicationId).getFirst();
        var detail = get(application(APP) + "/releases/1", admin).assertOk().as(ReleaseDetailResponseBody.class);
        assertAll(() -> assertEquals(ReleaseStatus.FAILED.getCode(), release.get("status")),
            () -> assertTrue(((String) release.get("failureReason")).startsWith("语言包检查未通过："),
                (String) release.get("failureReason")),
            () -> assertTrue(((String) release.get("failureReason")).matches("语言包检查未通过：(en|zh-CN)/(billing|team).*"),
                "拒绝优先：首个被拒绝的语言包即可让发布失败，原因至少列出一个"),
            () -> assertNull(localizationRows.application(APP).get("currentReleaseId")),
            () -> assertEquals(4, detail.getCheckProgress().getRejected()),
            () -> assertTrue(localizationRows.activationTasks().isEmpty(), "标 FAILED 后任务结束"));
        post(application(APP) + "/current-release", admin, activateBody(1, "切换到失败的发布"))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        anonymous(HttpMethod.GET, application(APP) + "/manifest?releaseNo=1", null)
            .assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    /**
     * 生成语言包期间文案修订号被改（模拟锁租约失效后的并发写入）：第 2 步返回 409 I18N_CONTENT_CHANGED，不登记发布、文案变化与上线任务；
     * 已上传的语言包成为孤儿文件。
     */
    @Test
    void test_contentChangedDuringPublish() {
        for (int i = 0; i < 10; i++) {
            long namespace = localization.namespace(applicationId, "extra" + i);
            localization.translation(localization.key(applicationId, namespace, "extra" + i + ".title", "Title " + i),
                ZH, "标题 " + i, 1);
        }

        CompletableFuture<PlatformResponseChecker> publishing = CompletableFuture.supplyAsync(
            () -> post(application(APP) + "/releases", admin, new PublishReleaseRequestBody()));
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(5))
            .until(() -> !localizationRows.uploads(bundleReason()).isEmpty());
        assertEquals(1, localizationRows.bumpContentRevision(APP));
        publishing.join().assertProblem(409, LocalizationErrorCode.I18N_CONTENT_CHANGED);
        List<Map<String, Object>> uploads = localizationRows.uploads(bundleReason());
        assertAll(() -> assertEquals(0, localizationRows.releaseCount(applicationId)),
            () -> assertTrue(localizationRows.releaseChanges(applicationId).isEmpty(), "变化行随登记一起回滚"),
            () -> assertTrue(localizationRows.activationTasks().isEmpty()),
            () -> assertEquals(24, uploads.size(), "第 1 步已上传全部 24 个语言包"),
            () -> assertTrue(uploads.stream().allMatch(row -> row.get("fileId") != null), "孤儿文件已完成登记"));
    }

    /**
     * 两次发布：第 2 次发布的预检与摘要相对线上发布 1；差异列出 zh-CN 一条修改，发布 1 没有基准时全部为新增。
     * 发布 2 上线后把发布 1 恢复为等待检查再上线：发布 1 变 READY，指针仍为 2。回滚到 1 写 I18N_RELEASE_ACTIVATE 审计，
     * manifest 返回发布 1 的地址、指定 releaseNo=2 预览发布 2；再切回 2。
     */
    @Test
    void test_twoReleases() {
        publish();
        runActivations();
        post(application(APP) + "/translations/batch", admin, LocalizationRequestBuilder.save(ZH,
            LocalizationRequestBuilder.translation("team.invite.submit", "立即发送邀请", 1))).assertOk();
        var precheck = get(application(APP) + "/release-precheck", admin).assertOk()
            .as(ReleasePrecheckResponseBody.class);
        publish();
        runActivations();
        long first = releaseId(1);
        long second = releaseId(2);
        assertEquals(second, ((Number) localizationRows.application(APP).get("currentReleaseId")).longValue());

        assertEquals(1, localizationRows.markPendingCheck(first));
        CompensationHandlerResult olderReady = activate(first, 1);
        Number pointerAfterOlder = (Number) localizationRows.application(APP).get("currentReleaseId");
        var diff = get(application(APP) + "/releases/2/diff?baseReleaseNo=1", admin).assertOk()
            .as(ReleaseDiffResponseBody.class);
        var initial = get(application(APP) + "/releases/1/diff", admin).assertOk().as(ReleaseDiffResponseBody.class);
        var rolledBack = post(application(APP) + "/current-release", admin, activateBody(1, "回滚到发布 1")).assertOk()
            .as(CurrentReleaseResponseBody.class);
        ManifestResponseBody current = manifest("?locale=" + ZH);
        ManifestResponseBody preview = manifest("?locale=" + ZH + "&releaseNo=2");

        assertAll(() -> assertEquals(1, precheck.getChangeSummary().getBaseReleaseNo()),
            () -> assertEquals(1, precheck.getChangeSummary().getTranslationChanges().getFirst().getChangedCount()),
            () -> assertEquals(Outcome.SUCCESS, olderReady.outcome()),
            () -> assertEquals(ReleaseStatus.READY.getCode(), localizationRows.releases(applicationId).getFirst()
                .get("status")),
            () -> assertEquals(second, pointerAfterOlder.longValue(), "旧发布上线不改指针"),
            () -> assertEquals(List.of(ZH + ":team.invite.submit:发送邀请->立即发送邀请"), describe(diff.getChanged())),
            () -> assertTrue(diff.getAdded().isEmpty() && diff.getRemoved().isEmpty()),
            () -> assertNull(initial.getBaseReleaseNo()), () -> assertEquals(6, initial.getAdded().size()),
            () -> assertEquals(1, rolledBack.getReleaseNo()), () -> assertEquals(1, current.getReleaseNo()),
            () -> assertEquals(2, preview.getReleaseNo()),
            () -> assertEquals(urlOf(first, ZH, "team"), current.getBundles().get(ZH).get("team").getUrl()),
            () -> assertEquals(urlOf(second, ZH, "team"), preview.getBundles().get(ZH).get("team").getUrl()));
        assertEquals("立即发送邀请", BundleContent.read(storage.browserGet(preview.getBundles().get(ZH).get("team")
            .getUrl()).body()).get("team.invite.submit"));
        post(application(APP) + "/current-release", admin, activateBody(2, "切回发布 2")).assertOk();
        assertEquals(second, ((Number) localizationRows.application(APP).get("currentReleaseId")).longValue());
        List<Map<String, Object>> activations = releaseAudits().stream()
            .filter(row -> "I18N_RELEASE_ACTIVATE".equals(row.get("action"))).toList();
        assertEquals(List.of(Long.toString(first), Long.toString(second)), activations.stream()
            .map(row -> row.get("resourceId")).toList());
    }

    /**
     * 语言包仍在检查（新修订要求一个未部署的检查器）：上线任务可重试，详情检查进度全部等待；第 60 次仍在检查时标 FAILED、原因“文件检查超时”。
     * 任务身份不合法与发布不存在为终态失败。
     */
    @Test
    void test_checkTimeoutAndInvalidTask() {
        fileMocker.replaceRevision(filePolicy, 2, "UT_ABSENT_SCANNER", FileScanType.ANTI_MALWARE.getCode(), "CLEAN");
        publish();
        fileMocker.awaitScanTasks(platformDataCleaner);
        localization.awaitActivationIdle(platformDataCleaner);
        long releaseId = releaseId(1);

        CompensationHandlerResult pending = activate(releaseId, 1);
        var detail = get(application(APP) + "/releases/1", admin).assertOk().as(ReleaseDetailResponseBody.class);
        CompensationHandlerResult timedOut = activate(releaseId, 60);
        CompensationHandlerResult invalid = activationHandler.handle(new CompensationEvent(1L, "release-1",
            ReleaseActivationHandler.EVENT_TYPE, "-", null, 1));
        CompensationHandlerResult missing = activate(OperatorBuilder.nextId(), 1);

        Map<String, Object> release = localizationRows.releases(applicationId).getFirst();
        assertAll(() -> assertEquals(Outcome.RETRYABLE_FAILURE, pending.outcome()),
            () -> assertEquals("I18N_RELEASE_CHECK_PENDING", pending.errorCode()),
            () -> assertEquals(4, detail.getCheckProgress().getPending()),
            () -> assertEquals(Outcome.SUCCESS, timedOut.outcome()),
            () -> assertEquals(ReleaseStatus.FAILED.getCode(), release.get("status")),
            () -> assertEquals(ReleaseCheckDecision.TIMEOUT_REASON, release.get("failureReason")),
            () -> assertNull(localizationRows.application(APP).get("currentReleaseId")),
            () -> assertEquals(Outcome.TERMINAL_FAILURE, invalid.outcome()),
            () -> assertEquals("I18N_RELEASE_ACTIVATE_INVALID", invalid.errorCode()),
            () -> assertEquals(Outcome.TERMINAL_FAILURE, missing.outcome()),
            () -> assertEquals("I18N_RELEASE_NOT_FOUND", missing.errorCode()));
    }

    /** 发布并返回 PENDING_CHECK 摘要。 */
    private ReleaseSummaryResponseBody publish() {
        return post(application(APP) + "/releases", admin, new PublishReleaseRequestBody()).assertOk()
            .as(ReleaseSummaryResponseBody.class);
    }

    /** 以 Handler 事件工厂构造的上线任务执行一次。 */
    private CompensationHandlerResult activate(long releaseId, int executeCount) {
        CompensationEventRequest request = ReleaseActivationHandler.buildEvent(releaseId);
        return activationHandler.handle(new CompensationEvent(1L, request.eventKey(), request.eventType(),
            request.subEventType(), null, executeCount));
    }

    /** 匿名读取 manifest。 */
    private ManifestResponseBody manifest(String query) {
        return anonymous(HttpMethod.GET, application(APP) + "/manifest" + query, null).assertOk()
            .as(ManifestResponseBody.class);
    }

    /** 切换请求。 */
    private static ActivateReleaseRequestBody activateBody(int releaseNo, String reason) {
        ActivateReleaseRequestBody body = new ActivateReleaseRequestBody();
        body.setReleaseNo(releaseNo);
        body.setReason(reason);
        return body;
    }

    /** 发布号对应的发布 ID。 */
    private long releaseId(int releaseNo) {
        return ((Number) localizationRows.releases(applicationId).stream()
            .filter(row -> ((Number) row.get("releaseNo")).intValue() == releaseNo).findFirst()
            .orElseThrow(() -> new AssertionError("发布不存在: " + releaseNo)).get("releaseId")).longValue();
    }

    /** 语言包上传原因码。 */
    private static String bundleReason() {
        return FileServiceReleaseBundleStore.REASON_CODE;
    }

    /** 发布某语言 × 命名空间语言包的公开地址：公开基础地址加上传登记的对象名。 */
    private String urlOf(long releaseId, String locale, String namespace) {
        Map<String, Object> bundle = localizationRows.bundles(releaseId).stream()
            .filter(row -> locale.equals(row.get("locale")) && namespace.equals(row.get("namespaceCode"))).findFirst()
            .orElseThrow();
        long fileId = FileAddress.parse((String) bundle.get("fileAddress")).fileId();
        return publicBase + "/" + localizationRows.uploads(bundleReason()).stream()
            .filter(row -> fileId == ((Number) row.get("fileId")).longValue()).findFirst().orElseThrow()
            .get("objectRef");
    }

    /** 一个语言包：上传与文件登记、对象名、摘要、manifest 地址与匿名读取的字节。 */
    private void assertStoredBundle(int releaseNo, Map<String, Object> bundle, List<Map<String, Object>> uploads,
        ManifestResponseBody manifest) {
        String locale = (String) bundle.get("locale");
        String namespace = (String) bundle.get("namespaceCode");
        String hash = (String) bundle.get("contentHash");
        long fileId = FileAddress.parse((String) bundle.get("fileAddress")).fileId();
        Map<String, Object> upload = uploads.stream().filter(row -> row.get("fileId") != null
            && fileId == ((Number) row.get("fileId")).longValue()).findFirst()
            .orElseThrow(() -> new AssertionError("语言包没有对应的上传: " + locale + "/" + namespace));
        String objectRef = (String) upload.get("objectRef");
        ManifestBundle listed = manifest.getBundles().get(locale).get(namespace);
        byte[] bytes = storage.browserGet(listed.getUrl()).body();
        assertAll(locale + "/" + namespace,
            () -> assertEquals(FileSourceCodes.PLATFORM_SUPPORT, upload.get("sourceSystem")),
            () -> assertEquals(FileAccessLevel.PUBLIC.getCode(), upload.get("accessLevel")),
            () -> assertEquals(StoredContentType.JSON, upload.get("mimeType")),
            () -> assertNull(upload.get("uploaderId")),
            () -> assertEquals(FileScanStatus.AVAILABLE.getCode(), upload.get("scanStatus")),
            () -> assertEquals(hash, upload.get("contentHash")),
            () -> assertTrue(objectRef.matches(String.format(OBJECT_REF, releaseNo, locale, namespace,
                hash.substring(0, 16))), objectRef),
            () -> assertEquals(hash, listed.getHash()),
            () -> assertEquals(publicBase + "/" + objectRef, listed.getUrl()),
            () -> assertEquals(((Number) bundle.get("byteSize")).intValue(), bytes.length),
            () -> assertEquals(hash, LocalizationMocker.sha256(bytes)));
        assertArrayEquals(bytes, storage.browserGet(listed.getUrl()).body(), "公开地址内容稳定");
    }

    /** 等待异步审计落库后读取发布与切换审计。 */
    private List<Map<String, Object>> releaseAudits() {
        await().atMost(Duration.ofSeconds(5)).until(() -> auditDispatcher.pendingCount() == 0);
        return localizationRows.releaseAudits();
    }

    /** 差异项的可读形式。 */
    private static List<String> describe(List<ReleaseTextChange> changes) {
        return changes.stream().map(change -> change.getLocale() + ":" + change.getKeyCode() + ":"
            + change.getBefore() + "->" + change.getAfter()).toList();
    }
}
