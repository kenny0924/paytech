package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.client.master.api.enums.OnboardingRequirementCode;
import com.paytech.client.master.domain.enums.RuleCode;
import com.paytech.client.master.domain.enums.RuleScopeType;
import com.paytech.client.master.domain.enums.UboThresholdComparison;
import com.paytech.client.master.test.base.onboarding.AbstractOnboardingHttpTest;
import com.paytech.client.master.test.mocker.onboarding.OnboardingApplicationMocker.ApplicationFixture;
import com.paytech.client.master.test.mocker.onboarding.OnboardingFixtures;

/**
 * 受益人门槛比较方式 UBO_THRESHOLD_COMPARISON：GTE 时恰好达到门槛（25%）计入受益人，GT 时须超过门槛；取值不合法失败关闭。
 *
 * @see com.paytech.client.contract.api.onboarding.ClientSubmissionApi#check(String, String, String, String)
 * @see com.paytech.client.master.api.onboarding.ClientOnboardingCatalogApi#formSchema(String, String, String, String, String, String,
 *      String)
 */
class UboThresholdComparisonTest extends AbstractOnboardingHttpTest {
    /** 夹具受益人恰好持有门槛比例。 */
    private static final BigDecimal THRESHOLD = new BigDecimal("25");
    /** 受益人缺失的提交核对结论。 */
    private static final String UBO_MISSING = "relation.UBO=" + OnboardingRequirementCode.UBO_MISSING.getCode();

    /** 本类公共配置：三类程序的材料。 */
    @BeforeEach
    void setUpClassMocks() {
        owners.mockStandardPrograms();
    }

    /** GTE：恰好 25% 的受益人满足门槛；表单规格下发 GTE。 */
    @Test
    void test_gteCountsExactThreshold() {
        comparison(UboThresholdComparison.GTE.getCode());
        var app = organizationWithOwner(THRESHOLD);

        var errors = checkErrors(app.applicationId());

        assertFalse(errors.contains(UBO_MISSING), errors.toString());
        assertEquals(UboThresholdComparison.GTE.getCode(), organizationSchema().path("relationRules").path("uboThresholdComparison")
            .asString());
    }

    /** GT：恰好 25% 不计入受益人，且无兜底角色时报 UBO_MISSING；超过门槛即满足；表单规格下发 GT。 */
    @Test
    void test_gtRequiresAboveThreshold() {
        comparison(UboThresholdComparison.GT.getCode());
        var app = applications.organization(ACCOUNT_ID);
        var owner = applications.person(app, "UBO");
        mapper.ownership(owner.relationId(), THRESHOLD);

        var errors = checkErrors(app.applicationId());

        assertTrue(errors.contains(UBO_MISSING), errors.toString());
        assertEquals(UboThresholdComparison.GT.getCode(), organizationSchema().path("relationRules").path("uboThresholdComparison")
            .asString());
        mapper.ownership(owner.relationId(), new BigDecimal("25.01"));
        var aboveErrors = checkErrors(app.applicationId());
        assertFalse(aboveErrors.contains(UBO_MISSING), aboveErrors.toString());
    }

    /** 未登记的比较方式属于目录配置错误：提交核对失败关闭为 500，不按默认放行。 */
    @Test
    void test_invalidComparisonFailsClosed() {
        comparison("GE");
        var app = organizationWithOwner(THRESHOLD);

        var result = http.get().uri("/client-onboarding/v1/applications/{id}/submission-check", app.applicationId()).headers(this::client)
            .exchange().expectBody(byte[].class).returnResult();

        assertEquals(500, result.getStatus().value(), new String(result.getResponseBody()));
    }

    /** 设置夹具辖区的比较方式。 */
    void comparison(String value) {
        packs.rule(RuleScopeType.JURISDICTION.getCode(), OnboardingFixtures.JURISDICTION, RuleCode.UBO_THRESHOLD_COMPARISON.getCode(),
            value);
    }

    /** 默认账号的组织申请：只有一名受益人（无兜底角色），持股为给定比例。 */
    ApplicationFixture organizationWithOwner(BigDecimal ownership) {
        var app = applications.organization(ACCOUNT_ID);
        var owner = applications.person(app, "UBO");
        mapper.ownership(owner.relationId(), ownership);
        return app;
    }

    /** 夹具辖区组织默认表单规格。 */
    tools.jackson.databind.JsonNode organizationSchema() {
        return body(formSchema("jurisdiction", OnboardingFixtures.JURISDICTION, "partyType", "ORGANIZATION"));
    }
}
