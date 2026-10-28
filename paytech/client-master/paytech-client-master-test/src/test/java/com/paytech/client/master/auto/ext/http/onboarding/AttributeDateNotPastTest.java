package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.client.master.api.enums.OnboardingRequirementCode;
import com.paytech.client.master.test.base.onboarding.AbstractOnboardingHttpTest;

/**
 * DATE_NOT_PAST 规则（ATTRIBUTE 作用域）：SA 自由职业者文件到期日早于当天时提交核对报 IDENTIFIER_EXPIRED，当天及以后满足。
 *
 * @see com.paytech.client.contract.api.onboarding.ClientSubmissionApi#check(String, String, String, String)
 * @see com.paytech.client.contract.api.onboarding.ClientDraftApi#save(String, String, String, String,
 *      com.paytech.client.contract.api.onboarding.ClientDraftModels.DraftPatchRequestBody)
 */
class AttributeDateNotPastTest extends AbstractOnboardingHttpTest {
    /** 正式 DML 配置了 DATE_NOT_PAST 的属性。 */
    private static final String EXPIRY = "SA_FREELANCE_DOCUMENT_EXPIRY_DATE";
    /** 到期日已过的提交核对结论。 */
    private static final String EXPIRED = "attributes." + EXPIRY + "=" + OnboardingRequirementCode.IDENTIFIER_EXPIRED.getCode();

    /** 本类公共配置：三类程序的材料。 */
    @BeforeEach
    void setUpClassMocks() {
        owners.mockStandardPrograms();
    }

    /** 过期日报 IDENTIFIER_EXPIRED；改为今天后不再报。 */
    @Test
    void test_freelanceDocumentExpiry() {
        long applicationId = started("INDIVIDUAL", "SA", "FREELANCER", null);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        saved(applicationId, values("attributes", EXPIRY, today.minusDays(1).toString()));
        var expired = checkErrors(applicationId);
        saved(applicationId, values("attributes", EXPIRY, today.toString()));
        var current = checkErrors(applicationId);

        assertTrue(expired.contains(EXPIRED), expired.toString());
        assertFalse(current.stream().anyMatch(item -> item.startsWith("attributes." + EXPIRY + "=")), current.toString());
    }
}
