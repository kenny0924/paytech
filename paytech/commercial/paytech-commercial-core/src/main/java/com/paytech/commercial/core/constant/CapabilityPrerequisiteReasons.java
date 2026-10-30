package com.paytech.commercial.core.constant;

/**
 * 开通项前置检查的原因码（开放字符串，technique.md §9.3）
 * <li>只在核对响应中返回、不落库；前端按 commercial.enum.CapabilityPrerequisiteReason.&lt;CODE&gt; 取文案，未知码显示原值。</li>
 * <li>④ 全部已覆盖币种都不就绪时原样采用 channel-connector 的 ChannelIdentityReadinessReason（MID_NOT_FOUND、PAYMENT_METHOD_NOT_SUPPORTED、
 * MCC_NOT_COVERED、CURRENCY_NOT_SUPPORTED、PAYMENT_ACTION_NOT_SUPPORTED），不在此重复定义；参数 entityNo、paymentMethodCode、mccCode、
 * currencies（全部不就绪币种，字典序逗号分隔）；部分币种就绪时 ④ 满足并带 CHANNEL_IDENTITY_PARTIAL。</li>
 */
public final class CapabilityPrerequisiteReasons {
    /** ① Client 资格不合格（含 compliance 判定的结算冻结） */
    public static final String CLIENT_NOT_QUALIFIED = "CLIENT_NOT_QUALIFIED";

    /** ① 开通项所在 OE 资格不合格 */
    public static final String OPERATING_ENTITY_NOT_QUALIFIED = "OPERATING_ENTITY_NOT_QUALIFIED";

    /** ①④ 资格或渠道身份查询失败、超时 */
    public static final String CHECK_UNAVAILABLE = "CHECK_UNAVAILABLE";

    /** ② 合同不是 ACTIVE 或当前不在合同生效区间内 */
    public static final String AGREEMENT_NOT_ACTIVE = "AGREEMENT_NOT_ACTIVE";

    /** ② 当前不在开通项生效区间内 */
    public static final String CAPABILITY_NOT_EFFECTIVE = "CAPABILITY_NOT_EFFECTIVE";

    /** ② 合同没有与开通项种类相关、且有 ACTIVE 版本的商业条款方案 */
    public static final String TERM_VERSION_NOT_ACTIVE = "TERM_VERSION_NOT_ACTIVE";

    /** ② ACTIVE 版本的价格没有完整覆盖开通项 */
    public static final String PRICE_NOT_COVERED = "PRICE_NOT_COVERED";

    /** ④ 该种类开通项不需要渠道身份，或对应能力尚未交付 */
    public static final String NOT_APPLICABLE = "NOT_APPLICABLE";

    /** ④ 价格尚未覆盖任何币种，等价格确定后再核对（按满足处理，② 已说明缺价格）；参数 entityNo */
    public static final String AWAITING_PRICE = "AWAITING_PRICE";

    /** ④ Client 主经营分类 MCC 尚未确认；参数 clientId */
    public static final String CLIENT_MCC_MISSING = "CLIENT_MCC_MISSING";

    /** ④ 满足（至少一个已覆盖币种就绪）但部分币种不就绪；参数 entityNo、paymentMethodCode、mccCode、currencies（不就绪币种） */
    public static final String CHANNEL_IDENTITY_PARTIAL = "CHANNEL_IDENTITY_PARTIAL";

    /** 纯常量。 */
    private CapabilityPrerequisiteReasons() {
    }
}
