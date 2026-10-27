package com.paytech.client.master.test.mocker.onboarding;

import java.util.List;

import com.paytech.client.master.domain.onboarding.pack.IdentifierRules;

/**
 * 入驻夹具使用的国家包取值：AE 包（正式初始化 DML）与测试辖区 ZZ（zz-pack.sql）
 * <li>包代码是数据而不是枚举，只在测试夹具与断言中出现；生产代码不得引用这些取值。</li>
 */
public final class OnboardingFixtures {
    /** 夹具辖区。 */
    public static final String JURISDICTION = "AE";
    /** AE 组织默认 Schema。 */
    public static final long AE_ORGANIZATION_SCHEMA = 9_270_000_000_700_001L;
    /** AE FZ_LLC 专用 Schema。 */
    public static final long AE_FZ_LLC_SCHEMA = 9_270_000_000_700_002L;
    /** AE 个人默认 Schema。 */
    public static final long AE_INDIVIDUAL_SCHEMA = 9_270_000_000_700_003L;
    /** ZZ 组织默认 Schema。 */
    public static final long ZZ_ORGANIZATION_SCHEMA = 9_399_000_000_700_001L;
    /** 组织夹具的组织类型。 */
    public static final String ORGANIZATION_TYPE = "LLC";
    /** 组织夹具的登记机关（AE_TRADE_LICENSE 的唯一性作用域）。 */
    public static final String REGISTRATION_AUTHORITY = "AE_DED_DUBAI";
    /** 组织证件类型。 */
    public static final String TRADE_LICENSE = "AE_TRADE_LICENSE";
    /** 个人证件类型。 */
    public static final String PASSPORT = "PASSPORT";
    /** 个人证件签发国。 */
    public static final String PASSPORT_COUNTRY = "GB";
    /** Vault 数据类型。 */
    public static final String IDENTIFIER_DATA_TYPE = "LEGAL_IDENTIFIER_V1";
    /** 组织申请须签署的声明。 */
    public static final List<String> ORGANIZATION_DECLARATIONS = List.of("OWNERSHIP_COMPLETE", "DIRECTORSHIP_COMPLETE",
        "REPRESENTATIVE_AUTHORITY", "PEP_DECLARATION", "INFORMATION_TRUE", "PLATFORM_TERMS");
    /** 个人申请须签署的声明。 */
    public static final List<String> INDIVIDUAL_DECLARATIONS = List.of("PEP_DECLARATION", "INFORMATION_TRUE", "PLATFORM_TERMS");

    private OnboardingFixtures() {
    }

    /** 某主体夹具护照的规范化号码。 */
    public static String passport(long partyId) {
        return "P" + String.format("%08d", partyId % 100_000_000L);
    }

    /** 与 Vault Mock 一致的稳定 Token。 */
    public static String passportToken(String normalized) {
        return VaultMocker.token(IDENTIFIER_DATA_TYPE, normalized);
    }

    /** 某主体夹具营业执照的规范化号码。 */
    public static String tradeLicense(long partyId) {
        return "TL" + partyId % 1_000_000L;
    }

    /** 末 4 位脱敏。 */
    public static String masked(String normalized) {
        return IdentifierRules.mask(normalized);
    }
}
