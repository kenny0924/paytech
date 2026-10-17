package com.paytech.commerce.customer.api.v1.checkout.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 加载与轮询 Checkout 的公开视图。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GetCheckoutResponseBody {
    /** 平台订单号。 */
    private String orderNo;

    /** 订单状态 Code；支付摘要不会直接改写订单状态。 */
    private String orderStatus;

    /** Checkout 会话状态 Code。 */
    private String checkoutStatus;

    /**
     * 初始化完整性。
     * <li>READY：创建时最终金额与合同已齐备；PENDING 仅表示未初始化的历史数据，不提供准备操作。Intent 在首次提交支付时创建。</li>
     */
    private String preparationStatus;

    /**
     * 金额状态。
     * <li>ESTIMATED：尚未冻结；FINAL：已冻结最终金额。</li>
     */
    private String amountStatus;

    /** 订单币种 Code。 */
    private String currency;

    /** 创建时确认的消费者计税国家 Code。 */
    private String consumerCountryCode;

    /** Checkout 展示语言。 */
    private String locale;

    /**
     * 原 Checkout 到期时间。
     * <li>UTC 时间；查询不延长期限。</li>
     */
    private Instant expiresAt;

    /** 订单金额。 */
    private CheckoutAmountsInfo amounts;

    /** 创建时冻结的商品展示行。 */
    private List<CheckoutItemInfo> items;

    /** 消费者资料保存状态及可选回填。 */
    private ConsumerInfo consumer;

    /**
     * 冻结的法律卖方。
     * <li>尚未冻结时为空。</li>
     */
    private SellerInfo seller;

    /**
     * 原冻结合同和文件链接。
     * <li>尚未冻结时为空。</li>
     */
    private LegalInfo legal;

    /**
     * 当前支付只读摘要。
     * <li>尚无 Intent 时为空。</li>
     */
    private PaymentInfo payment;

    /**
     * 当前可提示操作。
     * <li>命令入口仍须重新验证资格。</li>
     */
    private List<String> allowedActions;

    /**
     * 建议轮询间隔。
     * <li>单位毫秒；无需轮询时为空。</li>
     */
    private Integer pollAfterMs;

    /**
     * 存卡选项。
     * <li>available 为 true 时可在卡信息下方显示“保存卡以便下次使用”，默认不勾选。</li>
     */
    private CardSavingInfo cardSaving;

    /**
     * 当前消费者的已存卡。
     * <li>只含 Token、后四位与有效期；不可存卡时为空列表。使用时须重新输入 CVV。</li>
     */
    private List<StoredCardInfo> storedCards;

    /** 存卡选项。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardSavingInfo {
        /** 是否可以保存卡。 */
        private boolean available;
    }

    /** 可选的已存卡。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StoredCardInfo {
        /** 已存卡 Token。 */
        private String storedCardToken;

        /** 卡号后四位。 */
        private String last4;

        /** 有效期月份，两位。 */
        private String expiryMonth;

        /** 有效期年份，四位。 */
        private String expiryYear;
    }

    /** 订单六项金额。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckoutAmountsInfo {
        /**
         * 优惠前商品总额，等于各行单价乘数量之和。
         * <li>订单币种主单位金额字符串，按币种精度输出；当前 USD "10.00" 表示 10 美元。</li>
         */
        private String merchandiseAmount;

        /**
         * 整单优惠总额，等于各行优惠金额之和。
         * <li>订单币种主单位金额字符串，按币种精度输出；当前 USD "10.00" 表示 10 美元。</li>
         */
        private String discountAmount;

        /**
         * 优惠后税前净额。
         * <li>订单币种主单位金额字符串，按币种精度输出；当前 USD "10.00" 表示 10 美元。</li>
         */
        private String netAmount;

        /**
         * 税额。
         * <li>未初始化的历史数据为空，新订单在创建时确定。</li>
         * <li>订单币种主单位金额字符串，按币种精度输出；当前 USD "10.00" 表示 10 美元。</li>
         */
        private String taxAmount;

        /**
         * 舍入调整金额。
         * <li>未初始化的历史数据为空，新订单在创建时确定。</li>
         * <li>订单币种主单位金额字符串，按币种精度输出；当前 USD "10.00" 表示 10 美元。</li>
         */
        private String roundingAmount;

        /**
         * 最终应付总额。
         * <li>未初始化的历史数据为空，新订单在创建时确定。</li>
         * <li>订单币种主单位金额字符串，按币种精度输出；当前 USD "10.00" 表示 10 美元。</li>
         */
        private String totalAmount;
    }

    /** 冻结的商品展示行。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckoutItemInfo {
        /** 商品名称。 */
        private String name;

        /**
         * 商品描述。
         * <li>未提供时为空。</li>
         */
        private String description;

        /**
         * 购买数量。
         * <li>正数字符串，可含小数，例如 "2" 或 "0.5"；不受币种小数位限制。</li>
         */
        private String quantity;

        /**
         * 单价。
         * <li>订单币种主单位金额字符串，按币种精度输出；当前 USD "10.00" 表示 10 美元。</li>
         */
        private String unitAmount;

        /**
         * 整行优惠金额，不再乘数量。
         * <li>订单币种主单位金额字符串，按币种精度输出；当前 USD "10.00" 表示 10 美元。</li>
         */
        private String discountAmount;

        /**
         * 行折后净额。
         * <li>订单币种主单位金额字符串，按币种精度输出；当前 USD "10.00" 表示 10 美元。</li>
         */
        private String netAmount;
    }

    /** 消费者资料状态。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsumerInfo {
        /**
         * 资料状态。
         * <li>MISSING 或 COMPLETE。</li>
         */
        private String status;

        /**
         * 消费者原值。
         * <li>仅 includeConsumer=true 且已有资料时返回。</li>
         */
        private ConsumerDetailsInfo details;
    }

    /** 显式授权回填的消费者资料。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsumerDetailsInfo {
        /** 消费者姓名。 */
        private String fullName;

        /** 消费者邮箱。 */
        private String email;

        /** 完整账单地址。 */
        private BillingAddressInfo billingAddress;
    }

    /** 消费者账单地址，不覆盖订单计税国家。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillingAddressInfo {
        /** 国家 Code。 */
        private String countryCode;

        /** 地址第一行。 */
        private String addressLine1;

        /**
         * 地址第二行。
         * <li>可为空。</li>
         */
        private String addressLine2;

        /**
         * 州或地区。
         * <li>可为空。</li>
         */
        private String region;

        /** 城市。 */
        private String city;

        /**
         * 邮政编码。
         * <li>可为空。</li>
         */
        private String postalCode;
    }

    /** 原冻结法律卖方的公开披露。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SellerInfo {
        /** 法律名称。 */
        private String legalName;

        /**
         * 注册号。
         * <li>可为空。</li>
         */
        private String registrationNumber;

        /**
         * 税务登记号。
         * <li>可为空。</li>
         */
        private String taxRegistrationNumber;

        /** 公开地址。 */
        private String addressText;

        /**
         * 支持邮箱。
         * <li>可为空。</li>
         */
        private String supportEmail;

        /**
         * 支持电话。
         * <li>可为空。</li>
         */
        private String supportPhone;
    }

    /** 原冻结消费者合同。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LegalInfo {
        /**
         * 消费者合同 ID。
         * <li>十进制字符串。</li>
         */
        private String consumerContractId;

        /** CONSUMER_TERMS、REFUND_POLICY、PRIVACY_NOTICE 三份原文件。 */
        private List<DocumentInfo> documents;
    }

    /** 准确不可变文件的受控访问链接。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentInfo {
        /** 合同文件类型 Code。 */
        private String documentType;

        /** 文件展示标题。 */
        private String title;

        /** HTTPS 文件链接。 */
        private String url;

        /**
         * 链接到期时间。
         * <li>UTC 时间；永久链接为空，临时链接不超过 Checkout 期限。</li>
         */
        private Instant urlExpiresAt;
    }

    /** Payment 的只读查询摘要，不包含渠道执行指令。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentInfo {
        /**
         * 查询可用性。
         * <li>AVAILABLE 或 UNAVAILABLE；后者不推断可支付。</li>
         */
        private String availability;

        /**
         * 原支付提交 ID。
         * <li>尚无或不可用时为空。</li>
         */
        private String submissionId;

        /**
         * 原支付提交期限。
         * <li>UTC 时间；尚无或不可用时为空。</li>
         */
        private Instant submissionExpiresAt;

        /**
         * 支付尝试 ID。
         * <li>尚无或不可用时为空。</li>
         */
        private String attemptId;

        /**
         * 支付状态 Code。
         * <li>尚无或不可用时为空。</li>
         */
        private String status;

        /**
         * 消费者动作概要。
         * <li>无需动作或不可用时为空。</li>
         */
        private NextActionInfo nextAction;

        /**
         * 安全的支付失败原因。
         * <li>无失败原因或不可用时为空。</li>
         */
        private FailureInfo failure;

        /**
         * Payment 观察时间。
         * <li>UTC 时间；不可用时为空。</li>
         */
        private Instant observedAt;
    }

    /** 消费者动作概要。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NextActionInfo {
        /** 消费者动作 ID。 */
        private String actionId;

        /** 动作类型 Code。 */
        private String type;

        /**
         * 动作期限。
         * <li>UTC 时间；未指定时为空。</li>
         */
        private Instant expiresAt;
    }

    /** 可安全展示的支付失败原因。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailureInfo {
        /** 稳定的失败原因 Code。 */
        private String code;

        /** 安全提示；不透传渠道原文。 */
        private String message;
    }
}
