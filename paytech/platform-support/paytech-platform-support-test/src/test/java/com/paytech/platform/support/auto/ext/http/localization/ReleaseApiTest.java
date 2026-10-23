package com.paytech.platform.support.auto.ext.http.localization;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.LocalizationErrorCode;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.api.localization.LocalizationDeliveryApi;
import com.paytech.platform.support.api.localization.LocalizationReleaseApi;
import com.paytech.platform.support.api.localization.request.PublishReleaseRequestBody;
import com.paytech.platform.support.api.localization.response.LocaleCoverage;
import com.paytech.platform.support.api.localization.response.ManifestResponseBody;
import com.paytech.platform.support.api.localization.response.ReleasePrecheckResponseBody;
import com.paytech.platform.support.localization.domain.LocalizationCodes;
import com.paytech.platform.support.test.base.LocalizationApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.localization.LocalizationMocker;

/**
 * 发布预检与覆盖率拒绝（需求 R5、AC-04），以及尚无发布时的 manifest（技术方案 3.7）。
 * <li>发布成功、检查通过后上线、检查拒绝标 FAILED、手动切到更新发布后旧发布上线不改指针、回滚与差异见
 * {@link ReleasePublicationApiTest}。</li>
 *
 * @see LocalizationReleaseApi#precheckRelease(String, String, String)
 * @see LocalizationReleaseApi#publishRelease(String, String, String, PublishReleaseRequestBody)
 * @see LocalizationDeliveryApi#getManifest(String, String, Integer)
 */
class ReleaseApiTest extends LocalizationApiTestBase {

    private Operator admin;

    /** 本类公共：应用要求 zh-CN 覆盖 100%，两个 key 只有一个中文翻译（覆盖率 50%）。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = operationsMocker.operator(OperatorBuilder.administrator());
        long applicationId = localization.application(LocalizationMocker.APPLICATION, 100, LocalizationMocker.ZH_CN);
        long team = localization.namespace(applicationId, "team");
        long submit = localization.key(applicationId, team, "team.invite.submit", "Send invitation");
        localization.key(applicationId, team, "team.invite.cancel", "Cancel");
        localization.translation(submit, LocalizationMocker.ZH_CN, "发送邀请", 1);
    }

    /** 预检：zh-CN 50% 不达标、不可发布，列出缺口；en 恒为 100%；预检不写库。 */
    @Test
    void test_precheck() {
        var precheck = get(application(LocalizationMocker.APPLICATION) + "/release-precheck", admin).assertOk()
            .as(ReleasePrecheckResponseBody.class);
        LocaleCoverage zh = precheck.getCoverage().get(1);
        assertAll(() -> assertFalse(precheck.isPublishable()), () -> assertEquals(1, precheck.getNextReleaseNo()),
            () -> assertEquals(2, precheck.getActiveKeyCount()),
            () -> assertEquals(List.of(LocalizationCodes.BASE_LOCALE, LocalizationMocker.ZH_CN), precheck.getCoverage()
                .stream().map(LocaleCoverage::getLocale).toList()),
            () -> assertEquals(100, precheck.getCoverage().getFirst().getCoveragePercent()),
            () -> assertEquals(50, zh.getCoveragePercent()), () -> assertFalse(zh.isSatisfied()),
            () -> assertEquals(1, precheck.getMissingTotal()),
            () -> assertEquals("team.invite.cancel", precheck.getMissing().getFirst().getKeyCode()));
    }

    /** 覆盖率不足拒绝发布（422），errors 列出缺口与总数；不生成发布。 */
    @Test
    void test_coverageInsufficient() {
        var rejected = post(application(LocalizationMocker.APPLICATION) + "/releases", admin,
            new PublishReleaseRequestBody()).assertProblem(422, LocalizationErrorCode.I18N_COVERAGE_INSUFFICIENT);
        var errors = rejected.json().path("errors");
        long applicationId = ((Number) localizationRows.application(LocalizationMocker.APPLICATION)
            .get("applicationId")).longValue();
        assertAll(() -> assertEquals(2, errors.size()),
            () -> assertEquals("missing.zh-CN", errors.get(0).path("field").asString()),
            () -> assertTrue(errors.get(0).path("message").asString().contains("team.invite.cancel")),
            () -> assertEquals("missingTotal", errors.get(1).path("field").asString()),
            () -> assertEquals(0, localizationRows.releaseCount(applicationId)));
    }

    /** 尚无发布：manifest 匿名可读，releaseNo 为 null、bundles 为空；未知应用 404；发布需要 I18N_PUBLISH。 */
    @Test
    void test_manifestWithoutRelease() {
        var manifest = anonymous(HttpMethod.GET, BASE + "/applications/" + LocalizationMocker.APPLICATION
            + "/manifest?locale=" + LocalizationMocker.ZH_CN, null).assertOk().as(ManifestResponseBody.class);
        anonymous(HttpMethod.GET, BASE + "/applications/unknown-app/manifest", null)
            .assertProblem(404, CommonErrorCode.NOT_FOUND);
        Operator viewer = operationsMocker.operator(OperatorBuilder.viewer());
        post(application(LocalizationMocker.APPLICATION) + "/releases", viewer, new PublishReleaseRequestBody())
            .assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        assertAll(() -> assertNull(manifest.getReleaseNo()), () -> assertTrue(manifest.getBundles().isEmpty()),
            () -> assertEquals(LocalizationCodes.BASE_LOCALE, manifest.getDefaultLocale()));
    }
}
