package com.paytech.billing.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.enums.SourceRuleType;
import com.paytech.billing.core.enums.FactTargetBucket;

import lombok.Getter;
import lombok.Setter;

/**
 * t_billing_fact_line：计费事实明细行，每行一个付方 → 收方的金额及其规则来源证据；不可变
 * <li>人工登记行：基数、原始金额与记账金额相同，费率与来源规则字段为空。</li>
 */
@Getter
@Setter
public class BillingFactLine {
    /** Snowflake 主键 */
    private Long id;

    /** 所属计费事实 */
    private Long factId;

    /** 事实内从 1 起连续的行号 */
    private Integer lineNo;

    /** 金额组成类型 */
    private FactComponentType componentType;

    /** 付方角色 */
    private FactPartyRole payerRole;

    /** 按 payerRole 解释的付方标识；OE 为 entityNo，其余为十进制字符串 */
    private String payerId;

    /** 收方角色，不得与付方相同 */
    private FactPartyRole payeeRole;

    /** 按 payeeRole 解释的收方标识 */
    private String payeeId;

    /** 本行费率或规则实际使用的非负基数；资金类与人工行等于金额本身 */
    private BigDecimal basisAmount;

    /**
     * 实际使用的十进制费率
     * <li>固定金额或非规则行为空。</li>
     */
    private BigDecimal rateValue;

    /** 按八位小数计算、尚未按币种精度舍入的金额 */
    private BigDecimal rawAmount;

    /** 按币种精度 HALF_UP 归整后的正金额 */
    private BigDecimal postedAmount;

    /** 本行相对原事实的作用 */
    private FactLineDirection effectDirection;

    /**
     * 收方 Owner 账户的目标余额桶
     * <li>仅 MANUAL_ADJUSTMENT 且收方为 CLIENT / LOCAL_PARTNER 时可非空，为空按 AVAILABLE。</li>
     */
    private FactTargetBucket targetBucket;

    /**
     * 提供计算参数的规则族
     * <li>资金类与人工行为空。</li>
     */
    private SourceRuleType sourceRuleType;

    /**
     * 规则族中实际使用的不可变版本 ID
     * <li>规则行必填；资金类与人工行为空。</li>
     */
    private Long sourceRuleVersionId;

    /**
     * COMMERCIAL_PRICE 族命中的 Price ID
     * <li>其他族为空。</li>
     */
    private Long sourcePriceId;

    /**
     * 规则中命中的组件 / 阶梯 / 范围的稳定 ID
     * <li>无则为空。</li>
     */
    private Long sourceRuleComponentId;

    /**
     * 计算证据 JSON 对象
     * <li>结构见列注释；规则行必填，资金类与人工行为空。经 SourceEvidenceTypeHandler 读写 JSON 列。</li>
     */
    private Map<String, Object> sourceEvidence;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;
}
