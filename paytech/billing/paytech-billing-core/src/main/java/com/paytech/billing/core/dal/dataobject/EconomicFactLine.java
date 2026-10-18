package com.paytech.billing.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;

import lombok.Getter;
import lombok.Setter;

/**
 * t_economic_fact_line（原 t_transaction_economic_component）：逐项冻结的经济事实明细行
 * <li>component_type 为开放目录，保持字符串；来源规则字段只对规则计算产生的交易事实行必填，人工 / 成本调整行为空。</li>
 */
@Getter
@Setter
public class EconomicFactLine {
    /**
     * 交易经济组件 Snowflake 主键
     */
    private Long id;

    /**
     * 所属交易经济结果主键
     */
    private Long transactionEconomicsId;

    /**
     * 同一结果内稳定且唯一的组件序号
     */
    private Integer componentNo;

    /**
     * 组件类型
     * <li>取值来源：Finance 经济组件类型目录；非固定枚举。</li>
     */
    private String componentType;

    /**
     * 归属业务角色
     * <li>可为空；人工 / 成本调整行等于收方角色。</li>
     */
    private FactPartyRole partyType;

    /**
     * 按 partyType 解释的稳定字符串标识
     * <li>可为空。</li>
     */
    private String partyId;

    /**
     * 付方角色
     * <li>人工 / 成本调整行必填；规则计算产生的交易事实行可为空。</li>
     */
    private FactPartyRole payerRole;

    /**
     * 按 payerRole 解释的付方稳定字符串标识
     * <li>与 payerRole 同时为空或非空。</li>
     */
    private String payerId;

    /**
     * 收方角色
     * <li>可空条件同 payerRole。</li>
     */
    private FactPartyRole payeeRole;

    /**
     * 按 payeeRole 解释的收方稳定字符串标识
     * <li>与 payeeRole 同时为空或非空。</li>
     */
    private String payeeId;

    /**
     * 本库税务计算行ID
     * <li>可为空。</li>
     */
    private Long taxLineId;

    /**
     * 非负金额基数
     */
    private BigDecimal basisAmount;

    /**
     * 实际费率
     * <li>可为空。</li>
     */
    private BigDecimal rateValue;

    /**
     * 未舍入原始金额
     */
    private BigDecimal rawAmount;

    /**
     * 按币种精度归整后的非负金额
     */
    private BigDecimal postedAmount;

    /**
     * 影响方向
     * <li>允许值：CHARGE、REVERSAL、ADJUSTMENT。</li>
     */
    private FactLineDirection effectDirection;

    /**
     * 来源规则族
     * <li>规则计算产生的组件必填；人工 / 成本调整行为空。</li>
     */
    private String sourceRuleType;

    /**
     * 来源规则不可变版本ID
     * <li>规则计算产生的组件必填；人工 / 成本调整行为空。</li>
     */
    private Long sourceRuleVersionId;

    /**
     * 准确 CCT Price ID
     * <li>可为空。</li>
     */
    private Long sourcePriceId;

    /**
     * Payment Method Profile Code
     * <li>可为空。</li>
     */
    private String sourceProfileCode;

    /**
     * 阶梯档位证据 JSON
     * <li>可为空。</li>
     */
    private String sourceBandEvidence;

    /**
     * 来源规则命中的 Scope ID
     * <li>可为空。</li>
     */
    private Long sourceRuleScopeId;

    /**
     * 来源规则命中的组件ID
     * <li>可为空。</li>
     */
    private Long sourceRuleComponentId;

    /**
     * 来源规则组件代码
     * <li>规则计算产生的组件必填；人工 / 成本调整行为空。</li>
     */
    private String sourceComponentCode;

    /**
     * 来源规则触发代码
     * <li>规则计算产生的组件必填；人工 / 成本调整行为空。</li>
     */
    private String sourceTriggerCode;

    /**
     * 来源规则计算基础
     * <li>规则计算产生的组件必填；人工 / 成本调整行为空。</li>
     */
    private String sourceCalculationBase;

    /**
     * 来源费率
     * <li>可为空。</li>
     */
    private BigDecimal sourceRate;

    /**
     * 来源固定金额
     * <li>可为空。</li>
     */
    private BigDecimal sourceFixedAmount;

    /**
     * 固定金额币种
     * <li>可为空。</li>
     */
    private String sourceCurrency;

    /**
     * 计算下限
     * <li>可为空。</li>
     */
    private BigDecimal sourceMinAmount;

    /**
     * 计算上限
     * <li>可为空。</li>
     */
    private BigDecimal sourceMaxAmount;

    /**
     * 来源规则内容摘要
     * <li>可为空。</li>
     */
    private String sourceRuleContentHash;

    /**
     * 计算方法
     * <li>规则计算产生的组件必填；人工 / 成本调整行为空。</li>
     */
    private String calculationMethod;

    /**
     * 舍入方式
     * <li>允许值：HALF_UP。</li>
     */
    private String roundingMode;

    /**
     * 该记录在 Billing 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;
}
