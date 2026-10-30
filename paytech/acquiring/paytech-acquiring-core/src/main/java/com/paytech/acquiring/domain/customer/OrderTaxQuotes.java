package com.paytech.acquiring.domain.customer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.paytech.acquiring.core.dal.dataobject.order.OrderItem;
import com.paytech.acquiring.core.dal.dataobject.order.OrderTaxCalculation;
import com.paytech.acquiring.domain.checkout.CheckoutUpstreamProtocolException;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.TaxCalculation;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.TaxComponent;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.TaxLine;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.money.Money;
import com.paytech.tax.api.enums.TaxCalculationStatus;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 订单税务报价的纯规则：核对 tax 结果与订单商品是否对齐，并形成本库追加写入的计算记录
 * <li>不访问数据库或外部服务；建单与消费者资料提交共用，失败一律按上游协议错误处理，不以零税兜底。</li>
 */
public final class OrderTaxQuotes {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 税组件元素的固定字段顺序（OrderPaymentSucceeded taxComponents[]）。 */
    private static final List<String> COMPONENT_FIELDS = List.of("jurisdictionCode", "taxType", "registrationId",
        "taxSubjectPartyId", "taxAmount");

    /** 纯静态规则。 */
    private OrderTaxQuotes() {
    }

    /**
     * 核对计算归属的商品行与金额恒等式
     * <li>逐行按 Client Item ID 对齐且净额一致；非 BLOCKED 时头部 净额 + 税额 + 舍入 = 总额、逐行 净额 + 税额 = 含税额、逐行税额合计等于
     * 头税额，READY 的组件合计等于头税额且只有一个纳税主体，NOT_MANAGED 没有组件；金额符合订单币种精度。</li>
     * <li>组件约束与 billing 消费 OrderPaymentSucceeded 的死信规则一致，在报价落库前拦截，避免支付成功后的事件进死信。</li>
     *
     * @param tax tax 计算结果
     * @param currency 订单币种
     * @param netAmount 订单净额
     * @param items 订单商品行
     */
    public static void validate(TaxCalculation tax, String currency, BigDecimal netAmount, List<OrderItem> items) {
        if (tax == null || tax.calculationId() <= 0 || tax.lines() == null || tax.lines().size() != items.size()
            || tax.components() == null) {
            throw new CheckoutUpstreamProtocolException("tax 计算结果不完整");
        }
        Map<String, TaxLine> lines = linesByItem(tax.lines());
        for (OrderItem item : items) {
            TaxLine line = lines.get(item.clientItemId());
            if (line == null || line.netAmount() == null || item.lineNetAmount().compareTo(line.netAmount()) != 0) {
                throw new CheckoutUpstreamProtocolException("tax 计算行与订单商品行不一致");
            }
        }
        if (!tax.blocked()) {
            validateAmounts(tax, currency, netAmount);
        }
    }

    /**
     * 形成本库追加写入的计算记录
     *
     * @param id 本库主键
     * @param orderId 订单主键
     * @param revision 订单内计算序号
     * @param basisCountryCode 本次国家证据
     * @param tax 已核对的计算结果
     * @param currency 订单币种，用于金额字符串精度
     * @param now 创建时刻
     * @return 待插入的记录
     */
    public static OrderTaxCalculation record(long id, long orderId, int revision, String basisCountryCode,
        TaxCalculation tax, String currency, Instant now) {
        return OrderTaxCalculation.builder().orderTaxCalculationId(id).orderId(orderId).revision(revision)
            .taxCalculationId(tax.calculationId()).calculationStage(tax.stage()).taxStatus(tax.status())
            .basisCountryCode(basisCountryCode).taxAmount(tax.taxAmount()).roundingAmount(tax.roundingAmount())
            .totalAmount(tax.totalAmount()).blockReasonCode(tax.blockReasonCode()).expiresAt(tax.expiresAt())
            .linesJson(tax.blocked() ? null : linesJson(tax.lines(), currency))
            .componentsJson(tax.blocked() ? null : componentsJson(tax.components(), currency)).createTime(now).build();
    }

    /**
     * 读取计算记录的组件为事件载荷数组
     * <li>MySQL JSON 列会按自身规则重排对象键，这里按固定顺序重建每个元素：jurisdictionCode、taxType、registrationId、
     * taxSubjectPartyId、taxAmount。</li>
     *
     * @param componentsJson 记录中的组件 JSON；BLOCKED 或存量订单为空
     * @return 组件数组；输入为空时返回 null
     */
    public static ArrayNode components(String componentsJson) {
        if (componentsJson == null) {
            return null;
        }
        var node = JSON.readTree(componentsJson);
        if (!(node instanceof ArrayNode stored)) {
            throw new IllegalArgumentException("components_json 不是 JSON 数组");
        }
        ArrayNode ordered = JsonNodeFactory.instance.arrayNode();
        for (var component : stored) {
            ObjectNode element = ordered.addObject();
            for (String field : COMPONENT_FIELDS) {
                element.set(field, component.path(field).isMissingNode() ? JsonNodeFactory.instance.nullNode()
                    : component.get(field));
            }
        }
        return ordered;
    }

    /** 报价金额与订单是否不同：任一金额或阻断状态变化都需要重新渲染合同。 */
    public static boolean amountsChanged(OrderTaxCalculation current, OrderTaxCalculation next) {
        return current.getTaxStatus() != next.getTaxStatus() || differs(current.getTaxAmount(), next.getTaxAmount())
            || differs(current.getRoundingAmount(), next.getRoundingAmount())
            || differs(current.getTotalAmount(), next.getTotalAmount());
    }

    /** 非 BLOCKED 的金额恒等式与精度。 */
    private static void validateAmounts(TaxCalculation tax, String currency, BigDecimal netAmount) {
        if (tax.taxAmount().signum() < 0 || tax.totalAmount().signum() <= 0
            || netAmount.add(tax.taxAmount()).add(tax.roundingAmount()).compareTo(tax.totalAmount()) != 0
            || !money(currency, tax.taxAmount()) || !money(currency, tax.roundingAmount())
            || !money(currency, tax.totalAmount())) {
            throw new CheckoutUpstreamProtocolException("tax 计算金额不完整或不平衡");
        }
        BigDecimal lineTax = BigDecimal.ZERO;
        for (TaxLine line : tax.lines()) {
            if (line.taxAmount().signum() < 0 || line.netAmount().add(line.taxAmount()).compareTo(line.totalAmount()) != 0) {
                throw new CheckoutUpstreamProtocolException("tax 计算行金额不平衡");
            }
            lineTax = lineTax.add(line.taxAmount());
        }
        BigDecimal componentTax = tax.components().stream().map(TaxComponent::taxAmount).reduce(BigDecimal.ZERO,
            BigDecimal::add);
        if (lineTax.compareTo(tax.taxAmount()) != 0
            || tax.status() == TaxCalculationStatus.READY && componentTax.compareTo(tax.taxAmount()) != 0) {
            throw new CheckoutUpstreamProtocolException("tax 行税额或组件合计与头税额不一致");
        }
        long subjects = tax.components().stream().map(TaxComponent::taxSubjectPartyId).distinct().count();
        if (tax.status() == TaxCalculationStatus.NOT_MANAGED ? !tax.components().isEmpty() : subjects > 1) {
            throw new CheckoutUpstreamProtocolException("tax 组件与计算状态不一致或纳税主体不唯一");
        }
    }

    /** 按 Client Item ID 建索引，重复行视为协议错误。 */
    private static Map<String, TaxLine> linesByItem(List<TaxLine> taxLines) {
        Map<String, TaxLine> lines = new HashMap<>();
        for (TaxLine line : taxLines) {
            if (line == null || lines.put(line.clientItemId(), line) != null) {
                throw new CheckoutUpstreamProtocolException("tax 计算行重复或为空");
            }
        }
        return lines;
    }

    /** 逐行税额 JSON：clientItemId、netAmount、taxAmount、totalAmount。 */
    private static String linesJson(List<TaxLine> lines, String currency) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        for (TaxLine line : lines) {
            ObjectNode node = array.addObject();
            node.put("clientItemId", line.clientItemId());
            node.put("netAmount", amount(currency, line.netAmount()));
            node.put("taxAmount", amount(currency, line.taxAmount()));
            node.put("totalAmount", amount(currency, line.totalAmount()));
        }
        return JSON.writeValueAsString(array);
    }

    /** 汇总组件 JSON：jurisdictionCode、taxType、registrationId、taxSubjectPartyId、taxAmount。 */
    private static String componentsJson(List<TaxComponent> components, String currency) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        for (TaxComponent component : components) {
            ObjectNode node = array.addObject();
            node.put("jurisdictionCode", component.jurisdictionCode());
            node.put("taxType", component.taxType());
            node.put("registrationId", component.registrationId());
            node.put("taxSubjectPartyId", component.taxSubjectPartyId());
            node.put("taxAmount", amount(currency, component.taxAmount()));
        }
        return JSON.writeValueAsString(array);
    }

    /** 按订单币种精度输出主单位金额字符串。 */
    private static String amount(String currency, BigDecimal value) {
        return Money.of(currency, value).toAmountString();
    }

    /** 金额符合订单币种精度与范围。 */
    private static boolean money(String currency, BigDecimal value) {
        try {
            Money.of(currency, value);
            return true;
        } catch (InvalidParameterException | IllegalArgumentException invalid) {
            return false;
        }
    }

    /** 可空金额按数值比较。 */
    private static boolean differs(BigDecimal left, BigDecimal right) {
        return left == null ? right != null : right == null || left.compareTo(right) != 0;
    }
}
