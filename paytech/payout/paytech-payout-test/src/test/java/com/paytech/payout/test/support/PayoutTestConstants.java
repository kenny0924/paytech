package com.paytech.payout.test.support;

/** payout 测试共用的稳定身份与合成原值；原值全部为合成测试值，只出现在请求体与 Vault Mock 内存中。 */
public final class PayoutTestConstants {
    /** 所有测试请求使用的 Request ID。 */
    public static final String REQUEST_ID = "Payout.Test_01";

    /** 测试 Client。 */
    public static final long CLIENT_ID = 880000001001L;

    /** 另一个 Client（共用账号场景）。 */
    public static final long OTHER_CLIENT_ID = 880000001002L;

    /** 测试 Local Partner。 */
    public static final long PARTNER_ID = 880000002001L;

    /** Client 门户成员账号。 */
    public static final long CLIENT_MEMBER_ID = 770000000101L;

    /** 运营代提交人账号。 */
    public static final long OPS_SUBMITTER_ID = 770000000201L;

    /** Maker 账号。 */
    public static final long MAKER_ID = 770000000202L;

    /** Checker 账号。 */
    public static final long CHECKER_ID = 770000000203L;

    /** 合成 AE IBAN（AE + 21 位数字）。 */
    public static final String AE_IBAN = "AE070331234567890123456";

    /** 另一个合成 AE IBAN。 */
    public static final String AE_IBAN_2 = "AE070331234567890654321";

    /** 合成 SA IBAN（SA + 22 位数字）。 */
    public static final String SA_IBAN = "SA0380000000608010167519";

    /** 合成户名。 */
    public static final String HOLDER_NAME = "Testholder Alpha";

    /** 合成户名的脱敏值。 */
    public static final String HOLDER_NAME_MASKED = "T********* A****";

    /** 合成银行名称。 */
    public static final String BANK_NAME = "Test Bank Emirates";

    /** 合成 SWIFT BIC。 */
    public static final String SWIFT_BIC = "TESTAEADXXX";

    /** 合成 SWIFT 账号。 */
    public static final String SWIFT_ACCOUNT_NUMBER = "1234567890123";

    /** 合成 EG 本地账号。 */
    public static final String EG_ACCOUNT_NUMBER = "100200300400";

    /** 合成 EG 本地银行代码。 */
    public static final String EG_BANK_CODE = "0019";

    /** 测试 Operating Entity。 */
    public static final String ENTITY_NO = "AE001";

    /** 另一个测试 Operating Entity。 */
    public static final String ENTITY_NO_2 = "SA001";

    /** Operations BFF 服务名。 */
    public static final String OPERATIONS_BFF = "operations-console-bff";

    /** Client BFF 服务名。 */
    public static final String CLIENT_BFF = "client-console-bff";

    private PayoutTestConstants() {
    }
}
