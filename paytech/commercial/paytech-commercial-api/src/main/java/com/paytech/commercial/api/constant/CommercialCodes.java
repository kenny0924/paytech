package com.paytech.commercial.api.constant;

/**
 * commercial 开放目录中业务必须依赖的稳定代码。
 * <li>这些列的值域由目录表或其他 Owner 维护（term_type、trigger_code / fee_event_code、capability_code、payment_method_code、
 * 维度值、billing_fact_type），数据库可以新增合法项，因此不建 Java 枚举；这里只集中业务代码直接引用的稳定项。</li>
 * <li>与初始化 DML {@code docs/database/sql/commercial-initial-data.sql} 的目录内容一致。</li>
 */
public final class CommercialCodes {
    /** Client 合同 T+N 结算使用的阿联酋工作日日历代码；本期固定，待 Platform 参考数据接入后改为目录引用。 */
    public static final String AE_BUSINESS_DAYS = "AE_BUSINESS_DAYS";

    private CommercialCodes() {
    }

    /** 商业条款类型（t_commercial_term_plan.term_type、t_product_fee_event.term_type）。 */
    public static final class TermType {
        /** Client 定价 */
        public static final String CLIENT_PRICING = "CLIENT_PRICING";

        /** Local Partner 分润 */
        public static final String PARTNER_SHARE = "PARTNER_SHARE";

        /** 平台向 Local Partner 收取的服务费 */
        public static final String PLATFORM_SERVICE_CHARGE = "PLATFORM_SERVICE_CHARGE";

        /** Partner 合同下按画像 × 币种的买入价，结构同 CLIENT_PRICING */
        public static final String PARTNER_BUY_RATE = "PARTNER_BUY_RATE";

        /** 出款手续费 */
        public static final String PAYOUT_FEE = "PAYOUT_FEE";

        /** 换汇点差 */
        public static final String FX_SPREAD = "FX_SPREAD";

        /** 争议处理费 */
        public static final String DISPUTE_FEE = "DISPUTE_FEE";

        /** 平台服务费（月费、开户、KYB 审核） */
        public static final String PLATFORM_FEE = "PLATFORM_FEE";

        private TermType() {
        }
    }

    /** 计费事件（t_product_fee_event.fee_event_code = t_commercial_term_component.trigger_code）。 */
    public static final class FeeEvent {
        /** 授权 */
        public static final String AUTH = "AUTH";

        /** 请款 */
        public static final String CAPTURE = "CAPTURE";

        /** 授权并请款 */
        public static final String SALE = "SALE";

        /** 退款 */
        public static final String REFUND = "REFUND";

        /** 拒付 */
        public static final String CHARGEBACK = "CHARGEBACK";

        /** 争议胜诉冲回 */
        public static final String DISPUTE_WON_REVERSAL = "DISPUTE_WON_REVERSAL";

        /** 出款提交 */
        public static final String PAYOUT_SUBMITTED = "PAYOUT_SUBMITTED";

        /** 出款退回 */
        public static final String PAYOUT_RETURNED = "PAYOUT_RETURNED";

        /** 换汇成交 */
        public static final String FX_DEAL = "FX_DEAL";

        /** 动态货币转换已应用 */
        public static final String DCC_APPLIED = "DCC_APPLIED";

        /** 月度周期 */
        public static final String MONTHLY_CYCLE = "MONTHLY_CYCLE";

        /** 开户 */
        public static final String SETUP = "SETUP";

        /** KYB 审核 */
        public static final String KYB_REVIEW = "KYB_REVIEW";

        /** 卡令牌保存 */
        public static final String TOKEN_STORED = "TOKEN_STORED";

        private FeeEvent() {
        }
    }

    /** 可开通项（t_product_capability.capability_code、t_agreement_capability.capability_code）。 */
    public static final class Capability {
        /** 银行卡（卡组织、借贷记等由合同开通项接受条件限定；2026-10-20 起替代 CARD_VISA / MC / AMEX / UNIONPAY） */
        public static final String CARD = "CARD";

        /** 存卡（功能开通项，TOKEN_STORED 计费事件挂接） */
        public static final String TOKEN_STORAGE = "TOKEN_STORAGE";

        /** Apple Pay */
        public static final String APPLE_PAY = "APPLE_PAY";

        /** Google Pay */
        public static final String GOOGLE_PAY = "GOOGLE_PAY";

        /** 本地出款 */
        public static final String PAYOUT_LOCAL = "PAYOUT_LOCAL";

        /** SWIFT 出款 */
        public static final String PAYOUT_SWIFT = "PAYOUT_SWIFT";

        /** 即期换汇 */
        public static final String FX_SPOT = "FX_SPOT";

        /** 争议处理 */
        public static final String DISPUTE_HANDLING = "DISPUTE_HANDLING";

        /** 数据分析订阅 */
        public static final String ANALYTICS_SUBSCRIPTION = "ANALYTICS_SUBSCRIPTION";

        private Capability() {
        }
    }

    /** Payment 拥有的 Payment Method 稳定代码中本服务直接引用的项。 */
    public static final class PaymentMethod {
        /** 银行卡 */
        public static final String CARD = "CARD";

        /** Apple Pay */
        public static final String APPLE_PAY = "APPLE_PAY";

        /** Google Pay */
        public static final String GOOGLE_PAY = "GOOGLE_PAY";

        private PaymentMethod() {
        }
    }

    /** CARD_BRAND 维度的规范化值。 */
    public static final class CardBrand {
        /** Visa */
        public static final String VISA = "VISA";

        /** Mastercard */
        public static final String MASTERCARD = "MASTERCARD";

        /** American Express */
        public static final String AMEX = "AMEX";

        /** 银联 */
        public static final String UNIONPAY = "UNIONPAY";

        private CardBrand() {
        }
    }

    /** billing 事实类型目录中计费事件映射使用的项（t_product_fee_event.billing_fact_type）。 */
    public static final class BillingFactType {
        /** 支付 */
        public static final String PAYMENT = "PAYMENT";

        /** 退款 */
        public static final String REFUND = "REFUND";

        /** 争议 */
        public static final String DISPUTE = "DISPUTE";

        /** 出款手续费 */
        public static final String PAYOUT_FEE = "PAYOUT_FEE";

        /** 换汇卖出 */
        public static final String FX_SELL = "FX_SELL";

        /** 平台服务费 */
        public static final String PLATFORM_FEE = "PLATFORM_FEE";

        private BillingFactType() {
        }
    }
}
