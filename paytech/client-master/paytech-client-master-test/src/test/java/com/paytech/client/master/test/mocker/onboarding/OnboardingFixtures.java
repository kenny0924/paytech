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
    /** 新申请（v2 Schema）组织声明：平台条款拆为接受条款、确认隐私告知，另加关系人资料授权。 */
    public static final List<String> ORGANIZATION_DECLARATIONS_V2 = List.of("OWNERSHIP_COMPLETE", "DIRECTORSHIP_COMPLETE",
        "REPRESENTATIVE_AUTHORITY", "PEP_DECLARATION", "INFORMATION_TRUE", "RELATED_PERSON_DATA_AUTHORITY", "PLATFORM_TERMS_ACCEPTANCE",
        "PRIVACY_NOTICE_ACKNOWLEDGED");
    /** 新申请（v2 Schema）个人声明。 */
    public static final List<String> INDIVIDUAL_DECLARATIONS_V2 = List.of("PEP_DECLARATION", "INFORMATION_TRUE",
        "PLATFORM_TERMS_ACCEPTANCE", "PRIVACY_NOTICE_ACKNOWLEDGED");
    /** 正式 DML 为条款与隐私告知配置的文档版本（DOCUMENT_VERSION）。 */
    public static final String DOCUMENT_VERSION = "2026-10-draft";
    /** 法务修订 v2：AE 组织默认 Schema（已由业务信息补采退役）。 */
    public static final long AE_ORGANIZATION_SCHEMA_V2 = 9_270_000_000_900_301L;
    /** 法务修订 v2：AE 个人默认 Schema（已由业务信息补采退役）。 */
    public static final long AE_INDIVIDUAL_SCHEMA_V2 = 9_270_000_000_900_303L;
    /** 业务信息补采 v3：AE 组织默认 Schema。 */
    public static final long AE_ORGANIZATION_SCHEMA_V3 = 9_281_200_000_900_301L;
    /** 业务信息补采 v3：AE FZ_LLC 专用 Schema。 */
    public static final long AE_FZ_LLC_SCHEMA_V3 = 9_281_200_000_900_302L;
    /** 业务信息补采 v3：AE 个人默认 Schema。 */
    public static final long AE_INDIVIDUAL_SCHEMA_V3 = 9_281_200_000_900_303L;
    /** 业务信息补采字段：期望支付方式。 */
    public static final String EXPECTED_PAYMENT_METHODS = "business.expectedPaymentMethods";
    /** 业务信息补采字段：退款政策链接。 */
    public static final String REFUND_POLICY_URL = "business.refundPolicyUrl";
    /** 业务信息补采字段：交付方式。 */
    public static final String FULFILLMENT_TYPE = "business.fulfillmentType";
    /** 业务信息补采字段：预计交付天数。 */
    public static final String FULFILLMENT_DAYS = "business.fulfillmentDays";
    /** 交付方式目录（client-master 自有目录 FULFILLMENT_TYPE）。 */
    public static final String FULFILLMENT_TYPE_CATALOG = "FULFILLMENT_TYPE";
    /** 交付方式目录项：即时交付、预售、订阅、服务交付。 */
    public static final List<String> FULFILLMENT_TYPES = List.of("INSTANT", "PRE_ORDER", "SUBSCRIPTION", "SERVICE");
    /** 唯一需要预计交付天数的交付方式。 */
    public static final String PRE_ORDER = "PRE_ORDER";
    /** 即时交付。 */
    public static final String INSTANT = "INSTANT";

    private OnboardingFixtures() {
    }

    /** 签署某声明时应携带的条款版本：附版本化文档的声明为 DOCUMENT_VERSION，其余为空。 */
    public static String termsVersion(String declarationType) {
        return "PLATFORM_TERMS_ACCEPTANCE".equals(declarationType) || "PRIVACY_NOTICE_ACKNOWLEDGED".equals(declarationType)
            ? DOCUMENT_VERSION : null;
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
