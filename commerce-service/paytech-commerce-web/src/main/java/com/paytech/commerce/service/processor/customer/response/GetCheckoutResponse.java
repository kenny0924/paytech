package com.paytech.commerce.service.processor.customer.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.paytech.commerce.domain.customer.CustomerCheckoutModels.ConsumerDetails;
import com.paytech.commerce.domain.customer.CustomerCheckoutModels.FileLink;
import com.paytech.commerce.domain.customer.CustomerCheckoutModels.PaymentStatus;
import com.paytech.commerce.domain.customer.CustomerCheckoutModels.Seller;
import com.paytech.commerce.domain.enums.CheckoutSessionStatus;
import com.paytech.commerce.domain.enums.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Checkout 的有限查询结果，不向 Controller 传递数据库对象或受保护引用。 */
@Getter
@AllArgsConstructor
public class GetCheckoutResponse {
    /** 公开订单号。 */
    private final String orderNo;

    /** 本库订单状态。 */
    private final OrderStatus orderStatus;

    /** 实际 Checkout 会话状态。 */
    private final CheckoutSessionStatus checkoutStatus;

    /** 最终合同与金额是否齐备，不依赖 Intent。 */
    private final boolean ready;

    /** 订单币种。 */
    private final String currency;

    /** 原计税国家。 */
    private final String country;

    /** 原合同语言，历史缺失时使用默认语言。 */
    private final String locale;

    /** 原 Checkout 到期时刻。 */
    private final Instant expiresAt;

    /** 本次读取的订单金额。 */
    private final Amounts amounts;

    /** 不可变商品展示行。 */
    private final List<Item> items;

    /** 是否已保存消费者资料。 */
    private final boolean consumerPresent;

    /** 显式回填时取得的消费者原值。 */
    private final ConsumerDetails consumerDetails;

    /** 原冻结法律卖方。 */
    private final Seller seller;

    /** 原冻结合同身份，未冻结时为空。 */
    private final Long consumerContractId;

    /** 原合同的三份受控文件链接。 */
    private final List<FileLink> links;

    /** 是否已绑定 Payment Intent。 */
    private final boolean paymentBound;

    /** 只读支付摘要，不可用时为空。 */
    private final PaymentStatus payment;

    /** 当前可提示的操作。 */
    private final List<String> allowedActions;

    /** 建议轮询毫秒数，不需轮询时为空。 */
    private final Integer pollAfterMs;

    /** 订单币种主单位金额，未确定的税额与总额保持为空。 */
    @Getter
    @AllArgsConstructor
    public static class Amounts {
        /** 商品总额。 */
        private final BigDecimal merchandise;

        /** 优惠总额。 */
        private final BigDecimal discount;

        /** 折后净额。 */
        private final BigDecimal net;

        /** 创建时确定的税额。 */
        private final BigDecimal tax;

        /** 创建时确定的舍入差额。 */
        private final BigDecimal rounding;

        /** 最终应付金额。 */
        private final BigDecimal total;
    }

    /** 商品展示结果，数量精度与金额币种精度独立。 */
    @Getter
    @AllArgsConstructor
    public static class Item {
        /** 商品名称。 */
        private final String name;

        /** 可选商品描述。 */
        private final String description;

        /** 冻结计量数量，可含小数。 */
        private final BigDecimal quantity;

        /** 订单币种主单位单价。 */
        private final BigDecimal unitAmount;

        /** 整行优惠金额，不再乘数量。 */
        private final BigDecimal discountAmount;

        /** 折后行净额。 */
        private final BigDecimal netAmount;
    }
}
