package com.paytech.billing.api.response;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/** 计费事实明细行及其计算证据；ID 与金额为十进制字符串，金额按币种精度补齐。 */
@Getter
@Setter
public class BillingFactLineResponseBody {
    /** 行号，从 1 起 */
    private String lineNo;

    /** 金额组成类型代码（FactComponentType） */
    private String componentType;

    /** 付方角色（FactPartyRole） */
    private String payerRole;

    /** 付方标识 */
    private String payerId;

    /** 收方角色（FactPartyRole） */
    private String payeeRole;

    /** 收方标识 */
    private String payeeId;

    /** 影响方向：CHARGE、REVERSAL、ADJUSTMENT */
    private String direction;

    /** 按币种精度归整后的正金额 */
    private String postedAmount;

    /**
     * 收方 Owner 账户的目标余额桶（PENDING、AVAILABLE）
     * <li>仅 MANUAL_ADJUSTMENT 可非空，为空按 AVAILABLE。</li>
     */
    private String targetBucket;

    /**
     * 本行费率或规则实际使用的非负基数
     * <li>资金类与人工行等于金额本身；八位小数以内的普通十进制字符串。</li>
     */
    private String basisAmount;

    /**
     * 实际使用的十进制费率
     * <li>固定金额或非规则行为空。</li>
     */
    private String rateValue;

    /** 尚未按币种精度舍入的金额（八位小数以内） */
    private String rawAmount;

    /**
     * 提供计算参数的规则族（SourceRuleType）
     * <li>资金类与人工行为空。</li>
     */
    private String sourceRuleType;

    /**
     * 规则族中实际使用的不可变版本 ID
     * <li>规则行必填；资金类与人工行为空。</li>
     */
    private String sourceRuleVersionId;

    /**
     * COMMERCIAL_PRICE 族命中的 Price ID
     * <li>其他族为空。</li>
     */
    private String sourcePriceId;

    /**
     * 规则中命中的组件 / 阶梯 / 范围的稳定 ID
     * <li>无则为空。</li>
     */
    private String sourceRuleComponentId;

    /**
     * 计算证据 JSON 对象
     * <li>结构见 t_billing_fact_line.source_evidence 列注释；规则行必填，资金类与人工行为空。</li>
     */
    private Map<String, Object> sourceEvidence;
}
