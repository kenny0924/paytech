package com.paytech.acquiring.service.processor.checkout.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import com.paytech.acquiring.domain.order.CommerceDecimalRules;
import com.paytech.foundation.money.Money;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 创建 Checkout 的内部请求；封装无外部访问的商品标识判断和金额计算。 */
@Getter
@AllArgsConstructor
public class CreateCheckoutRequest {
    /** 可信 Client 主键。 */
    private final long clientId;

    /** Client 内永久唯一的订单号。 */
    private final String clientOrderId;

    /** 订单币种；当前创建入口仅支持 USD。 */
    @NotBlank
    @Pattern(regexp = "USD", message = "当前创建 Checkout 仅支持 USD")
    private final String currency;

    /** 消费者国家。 */
    private final String consumerCountryCode;

    /** 请求到期时间；省略时按本次请求时间确定。 */
    private final Instant expiresAt;

    /** 已完成字段校验的原顺序商品。 */
    private final List<Item> items;

    /** 可选的 Client 命名空间内消费者引用；为空表示访客订单。 */
    private final String consumerReference;

    /** 商品行标识按大小写敏感的原值判断，不能在同一订单内重复。 */
    public boolean hasDuplicateClientItemIds() {
        return items.stream().map(Item::getClientItemId).distinct().count() != items.size();
    }

    /** 根据当前请求计算行金额与订单汇总，不推断税额或执行外部查询。 */
    public Amounts calculateAmounts() {
        var lines = items.stream().map(item -> item.calculateAmounts(currency)).toList();
        return new Amounts(lines, sum(lines, LineAmounts::merchandise), sum(lines, LineAmounts::discount),
            sum(lines, LineAmounts::net));
    }

    /** 按金额维度精确汇总，币种和数据库范围由 Processor 校验。 */
    private BigDecimal sum(List<LineAmounts> amounts, Function<LineAmounts, BigDecimal> extractor) {
        return amounts.stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 本次计算的商品行及订单汇总；金额均为订单币种主单位，供校验和 Context 构建使用。 */
    public record Amounts(List<LineAmounts> lines, BigDecimal merchandise, BigDecimal discount, BigDecimal net) {
    }

    /** 原商品输入及其按整行优惠计算的主单位金额；净额为单价乘数量再减整行优惠。 */
    public record LineAmounts(Item item, BigDecimal merchandise, BigDecimal discount, BigDecimal net) {
    }

    /** 创建商品行输入。 */
    @Getter
    @AllArgsConstructor
    public static class Item {
        /** Client 商品行标识。 */
        private final String clientItemId;

        /** 商品名称。 */
        private final String name;

        /** 商品描述，可为空。 */
        private final String description;

        /** 商品分类代码。 */
        private final String productTypeCode;

        /** 税务分类代码。 */
        private final String taxCategoryCode;

        /**
         * 商品数量。
         * <li>正数字符串，可含小数，例如 "2" 或 "0.5"；不受币种小数位限制。</li>
         */
        private final String quantity;

        /**
         * 商品单价。
         * <li>订单币种主单位的非负金额字符串，例如 USD "10.00" 表示 10 美元。</li>
         * <li>允许省略无意义的尾随零，按币种精度校验，不隐式舍入。</li>
         */
        private final String unitAmount;

        /**
         * 整行优惠金额。
         * <li>订单币种主单位的非负金额字符串，例如 USD "1.00" 表示该行共优惠 1 美元。</li>
         * <li>未提供时为 null，计算按零处理；优惠不再乘数量，不能超过单价乘数量。</li>
         */
        private final String discountAmount;

        /** 校验数量、币种精度和整行优惠，按公共 Money 计算净额。 */
        private LineAmounts calculateAmounts(String currency) {
            BigDecimal count = CommerceDecimalRules.parsePositive(quantity);
            Money unit = Money.of(currency, CommerceDecimalRules.parseNonNegative(unitAmount));
            Money discount = Money.of(currency,
                CommerceDecimalRules.parseNonNegative(discountAmount == null ? "0" : discountAmount));
            Money merchandise = Money.of(currency, count.multiply(unit.getAmount()));
            CommerceDecimalRules.requireRange(merchandise.getAmount());
            if (discount.compareTo(merchandise) > 0) {
                throw new IllegalArgumentException("整行优惠不能大于行毛额");
            }
            Money net = merchandise.subtract(discount);
            return new LineAmounts(this, merchandise.getAmount(), discount.getAmount(), net.getAmount());
        }
    }
}
