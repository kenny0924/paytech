package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.client.master.test.base.onboarding.AbstractSubmissionApiTest;
import com.paytech.client.master.test.mocker.onboarding.OnboardingApplicationMocker.ApplicationFixture;
import com.paytech.client.master.test.mocker.onboarding.OnboardingFixtures;
import com.paytech.client.master.test.mocker.platform.ReferenceDataMocker.Catalog;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;

/**
 * C14 提交与提交核对中的 Platform 参考数据校验（需求 R5、AC-07、AC-09；技术方案 S-03、S-05、S-06）
 * <li>国家、币种与一级行政区经真实出站客户端向 Platform 参考数据测试接口按编码批量查询；不存在、未启用或行政区与地址国家不一致时
 * 对应字段报 FIELD_VALUE_NOT_IN_CATALOG 且 C14 拒绝；Platform 不可用整体 503，申请不变。</li>
 * <li>夹具个人申请：地址 AE / AE-DU，国籍与出生国 GB，居住国 AE，税务居民国属性 AE，护照签发国 GB，预计流水币种 USD。</li>
 *
 * @see com.paytech.client.contract.api.onboarding.ClientSubmissionApi#submit(String, String, String, String)
 * @see com.paytech.client.contract.api.onboarding.ClientSubmissionApi#check(String, String, String, String)
 */
class SubmitReferenceDataTest extends AbstractSubmissionApiTest {
    private static final String NOT_IN_CATALOG = "=FIELD_VALUE_NOT_IN_CATALOG";

    /** 本类公共配置：三类程序的材料。 */
    @BeforeEach
    void setUpClassMocks() {
        mockGenericRequirements();
    }

    /** 合规申请：国家、币种、行政区各只查询 Platform 一次，只带本次填写的编码，且不报参考数据问题。 */
    @Test
    void test_normalFlow() {
        var app = applications.individual(ACCOUNT_ID);

        var errors = checkErrors(app);

        assertTrue(errors.stream().noneMatch(error -> error.endsWith(NOT_IN_CATALOG)), errors.toString());
        var countries = referenceData.calls(Catalog.COUNTRY);
        assertEquals(1, countries.size());
        assertEquals(List.of("AE", OnboardingFixtures.PASSPORT_COUNTRY), List.of(countries.getFirst().codes().split(",")).stream()
            .sorted().toList());
        assertEquals("USD", referenceData.calls(Catalog.CURRENCY).getFirst().codes());
        assertEquals("AE-DU", referenceData.calls(Catalog.SUBDIVISION).getFirst().codes());
        assertEquals(1, referenceData.calls(Catalog.CURRENCY).size());
        assertEquals(1, referenceData.calls(Catalog.SUBDIVISION).size());
        assertEquals(0, referenceData.calls(Catalog.INDUSTRY).size() + referenceData.calls(Catalog.MCC).size());
    }

    /** 国家不在 Platform：国籍、出生国与证件签发国逐项报错，C14 拒绝且不写入审核登记。 */
    @Test
    void test_countryNotFound() {
        var app = applications.individual(ACCOUNT_ID);
        referenceData.remove(Catalog.COUNTRY, OnboardingFixtures.PASSPORT_COUNTRY);

        var errors = checkErrors(app);

        assertTrue(errors.containsAll(List.of("individual.nationalityCountry" + NOT_IN_CATALOG, "individual.birthCountry" + NOT_IN_CATALOG,
            "identifier.issuingCountry" + NOT_IN_CATALOG)), errors.toString());
        assertFalse(errors.contains("address.countryCode" + NOT_IN_CATALOG), errors.toString());
        assertRejectedWithoutWrites(app);
    }

    /** 国家与币种已停用：地址国家、居住国、COUNTRY 扩展属性与预计流水币种逐项报错。 */
    @Test
    void test_countryAndCurrencyInactive() {
        var app = applications.individual(ACCOUNT_ID);
        referenceData.mock(Catalog.COUNTRY, "AE", ReferenceDataStatus.INACTIVE);
        referenceData.mock(Catalog.CURRENCY, "USD", ReferenceDataStatus.INACTIVE);

        var errors = checkErrors(app);

        assertTrue(errors.containsAll(List.of("address.countryCode" + NOT_IN_CATALOG, "individual.residenceCountry" + NOT_IN_CATALOG,
            "attributes.TAX_RESIDENCE_COUNTRY" + NOT_IN_CATALOG, "business.currency" + NOT_IN_CATALOG)), errors.toString());
        assertRejectedWithoutWrites(app);
    }

    /** 行政区不存在或已停用：地址省州报错。 */
    @Test
    void test_subdivisionNotFound() {
        var app = applications.individual(ACCOUNT_ID);
        mapper.set("t_party_address", "state_or_province", "AE-XX", app.profileId());

        assertTrue(checkErrors(app).contains("address.stateOrProvince" + NOT_IN_CATALOG));

        mapper.set("t_party_address", "state_or_province", "AE-SH", app.profileId());
        referenceData.mockSubdivision("AE-SH", "AE", ReferenceDataStatus.INACTIVE);

        assertTrue(checkErrors(app).contains("address.stateOrProvince" + NOT_IN_CATALOG));
        assertRejectedWithoutWrites(app);
    }

    /** 行政区属于其他国家（AE 地址填 EG-C）：地址省州报错。 */
    @Test
    void test_subdivisionCountryMismatch() {
        var app = applications.individual(ACCOUNT_ID);
        mapper.set("t_party_address", "state_or_province", "EG-C", app.profileId());

        var errors = checkErrors(app);

        assertTrue(errors.contains("address.stateOrProvince" + NOT_IN_CATALOG), errors.toString());
        assertEquals("EG-C", referenceData.calls(Catalog.SUBDIVISION).getFirst().codes());
        assertRejectedWithoutWrites(app);
    }

    /** Platform 参考数据 5xx：核对与 C14 都返回 503 ONBOARDING_DEPENDENCY_UNAVAILABLE，申请保持草稿、无审核登记。 */
    @Test
    void test_platformUnavailable() {
        var app = applications.individual(ACCOUNT_ID);
        referenceData.mockUnavailable(Catalog.SUBDIVISION);

        var check = check(app);
        var result = submit(app);

        assertEquals(503, check.getStatus().value());
        assertEquals(503, result.getStatus().value());
        assertEquals("ONBOARDING_DEPENDENCY_UNAVAILABLE", body(result).path("code").asString());
        assertEquals("DRAFT", mapper.application(app.applicationId()).get("status"));
        assertNull(mapper.application(app.applicationId()).get("current_submission_id"));
        assertEquals(0, mapper.reviewEventCount());
        assertEquals(0, owners.count("review:"));
    }

    /** C14 以资料不完整拒绝，申请仍为草稿且没有审核登记任务。 */
    void assertRejectedWithoutWrites(ApplicationFixture app) {
        var result = submit(app);
        assertEquals(422, result.getStatus().value(), new String(result.getResponseBody()));
        assertEquals("SUBMISSION_INCOMPLETE", body(result).path("code").asString());
        assertEquals("DRAFT", mapper.application(app.applicationId()).get("status"));
        assertEquals(0, mapper.reviewEventCount());
    }
}
